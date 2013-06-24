#Author::    Masashi Sakurai (mailto:m.sakurai@dream.com)
#Copyright:: Copyright (C) 2005-2006 Masashi Sakurai 
#License::   Distributes under LGPL

require "socket"
require "thread"
require "yajb/comm_abstract"
require "yajb/inourpc"


module JavaBridge 

  #The driver implementation of binary stream protocol.
  class BinStreamBridge < AbstractBridgeConnection
	include INOURPC

	def get_default_option(key)
	  nil
	end

	def get_port_number(opt_proc)
	  if @port.nil? then
		ap = opt_proc.call(:bstream_bridge_port)
		if ap then
		  @port = ap
		else
		  s = TCPServer.open(0)
		  @port = s.addr[1]
		  s.close
		end
	  end
	  return @port
	end

	def get_bridge_args(opt_proc)
	  return "jbridge.BridgeBuilder jbridge.comm.binstream.BStream_JBServer -remoteport:#{get_port_number(opt_proc)}"
	end

	def get_bridge_classpath(libpath)
	  "#{libpath}/yajb.jar"
	end
	
	def get_bridge_server_address(opt_proc)
	  if opt_proc.call(:bridge_server) == :self then
		"localhost"
	  else
		opt_proc.call(:bridge_server)
	  end
	end

	def startup_server(opt_proc,receiver_proc)
	  print_debug "start up BinStream connection on #{get_port_number(opt_proc)}"
	  @client = RPCClient.new(get_bridge_server_address(opt_proc),
							  get_port_number(opt_proc))
	  @client.set_debug(opt_proc.call(:bridge_log))
	  @client.add_handler("call",receiver_proc)
	  @client.start
	  @connected = true
	end

	def shutdown_server
	  @client.send_message("exit",nil)
	  @client.shutdown
	  @connected = false
	end

	def send_message_to_java(method,*args)
	  return @client.send_message(method,*args)
	end

	class BinStream_JBReceiver
	  def initialize(rproc)
		@receiver_proc = rproc
	  end
	  def call(sid,obj_id,method_name,args)
		@receiver_proc.call(sid,obj_id,method_name,args)
	  end
	end

  end #class BinStreamBridge

end #module JavaBridge
