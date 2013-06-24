#!/usr/bin/ruby

# Stop the yajb server 
# 
#  $ ruby stop_yajb_server.rb (server address) (port)

SERVER_ADDRESS = $*[0]
SERVER_PORT = $*[1].to_i

JBRIDGE_OPTIONS = {
#  :jvm_stdout => :t,
#  :bridge_log => true,

#  :bridge_driver => :xmlrpc,

   :bridge_server => SERVER_ADDRESS,
   :xmlrpc_bridge_port_r2j => SERVER_PORT,
   :xmlrpc_bridge_port_j2r => (SERVER_PORT+1),
   :bstream_bridge_port => SERVER_PORT,
}

require 'yajb'
include JavaBridge

startup_bridge
shutdown_bridge
