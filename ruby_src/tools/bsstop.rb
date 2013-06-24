#!/usr/bin/ruby

# 
# Java-XMLRPC stopper
#   Sometimes ruby fail to kill the jvm process.
#   Then, executing this program, you can stop the process.
# 

$portNumber = 9999

require "yajb/bstream"
include BinStream


client = BinClient.new("localhost",$portNumber)
client.start
client.send_message "exit"
