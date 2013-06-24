#Author::    Masashi Sakurai (mailto:m.sakurai@dream.com)
#Copyright:: Copyright (C) 2005-2006 Masashi Sakurai 
#License::   Distributes under LGPL

require "xmlrpc/client"
require "xmlrpc/server"
require "socket"
require "thread"
require "yajb/comm_abstract"

module JavaBridge 

  module TransferObjectFilter

	NULL_SYMBOL = "$$$NULL$$$"
	LONG_SYMBOL = "$$$LONG$$$"
	TYPED_ARRAY_SYMBOL = "$$$TYPED_A$$$"

	EXCEPTION_SYMBOL = "$$$EXCEPTION$$$"
	EXCEPTION_SEP = "__$$$__"

	def export_filter_array(args)
	  return args if (args.nil?)
	  args.size.times {|i|
		args[i] = export_filter(args[i])
	  }
	  args
	end

	def export_filter(a)
	  return NULL_SYMBOL if a.nil?
	  return LONG_SYMBOL+a.to_s if (a.class == Integer && ((a>>32) > 0))
	  return typed_array_filter(a) if a.class == Array
	  return a
	end

	# [type_symbol, values... ] -> [type string, values as string...]
	# [values... ] -> [values...]
	# type_symbol => :t_int1, :t_int2, :t_int4, :t_int8, :t_float, :t_double
	#                :t_decimal, :t_string, :t_boolean
	def typed_array_filter(arg)
	  return arg if arg.size == 0
	  return arg if arg[0].class != Symbol
	  type = arg.shift
	  ret = arg.map {|i| i.to_s }
	  return ret.unshift(TYPED_ARRAY_SYMBOL + type.to_s)
	end

	def import_filter(ret)
	  return ret[LONG_SYMBOL.size .. -1].to_i if (ret.class == String && ret[LONG_SYMBOL])
	  if (ret.class == String && ret[EXCEPTION_SYMBOL]) then
		ss = ret.split(EXCEPTION_SEP)
		raise JException.new(ss[1],ss[2],ss[3])
	  end
	  return (ret == NULL_SYMBOL) ? nil : ret
	end

  end

  # The driver implementation of XMLRPC
  class XMLRPCBridge < AbstractBridgeConnection
	include TransferObjectFilter

	def get_default_option(key)
	  print_debug("DefaultOption:#{key}")
	  case key
	  when :xmlrpc_bridge_port_r2j
		return 9010
	  when :xmlrpc_bridge_port_j2r
		return 9009
	  when :xmlrpc_bridge_opened
		return :kill
	  else
		return nil
	  end
	end

	def get_bridge_args(opt_proc)
	  portj2r = opt_proc.call(:xmlrpc_bridge_port_j2r)
	  portr2j = opt_proc.call(:xmlrpc_bridge_port_r2j)
	  if opt_proc.call(:bridge_server) == :self && 
		  check_previous_port(opt_proc,portr2j) then
		return "jbridge.BridgeBuilder jbridge.comm.xmlrpc.XMLRPC_JBServer -remoteport:#{portj2r} -javaport:#{portr2j}"
	  else
		return nil
	  end
	end

	def get_bridge_server_address(opt_proc)
	  if opt_proc.call(:bridge_server) == :self then
		"localhost"
	  else
		opt_proc.call(:bridge_server)
	  end
	end

	def check_previous_port(opt_proc,port)
	  way = opt_proc.call(:xmlrpc_bridge_opened)
	  begin
		s = TCPSocket.open(get_bridge_server_address(opt_proc), port)
		s.close
		#someone using the port
		case way
		when :reuse
		  puts "Port:#{port} has been opened. Trying to reuse the port..."
		  return nil
		when :kill
		  puts "Port:#{port} has been opened. Trying to kill the port..."
		  client = XMLRPC::Client.new(@bridge_server_address, "/RPC2", port)
		  client.call2("jb.exit")
		  return 0
		when :abort
		  puts "Port:#{port} has been opened. Abort this program."
		  abort
		end
	  rescue Errno::ECONNREFUSED => e
		#no connection
		return 0
	  rescue => e
		puts "Failed to deal with the opened port..."
		abort
	  end
	end

	def get_bridge_classpath(libpath)
	  "#{libpath}/yajb.jar"
	end
	
	def startup_server(opt_proc,_receiver_proc)
	  @bridge_server_address = get_bridge_server_address(opt_proc)
	  @client_port = opt_proc.call(:xmlrpc_bridge_port_r2j)
	  @client_pool = []
	  @pool_lock = Monitor.new
	  myserver = XMLRPC::Server.new(opt_proc.call(:xmlrpc_bridge_port_j2r),
									@bridge_server_address,10,$stdout,false,
									opt_proc.call(:debug_out))
	  myserver.add_handler("jb",XMLRPCJBReceiver.new(_receiver_proc))
	  myserver.add_handler("server.exit") do
		myserver.shutdown
	  end
	  @server = Thread.new {
		myserver.serve
	  }
	  @shutdown_proc = lambda {
		myserver.shutdown
	  }
	  print_debug("ClientPort:#{opt_proc.call(:xmlrpc_bridge_port_r2j)} ServerPort:#{opt_proc.call(:xmlrpc_bridge_port_j2r)}")
	  @connected = true
	end

	def client_call2(method,args)
	  client = nil
	  @pool_lock.synchronize {
		if @client_pool.empty? then
		  print_debug "  ##Create XMLRPC_Client"
		  client = XMLRPC::Client.new(@bridge_server_address, "/RPC2", @client_port)
		else
		  client = @client_pool.pop
		end
	  }
	  begin
		return client.call2("jb.#{method}",*args)
	  ensure
		@pool_lock.synchronize {
		  @client_pool.push(client)
		}
	  end
	end

	def shutdown_server
	  client_call2("exit",[])
	  @shutdown_proc.call
	  @connected = false
	end

	def send_message_to_java(method,*args)
	  print_debug "## >> #{method} | (#{args.join','})"
	  ok,ret = client_call2(method,export_filter_array(args))
	  print_debug "## >> #{ok}, #{ret}"
	  if ok
		return import_filter(ret)
	  else
		print_debug "Error Code: #{ret.faultCode}"
		print_debug ret.faultString
		raise ret.faultString
	  end
	end

	class XMLRPCJBReceiver
	  include TransferObjectFilter

	  def initialize(rproc)
		@receiver_proc = rproc
	  end

	  def call(sid,obj_id,method_name,*args)
		begin
		  return export_filter(@receiver_proc.call(sid,obj_id,method_name,args))
		rescue Exception => e
		  print_debug "== Exporting exception: #{e.to_s}"
		  return [EXCEPTION_SYMBOL, e.class.to_s, e.message, e.backtrace.join("\n")].join(EXCEPTION_SEP)
		end
	  end
	end

  end #class XMLRPC_JBServer

end #module JavaBridge
