JBRIDGE_OPTIONS = {
  :jvm_log_file => "jlog.txt",
  :jvm_log_level => "verbose",
  :jvm_stdout => :t,
#  :bridge_log => true,
#  :bridge_driver => :xmlrpc,
#  :bstream_bridge_port => 9999,
#  :jvm_path =>  nil,
}

require 'yajb/jbridge'

include JavaBridge

a = :Integer.jnew(1)
b = :Integer.jnew(2)

jdump_object_list

a = nil
b = nil

ObjectSpace.garbage_collect

jdump_object_list
