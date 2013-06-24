#Author::    Masashi Sakurai (mailto:m.sakurai@dream.com)
#Copyright:: Copyright (C) 2005-2006 Masashi Sakurai 
#License::   Distributes under LGPL

require "singleton"

module JavaBridge 

  # abstract communication driver class
  # 
  class AbstractBridgeConnection
	include Singleton

	def initialize
	  @connected = false
	end

	# if the implementation driver has some default option parameters,
	# this method return the default values.
	def get_default_option(key)
	  nil
	end

	# java ${VM args} JavaBridge ${BridgeArgs} -logLevel:*** -logfile:***
	# This method should return "BridgeArgs".
	# "VM args" is made by options. 
	# An option is given by "opt_proc.call(:some_key)".
	def get_bridge_args(opt_proc)
	  raise "Not implemented error."
	end

	# If the communication bridge needs additional classpath for the java implementation,
	# the subclass return the classpath that will be used as a classpath argument of JVM.
	# "libpath" is the full path to the directory including jbridge.rb.
	def get_bridge_classpath(libpath)
	  nil
	end
	
	# Startup the communication server.
	# The JVM is started by the framework.
	def startup_server(opt_proc,receiver_proc)
	  raise "Not implemented error."
	end

	# Shutdown the communication server.
	# The subclass should kill the communication server running on ruby.
	# The JVM is killed by the framework.
	def shutdown_server
	  raise "Not implemented error."
	end

	# Send a message to JavaBridge.
	# Through this method, the jbridge module send some messages to Java,
	# such as new, call, ref and set method.
	def send_message_to_java(method,*args)
	  raise "Not implemented error."
	end

	def connected?
	  return @connected
	end

	def set_debugout(outProc)
	  @debugger_proc = outProc
	end

	def print_debug(line)
	  @debugger_proc.call(line)
	end
  end

end #module
