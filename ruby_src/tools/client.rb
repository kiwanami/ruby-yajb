#!/usr/bin/ruby

# 
# XMLRPC test client: 
# 

if $*.size == 0 then
  print "> testclient (method) (args...)\n"
  exit
end

$portNumber = 9010

require "xmlrpc/client"

server = XMLRPC::Client.new("localhost", "/RPC2", $portNumber)
ok,ret = server.call2(*$*)
if ok
  print "STATUS: #{ok}\n"
  print "RETURN: #{ret}\n"
else
  puts "Error: "
  puts ret.faultCode
  puts ret.faultString
end

