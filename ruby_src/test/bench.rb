# 
# Benchmark program
# 

require 'yajb'

JBRIDGE_OPTIONS = {
#  :jvm_log_file => "jlog.txt",
#  :jvm_log_level => "verbose",
#  :bridge_log => true,
#  :bridge_driver => :xmlrpc,
#  :bridge_driver => :bstream,
}

include JavaBridge

jimport "java.util.*"

def get_something(ar=false)
  case rand(6)
  when 0
	return nil
  when 1
	return rand(512)
  when 2
	return rand(1<<64)
  when 3
	return rand(nil)*100
  when 4
	return "string!"
  when 5
	return "array!" if ar
	ret = []
	rand(5).times {
	  ret << get_something(true)
	}
	return ret
  end
end

begin 
  num = 10000

  start = Time.now

  d = num/20
  list = :ArrayList.jnew
  num.times {|i|
	list.add(get_something)
	if (i % d) == 0 then
	  $stdout.print "." 
	  $stdout.flush
	end
  }
  puts ""

  mid = Time.now

  ret = list.toArray

  puts("call:#{mid-start}  large data:#{Time.now - mid}")

rescue JException => e
  puts e.klass, e.message, e.detail
end

