#!/usr/bin/ruby

# 
# Start up the yajb server.
# The server can be terminated by stop_yajb_server.rb.
# 
#  $ ruby run_yajb_server (port)

PORT = $*[0].to_i

JBRIDGE_OPTIONS = {
#  :jvm_stdout => :t,
#  :bridge_log => true,

  :bridge_server => :self,

  :bridge_driver => :bstream,
  :bstream_bridge_port => PORT,

  :xmlrpc_bridge_port_r2j => PORT,
  :xmlrpc_bridge_port_j2r => PORT+1,
  :xmlrpc_bridge_opened => :kill,
}

require 'yajb'
include JavaBridge

startup_bridge
puts "YAJB Bridge server is started : port #{PORT}"

stop_thread
puts "YAJB Bridge server is stopped."
