#!/usr/bin/ruby

# 
# Java-XMLRPC stopper
#   Sometimes ruby fail to kill the jvm process.
#   Then, executing this program, you can stop the process.
# 

$portNumber = 9010
require "xmlrpc/client"
server = XMLRPC::Client.new("localhost", "/RPC2", $portNumber)

ok,ret = server.call2("jb.exit")
