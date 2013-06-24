#Author::    Masashi Sakurai (mailto:m.sakurai@dream.com)
#Copyright:: Copyright (C) 2005-2006 Masashi Sakurai 
#License::   Distributes under LGPL

require "thread"
require "singleton"

#=JavaBridge
#
#This module provides a communication facility between Ruby and Java.
#The current implementation is written by pure Java and pure ruby.
#You can use ruby language and gain Java power easily.
#
#== Simple example and features
#
#=== Hello world
#
#  require 'yajb/jbridge'
#  include JavaBridge
#  jimport "javax.swing.*"
#  
#  :JOptionPane.jclass.showMessageDialog( nil, "Hello World!")
#  # this Java window sometimes shows behind the other window.
#
#=== GUI
#
#You can use Java GUI components as you use on Java.
#See sample scripts:
#
#* gui_simple.rb    : Simple GUI (Implementation of java.awt.event interfaces)
#* gui_dialog.rb    : JDialog Demo (Handling the Java-AWT thread)
#* gui_table.rb     : JTable Demo (Implementation of TableModel)
#* gui_table2.rb    : JTable Demo (Implementation of MVC)
#* gui_graphics.rb  : Drawing graphics (Implementing JComponent and drawing graphics)
#
#Writing scripts in UTF-8, you can use multibyte code correctly.
#
#=== Using java library
#
#* ext_poi.rb : POI Demo (Using external classpath and multibyte handling)
#
#POI sample demonstrates multibyte handling.
#
#=== Dynamic java code
#
#This package contains the javassist, the byte engineering tool library.
#You can make the POJO implementation from scratch.
# 
#* jlambda.rb : jlambda programming sample
# 
#See javassist website, http://www.csg.is.titech.ac.jp/~chiba/javassist/index.html
#
#== Configuration 
#
#When you call a communication method for the first time, 
#this module starts up JVM and constructs the bridge between Ruby and Java.
#You can configure JVM and Bridge parameters via hash object, as follows:
#
# JBRIDGE_OPTIONS = {
#   :classpath => [$CLASSPATH or %CLASSPATH%],
#                                 # classpath array. these entries will be evaluated 
#                                 # by the echo command in the system shell in order
#                                 # to use shell variables. The array is joined 
#                                 # by the appropriate path separator.
#   :jvm_path =>  "java",         # if you need, set fullpath to the JVM.
#                                 # if nil, the jbridge will not start the JVM by itself.
#   :jvm_vm_args => "",           # VM argument. ex: "-Xmx128m"
#   :jvm_stdout => :t,            # jbridge shows STDOUT from JVM.
#                                 # nil    : throw out stdout lines.
#                                 # :t     : transfer stdout lines to ruby's stdout.
#                                 # :never : never get java's stdout. 
#                                 # In the windows environment, nil or :t option may  
#                                 # cause serious slowing down.
#   :bridge_log => false,         # Ruby debug output. true or false
# 
# 
#   :bridge_driver => :bstream,    # communication driver (:xmlrpc or :bstream)
#   :bridge_server => :self,       # bridge server to connect
#                                  # :self   : use the self started up bridge server.
#                                  #           finishing the ruby program, yajb breaks
#                                  #           the bridge.
#                                  # "(address)"  : use other bridge server.
#                                  #           finishing the ruby program, yajb just
#                                  #           leaves the bridge.
#   
#   ## xmlrpc driver option 
#
#   :xmlrpc_bridge_port_r2j => 9010,  # communication port: ruby -> java
#   :xmlrpc_bridge_port_j2r => 9009,  # communication port: java -> ruby
#   :xmlrpc_bridge_opened => :kill,
#                    # If the port is using, how the program deals the port:
#                    # :kill  : try to kill the previous port and open new port
#                    # :abort : abort program
#                    # :reuse : use the previous port
#
#   ## bstream driver option
# 
#   :bstream_bridge_port => nil,  
#                    # communication port for bstream driver.
#                    # if nil, the available port will be searched automatically.
# }
#
#:xmlrpc driver is simple implementation to check the communication protocol.
#Although the XMLRPC communication library is easy to get and use the 
#implementation, the speed is slow. The slowness is due to the XML parsing and 
#writing.
#
#:bstream driver is second implementation improved the communication speed.
#This driver tranports binary data on the TCP socket. So the speed of the 
#:bstream communication is two times faster than that of :xmlrpc driver.
#
#== Basic API
#
#=== JavaBridge
#
#==== basic methods.
#* jnew
#* jstatic
#* jextend
#* jimport
#* junlink
#
#==== thread
#* stop_thread
#* wakeup_thread
#
#==== bridge methods
#* break_bridge
#* shutdown_bridge
#* startup_bridge
#
#=== Symbol
#
#* jnew( [<constructor arguments]... )
#* jclass
#* jext( [<constructor arguments]... )
#
#== Notes
#
#=== Transfer objects and primitive types
#
#The numerical values, String and Array objects are transfered to Java side 
#so as to maintain their precision. (the xmlrpc driver loses the precision of 
#the floating point values because of transfoming text encoding.)
#The transformation between the Ruby and Java is done as follows:
#
#    Java                Ruby
# ----------------------------------------
#   byte(Byte)          
#   short(Short)        
#   int(Integer)        Fixnum,Bignum,Float
#   long(Long)          (jbridge transforms dynamically)
#   float(Float)        
#   double(Double)      
#   BigDecimal          
#   String              String
#   Object[]            Array (content objects are applied transformation recursively)
#   primitive array     Array (typed array, see text)
#
#The instances of Fixnum, Bignum and Float are encoded by driver and 
#tranfored to Java side. Then, the variable type are detected by
#its own value size and arguments types of called methods dynamically.
#
#The other objects in Ruby can not be transfer into Java.
#On the other hand, if the other object in Java are transfered, the proxy
#objects are created to manipulate the Java objects from the Ruby side.
#Please see the next section for more details.
#
#The transfer mechanism of the array object is very complecated.
#Generally, jbridge can not exactly construct an array object in Java, because
#numerical values never tell its own Java type. (for example, the value of "1"
#can be hold byte, short, int, long and double in Java.)
#So, in many cases, the array objects are transformed into Object[] values. 
#Only if an array consists of String, an object of String[] is created in Java.
#
#If you want to transfer an array as a primitive array, you can use typed array
#notation. The first element in the array specifies the primitive type:
#
# [:t_int4, 1, 2, 3, 4] (Ruby) --> new int[]{ 1, 2, 3, 4 } (Java).
#
#You can use following type-symbols,
#
#* :t_int1    => byte[]
#* :t_int2    => short[]
#* :t_int4    => int[]
#* :t_int8    => long[]
#* :t_float   => float[]
#* :t_double  => double[]
#* :t_decimal => BigDecimal[]
#* :t_string  => String[]
#* :t_boolean => boolean[].
#
#=== Proxy objects and GC
#
#All created java objects, such as created by jnew, jextend, jstatic and return values,
#are holded by JavaBridge server. Those objects has proxy id to delegate the method 
#calling between the Java and ruby. If a proxy object is garbage collected in the ruby
#side, the JavaBridge sends an unlink message to the Java side to remove the 
#corresponding object from the repositry in Java.
#
#The overriding ruby objects created by jextend method are never removed automatically,
#because the JavaBridge can not decide when the object should be removed.
#You can remove the objects, calling the "junlink" method manually.
#
#=== Charactor encoding
#
#The string data is transported to the Java without any modification.
#The Java side interprets the string data as encoded in UTF-8.
#So, you need to adjust your string encoding to UTF-8, before you call the Java methods.
#
#=== Thread
#
#Any thread can call jbridge methods, except finalizer thread.
#If a jbridge method is called in the finalizer procedure, deadlock may 
#occurs.
#
#In the overriding method that is called by Java, all jbridge methods
#are executed by the Java thread that calls the overriding method by itself.
#For example, the ActionListener#actionPerformed method is called by 
#the event-dispatch thread. Then, all jbridge calling from the actionPerformed
#are executed by the event-dispatch thread. The thread mechanism is very important
#to writing GUI code. Please see the Java document for more detail of thread and 
#GUI.
#[Threads and Swing] http://java.sun.com/products/jfc/tsc/articles/threads/threads1.html
#
#When your Ruby program finishes (that is the termination of main thread),
#the communication bridge is also killed.
#If you want to wait for the message from the JVM, such as GUI events,
#you need to stop the main thread. 
#For convenience, you can call "stop_thread" method to hold the bridge 
#communication. Then, you can restart the main thread to call "wakeup_thread".
#
#* ref: sample/gui_xxx.rb
#
#=== Exceptions
#
#If an exception occurs in the Java side, jbridge throws a JException to Ruby side.
#The JException holds the exception class name, message and stacktrace.
#
#If an exception occurs in the overriding method, jbridge throws a 
#jbridge.RemoteRuntimeExcetion to Java side. The exception also holds 
#the exception class name, message and stacktrce.
#
#If an exception occurs in the jbridge communication, IOError or subclass of IOError
#is thrown.
#
#=== Speed
#
#In the present implementation, because the arguments and return values are
#transported by the serial stream, the calling Java method takes longer time
#than the calling pure Ruby methods.
#
#If you feel your code is too slow, you consider using jlambda or JClassBuilder.
#Because they are run on the Java, you can reduce the time of the trasportation 
#of values.
#
#In the future, replacing the communication driver by JNI may improve the speed.
#
#=== ClassLoader
#
#If a ClassLoader object is implemented in the ruby code, the class loader should be
#registered in order to use in yajb system. Calling "jadd_classloader" method with
#the ClassLoader object that you implemented in the ruby code, you can use the 
#ClassLoader to realize a class.
#
#* ref: sample/classfile.rb
#
#=== jlambda
#
#The utility of jlambda and JClassBuilder works through the javassist.
#So, your dynamic java code is restricted by the javassist compiler. For example,
#you can not write any comment, inner class and labeled jump in jlambda code.
#Please see the javassist documents for more details.
#
#The design of the jlambda utility is not good, because I have little idea.
#Please give me the advice or codes.
#
#* ref: sample/jlambda.rb
#
#== The lower protocol
#
#The YAJB communicates with the Java, using the following protocol that consists of
#basic remote procedure calls. 
#
#=== Type and Value
#
#* Z:boolean, B:byte, C:char, S:short, I:int, J:long, F:float, String
#* The other types are managed by the object manager with proxy ID.
#
#* Explicitly typed array : [(type symbol string), (values as string)...]
#
#=== Java
#
#* [proxy ID] = new([class name],[values])
#* [proxy ID] = static([class name])
#
#* [proxy ID] = extends([class names], [values])
#* impl([proxy ID],[method name], [override flag])
#
#* [fqcn] = classname([proxy ID])
#* [classinfo] = classinfo([fqcn])
#
#* [value] = ref([proxy ID],[field name])
#* set([proxy ID],[field name],[value])
#
#* [value] = call([proxy ID],[method name],[values])
#* [value] = callsuper([proxy ID],[method name],[values])
#
#* [value] = sessionCall([message],[arguments])
#
#* import([package names])
#
#* unlink([proxy ID])
#
#* addClassLoader([proxy ID])
#* removeClassLoader([proxy ID])
#
#* exit()
#* dump()
#
#=== Ruby
#
#* [value] = call([session ID],[proxy ID],[values])
#
module JavaBridge

  ##########################################################################
  # Bridge driver area
  ##########################################################################

  private

  ##########################################################################
  # Configuration area
  ##########################################################################

  @@default_options = {
	:jvm_path =>  "java",
	:jvm_vm_args => "",
	:bridge_log => false,

	:bridge_driver => :bstream,
	:bridge_server => :self,
  }

  @@opt_proc = lambda do |name|
	return @@options[name] if @@options.key?(name)
	if @@driver then
	  ret = @@driver.get_default_option(name)
	  return ret if ret
	end
	if name == :classpath then
	  if __not_cygwin? then
		return ["%CLASSPATH%"]
	  else
		return ["$CLASSPATH"]
	  end
	elsif name == :jvm_stdout then
	  if __not_cygwin? then
		return :never
	  else
		return :t
	  end
	end
	return @@default_options[name]
  end

  def __jbopt(name)
	@@opt_proc.call(name)
  end

  def __windows?
	RUBY_PLATFORM =~ /mingw|mswin32|cygwin/
  end

  def __not_cygwin?
	RUBY_PLATFORM =~ /mingw|mswin32/
  end

  def __cygwin?
	RUBY_PLATFORM =~ /cygwin/
  end

  def __cpseparator
	if __windows? then
	  csep = ";"
	else
	  csep = ":"
	end
  end

  def __search_libpath
	ret = nil
	catch (:found) {
	  $LOAD_PATH.each do |dir|
		Dir.glob("#{dir}/yajb/jbridge.rb") do |path|
		  ret = path.chomp.gsub(/\/jbridge\.rb$/,'')
		  throw :found
		end
	  end
	}
	ret = "." if ret.nil?
	if !RUBY_PLATFORM["cyg"].nil? then
	  ret = `cygpath -w #{ret}`.gsub(/\\/,"/").chomp
	end
	return ret
  end
  
  ##########################################################################
  # Low level API area
  ##########################################################################

  def __build_bridge
	return if defined?(@@object_table)

	@@driver = nil
	if defined?(JBRIDGE_OPTIONS) then
	  @@options = JBRIDGE_OPTIONS
	else
	  @@options = Hash.new
	end

	@@object_table = Hash.new          # proxy_id -> object
	@@gc_manager = JGCManager.instance

	@@debug_out = __jbopt(:bridge_log)

	print_debug("JavaBridge: Startup communication bridge...")

	case __jbopt(:bridge_driver)
	when :xmlrpc
	  @@driver = XMLRPCBridge.instance
	when :bstream
	  @@driver = BinStreamBridge.instance
	else
	  raise "Driver #{__jbopt(:bridge_driver).to_s} not found."
	end
	@@driver.set_debugout( lambda{|a| print_debug(a)} )
	
	__startup_JVM if __jbopt(:bridge_server) == :self
	@@driver.startup_server(@@opt_proc, lambda { |*args| __called_by_java(*args)})
	@@class_repository = JClassRepository.new

	print_debug("JavaBridge: Finished initialized.")
  end

  def __make_classpath_param
	cp_gen = __jbopt(:classpath).join(__cpseparator)
	if __not_cygwin? then
	  cp = cp_gen
	else
	  cp = `echo "#{cp_gen}"`.chomp
	end
	return "#{cp}#{__cpseparator}#{@@driver.get_bridge_classpath(__search_libpath)}"
  end

  def __startup_JVM
	#JVM CLASSPATH
	cp = __make_classpath_param
	print_debug("CLASSPATH: #{cp}")
	#JVM PATH
	path = __jbopt(:jvm_path)
	return unless path
	#JVM VM ARGS
	vmarg = __jbopt(:jvm_vm_args)
	
	#JVM LOG
	if __jbopt(:jvm_log_file).nil? then
	  logfile = ""
	else
	  logfile = " -logfile:#{__jbopt(:jvm_log_file)} "
	end
	loglevel = __jbopt(:jvm_log_level)

	#DRIVER ARGS
	driver_args = @@driver.get_bridge_args(@@opt_proc)
	return if driver_args.nil?

	cmd = "#{path} #{vmarg} -classpath \"#{cp}\" #{driver_args} -logLevel:#{loglevel} #{logfile}"
	print_debug(cmd)

	#EXEC COMMAND AND WAIT FOR INIT
	io = IO.popen(cmd,"r")
	while true
	  line = io.gets
	  puts "JVM: #{line}" if @@debug_out
	  abort "Can not start JVM: \n#{cmd}" if line.nil?
	  break if line =~ /^OK/
	end
	
	#START STDOUT PUTTER
	if __jbopt(:jvm_stdout) != :never then
	  Thread.start(io) {|java_io|
		loop {
		  if __jbopt(:jvm_stdout) then
			puts java_io.gets
		  else
			java_io.gets
		  end
		}
	  }
	end

	#REGISTER JVM KILLER
	at_exit {
	  print_debug "  EXIT JavaBridge..."
	  begin
		break_bridge
	  rescue => e
		puts e.message
		puts e.backtrace.join("\n")
	  ensure
		begin
		  Process.kill(:QUIT, io.pid)
		rescue => e
		  print_debug "Killing PID: #{io.pid}"
		  print_debug e.message
		  print_debug e.backtrace.join("\n")
		end
	  end
	  print_debug "  EXIT JavaBridge..."
	}
	print_debug("JVM: Initialized.")
  end

  def __register(obj)
	@@object_table[obj.__object_id] = obj
	obj
  end
  
  def print_debug(out)
	puts out if @@debug_out
  end

  # Called by java
  def __called_by_java(sid,obj_id,method_name,args)
	print_debug "%% Received: #{obj_id}.#{method_name} : #{__cjoin(args)}"
	obj = @@object_table[obj_id]
	if obj.nil? then
	  # not found extended object
	  raise "Object: #{obj_id} not found."
	else
	  # OK
	  Thread.current[:__jb_session] = sid
	  begin
		ret = obj.__jsend__(method_name,*args)
		return ret
	  rescue => ev
		print_debug "EXCEPTION (for debug): #{ev} -------------"
		print_debug ev.backtrace
		sa = __cjoin(args)
		print_debug "sid:#{sid}  object:#{obj.__classname}  method:#{method_name}  args:#{sa}:#{args.size}"
		print_debug "------------------------------------------"
		raise ev
	  ensure
		Thread.current[:__jb_session] = nil
	  end
	end
  end

  # calling the JavaBridge API
  def __send_message_to_java(method,*args)
	sid = Thread.current[:__jb_session]
	if sid then
	  # session thread logic
	  print_debug "## SESSION[#{sid}]  SendMessage: #{method}( #{__cjoin(args)} )"
	  args.insert(0,sid,method)
	  return @@driver.send_message_to_java("sessionCall",*args)
	else
	  # normal calling
	  print_debug "## SendMessage: #{method}( #{__cjoin(args)} )"
	  return @@driver.send_message_to_java(method,*args)
	end
  end

  ##########################################################################
  # Public API area
  ##########################################################################

  public

  # Breaking the bridge between ruby and JVM.
  # If :bridge_server option is set :self, shutdown message will be also sent.
  def break_bridge()
	return unless defined?(@@object_table)
	@@gc_manager.cancel_all_finalizer
	if @@driver.connected? && @@opt_proc.call(:bridge_server) == :self then
	  @@driver.shutdown_server
	end
	nil
  end

  # Sending a shutdown message to the JVM.
  def shutdown_bridge()
	if @@driver.connected? then
	  @@driver.shutdown_server
	end
	nil
  end

  # Building the bridge between ruby and JVM explicitly.
  def startup_bridge()
	__build_bridge
  end

  # Resuming the main thread.
  def wakeup_thread()
	@@main_thread.wakeup
	nil
  end

  #Stopping the main thread so as not to finish the Ruby program.
  #(The GUI programs can wait for messages from the JVM.)
  #
  #Calling "wakeup_thread", the Ruby program resumes the main thread.
  #(Before calling "exit" to terminate the Ruby program, the main thread 
  #should be on the running state.)
  def stop_thread()
	return unless defined?(@@object_table)
	@@main_thread = Thread.current
	Thread.stop
	nil
  end

  #Creating an instance of the concrete class.
  #The class name can be specified by String and Symbol.
  #Ex: "java.awt.Point" corresponds width :java_awt_Point.
  #
  #The return value is the proxy object for the instance of the Java.
  #
  #Corresponding notatin:  :SomeJavaClassName.jnew or :some_package_ClassName.jnew
  def jnew(classname,*args)
	__build_bridge
	obj = JCreatedObject.new(__to_s(classname),*args)
	@@gc_manager.register_finalizer(obj)
	return obj
  end

  #Creating an proxy instance that can override public and protected methods 
  #of the class and interfaces.
  #Adding the singleton method to the instance, you can override the Java method
  #in Ruby.
  #In the override method, the protected methods can be called.
  #You can use "super" method to invoke the method of super class in Java.
  #
  #The class name parameter can take zero or one class and arbitrary number of interfaces.
  #Those class names are specified by Symbol(only one) or String(separated by ",").
  #*Ex: "java.awt.event.ActionListener,java.awt.event.WindowAdaptor"
  #
  #If Ruby class overrides the Java method that has the same, the Java method delegates the operation to the Ruby method.
  #The abstract method delegates Ruby method to execute immediately.
  #
  #Corresponding notatin:  :SomeJavaClassName.jext or :some_package_ClassName.jext
  #
  #If the block is given, the block is used as a default method implementation.
  # set_default_dispatcher {|name,args|
  #    do somethind...
  # }
  #Where _name_ is the method name and _args_ is the array of argument objects.
  #This notation is useful for the implementation of few methods, such as ActionListener.
  def jextend(classnames,*args,&impl)
	__build_bridge
	ret = __register(JExtendedClass.new(__to_s(classnames),*args))
	ret.set_default_dispatcher(impl) if impl
	return ret
  end

  #Creating a static reference to the Java class.
  #Through the reference, the any static methods and fields can be called by Ruby.
  #
  #Corresponding notatin:  :SomeJavaClassName.jclass or :some_package_ClassName.jclass
  def jstatic(classname)
	__build_bridge
	obj = JClass.new(__to_s(classname))
	return obj
  end

  #jimport is almost similar to "import" statement of Java.
  #Ex:
  #
  #* import "java.awt.*"
  #* import "java.awt.*,java.awt.event.*"
  #* import "java.util.List"
  #
  #The later entry is given a priority.
  def jimport(lines)
	__build_bridge
	__send_message_to_java("import",lines)
	nil
  end

  #Remove the proxy object in the repositry of Java side.
  #After calling this method, the proxy object can not call
  #any method of the corresponding java object.
  #
  #This method remove the link between the proxy object and jbridge object repositry
  #so as for the proxy object to be garbage collected.
  #This method never remove the proxy object.
  def junlink(proxy)
	__build_bridge
	if proxy.kind_of?(JExtendedClass) then
	  key = proxy.__object_id
	  __send_message_to_java("unlink",key)
	else 
	  print_debug("The object #{proxy.__object_id} will be garbage_collected automatically.")
	end
  end

  #add a classloader object into the yajb system.
  def jadd_classloader(proxy_classloader)
	__build_bridge
	if proxy_classloader.kind_of?(JObject) then
	  key = proxy_classloader.__object_id
	  __send_message_to_java("addClassLoader",key)
	else 
	  print_debug("The object #{proxy_classloader.__object_id} can not be restered as a ClassLoder object.")
	end
  end

  #remove the classloader object from the yajb system.
  def jremove_classloader(proxy_classloader)
	__build_bridge
	if proxy_classloader.kind_of?(JObject) then
	  key = proxy_classloader.__object_id
	  __send_message_to_java("removeClassLoader",key)
	else 
	  print_debug("The object #{proxy_classloader.__object_id} can not be removed as a ClassLoder object.")
	end
  end

  #return all proxy objects in the object repositry.
  def jdump_object_list
	__build_bridge
	return __send_message_to_java("allObjects")
  end

  ##########################################################################
  # private API
  ##########################################################################

  private

  def __jclassname(key)
	__send_message_to_java("classname",key)
  end

  # creating a proxy object.
  def jproxy(classname,key)
	__build_bridge
	obj = JProxyObject.new(__to_s(classname),key)
	@@gc_manager.register_finalizer(obj)
	return obj
  end

  def __to_s(classname)
	return classname if classname.instance_of? String
	classname.to_s.gsub(/_/,".")
  end

  def __classinfo(classname)
	__send_message_to_java("classinfo",__to_s(classname)).split(",")
  end

  def __cjoin(obj)
    if obj.nil?
      return ""
    elsif obj.instance_of?(Array) then
      return "[]" if obj.size == 0
      return obj.inject([]) {|result,item| 
        result <<
          if item.instance_of?(Array) then
            if item.size == 0 then
              "[]"
            else
              "["+item.inject([]) {|r,i|
                if r.size == 3 then
                  r << "..."
                elsif r.size > 3 then
                  r
                else
                  r << i.to_s
                end
              }.join(",")+"]"
            end
          else
            item.to_s
          end
      }.join(",")
    else
      return obj.to_s
    end
  end

  ##########################################################################
  # JClass area
  ##########################################################################

  #
  # Abstract proxy class: a subclass should initialize @__classname, @__object_id
  #  and @__classinfo.
  #
  class JObject 

	def method_missing(name,*args)
	  print_debug "#### method_missing : #{name}(#{__cjoin(args)} )"
	  name = name.to_s
	  if args.size == 0 then
		#property get?
		return __ref__(name) if __define_jfield?(name)
	  else
		args = __obj2ids__(args)
		#property set?
		if !(name =~ /^.*=$/).nil? then
		  fname = name[0,name.length-1]
		  return __set__(fname,args[0]) if __define_jfield?(fname)
		end
	  end
	  #method call
	  return __id2obj__(__call__(name,*args)) if __define_jmethod?(name)
	  #not found
	  #super(name,*args)
	  as = __cjoin(args)
	  raise NoMethodError.new("Not found method: #{name}(#{as}) in #{__classname}",name, args)
	end

	def __define_jfield?(name)
	  return false if __define_jmethod?(name)
	  @__classinfo.define_jfield?(name)
	end

	def __define_jmethod?(name)
	  @__classinfo.define_jmethod?(name)
	end

	# called by java
	def __jsend__(method,*args)
	  args = __id2objs__(args)
	  return __send__(method,*args).to_trans_obj
	end

	def to_trans_obj
	  @__object_id
	end

	attr_reader :__object_id,:__classname,:__classinfo

	private

	def __obj2ids__(args)
	  args.map{|i| i.to_trans_obj }
	end

	def __id2obj__(arg)
	  return nil if (arg.nil?)
	  return __id2objs__(arg) if arg.instance_of?(Array)
	  return arg unless arg.instance_of?(String)
	  return arg if arg["\n"]
	  cn = __jclassname(arg)
	  if !cn.nil? then
		return @@object_table[arg] if @@object_table.key?(arg)
		jproxy(cn,arg)
	  else 
		arg
	  end
	end

	def __id2objs__(args)
	  args.map {|i| __id2obj__(i)}
	end

	def __call__(method,*args)
	  args = __obj2ids__(args)
	  __id2obj__(__send_message_to_java("call",@__object_id,method,*args))
	end

	def __ref__(fieldname)
	  __id2obj__(__send_message_to_java("ref",@__object_id,fieldname))
	end

	def __set__(fieldname,value)
	  __send_message_to_java("set",@__object_id,fieldname,value)
	  nil
	end

	public

	def to_s
	  "JB:ProxyObject  class:#{@__classname}, value:#{__call__('toString')}"
	end

	def inspect
	  to_s
	end

	def methods
	  return @accessible_methods if @accessible_methods
	  @accessible_methods = super
	  @accessible_methods |= @__classinfo.get_accessible_methods
	  return @accessible_methods
	end

  end # class JObject

  # the object created by ruby
  class JCreatedObject < JObject
	def initialize(classname,*args)
	  if args.size > 0 then
		args = __obj2ids__(args)
	  end
	  @__object_id = __send_message_to_java("new",classname,*args)
	  @__classname = __jclassname(@__object_id)
	  @__classinfo = @@class_repository.get_classinfo(@__classname)
	end
  end

  # the extended object created by ruby
  class JExtendedClass < JObject

	def initialize(_classnames,*args)
	  if args.size > 0 then
		args = __obj2ids__(args)
	  end
	  @__object_id = __send_message_to_java("extend",_classnames,*args)
	  @__classname = __jclassname(@__object_id)
	  @__classinfo = @@class_repository.get_classinfo(@__classname)
	  @table_impl_proc = Hash.new
	  @default_proc = nil
	end
	
	def __jsend__(method,*args)
	  if respond_to?(method) then
		return super(method,*args)
	  elsif @default_proc then 
		return @default_proc.call(method,args)
	  else
		raise "BUG: called not implemented method."
	  end
	end

	def __define_jmethod?(name)
	  @__classinfo.define_jmethod?(name) || @__classinfo.define_protected_jmethod?(name) 
	end

	def __super__(method,*args)
	  args = __obj2ids__(args)
	  __id2obj__(__send_message_to_java("superCall",@__object_id,method,*args))
	end

	def __call__(method,*args)
	  __super__(method,*args)
	end

	def singleton_method_added(name)
	  __send_message_to_java("impl",@__object_id,name.to_s,true)
	end

	def singleton_method_removed(name)
	  __send_message_to_java("impl",@__object_id,name.to_s,false)
	end

	def singleton_method_undefined(name)
	  __send_message_to_java("impl",@__object_id,name.to_s,false)
	end

	#the easy method implementation by the block notation.
	#users can write the event handler with the java-like variable scope.
	def jdef(name,&aproc)
	  @table_impl_proc[name] = aproc
	  instance_eval %Q{
		def #{name.to_s}(*args)
		  return @table_impl_proc[:#{name.to_s}].call(*args)
		  end
		}
	end

	#define default method implementation.
	#_name_ is the method name. _args_ is the array of argument objects.
	# set_default_dispatcher {|name,args|
	#    do somethind...
	# }
	def set_default_dispatcher(aproc)
	  @default_proc = aproc
	end

  end

  # the object created in the JVM
  class JProxyObject <  JObject
	def initialize(classname,key)
	  @__object_id = key
	  @__classname = classname
	  @__classinfo = @@class_repository.get_classinfo(@__classname)
	end
  end
  
  # the static class object
  class JClass < JObject

	attr_reader :__metainfo
	
	def initialize(classname)
	  @__object_id = __send_message_to_java("static",classname)
	  @__classname = __jclassname(@__object_id)
	  @__classinfo = @@class_repository.get_classinfo(@__classname)
	  @__metainfo = @@class_repository.get_classinfo("java.lang.Class")
	end

	def __define_jmethod?(name)
	  @__classinfo.define_jmethod?(name) || @__metainfo.define_jmethod?(name)
	end

	def methods
	  return @accessible_methods if @accessible_methods
	  @accessible_methods = super
	  @accessible_methods |= @__classinfo.get_accessible_methods
	  @accessible_methods |= @__metainfo.get_accessible_methods
	  return @accessible_methods
	end

  end

  ##########################################################################
  # JClassRepository and JClassInfo area
  ##########################################################################

  # 
  # JClassRepository manages the class information represented by JClassInfo.
  # 
  class JClassRepository
	def initialize
	  @classtable = Hash.new
	end

	def get_classinfo(classname)
	  return nil if classname.nil?
	  info = @classtable[classname]
	  return info if info
	  info = JClassInfo.new(classname)
	  @classtable[classname] = info
	  info
	end
  end

  # 
  # JClassInfo treats superclass, public fields, public and protected methods.
  # The instances of JClassInfo make a tree form in which the subclass 
  # makes a branche.
  #
  class JClassInfo 

	attr_reader :jsuperclass,:jclassname,:jfields,:jmethods,:protected_jmethods,:jinterfaces

	def initialize(_classname)
	  info = __classinfo(_classname)
	  @jclassname = _classname
	  @jinterfaces = []
	  @jfields = Hash.new
	  @jmethods = Hash.new
	  @protected_jmethods = Hash.new
	  _superclass = nil
	  mode = nil
	  info.each{|i|
		case i
		when "====Superclass"
		  mode = :superclass
		when "====Interfaces"
		  mode = :interfaces
		when "====Field"
		  mode = :fields
		when "====PublicMethod"
		  mode = :methods
		when "====ProtectedMethod"
		  mode = :protected_methods
		else
		  case mode
		  when :superclass
			_superclass = i
		  when :interfaces
			@jinterfaces << @@class_repository.get_classinfo(i)
		  when :fields
			@jfields[i] = :t
		  when :methods
			@jmethods[i] = :t
		  when :protected_methods
			@protected_jmethods[i] = :t
		  end
		end
	  }
	  if _superclass then
		@jsuperclass = @@class_repository.get_classinfo(_superclass) 
	  else
		@jsuperclass = nil
	  end

	  @public_jmethods = @jmethods.keys
	  @public_jmethods |= @jsuperclass.get_accessible_methods if @jsuperclass
	  @jinterfaces.each {|i| @public_jmethods |= i.get_accessible_methods }
	end

	def define_jfield?(name)
	  return true if @jfields.key?(name)
	  if (!@jsuperclass.nil?) then
		return true if @jsuperclass.define_jfield?(name)
	  end
	  @jinterfaces.each {|i|
		return true if i.define_jfield?(name)
	  }
	  false
	end

	def define_jmethod?(name)
	  return true if @jmethods.key?(name)
	  if @jsuperclass then
		return true if @jsuperclass.define_jmethod?(name)
	  end
	  @jinterfaces.each {|i|
		return true if i.define_jmethod?(name) 
	  }
	  false
	end

	def define_protected_jmethod?(name)
	  return true if @protected_jmethods.key?(name)
	  return @jsuperclass.define_protected_jmethod?(name) if @jsuperclass
	  @jinterfaces.each {|i|
		return true if i.define_jmethod?(name)
	  }
	  false
	end

	#return public java methods
	def get_accessible_methods
	  return @public_jmethods
	end

	def dump
	  puts "========: #{@jclassname}"
	  puts "  == Superclass: #{@jsuperclass.jclassname}" if @jsuperclass
	  putter = lambda {|i| puts "   #{i}" }
	  puts "  == Interface"
	  @jinterfaces.each {|i| puts "   #{i.jclassname}"}
	  puts "  == Field"
	  @jfields.each_key(putter)
	  puts "  == Public Method"
	  @jmethods.each_key(putter)
	  puts "  == ProtectedMethod Method"
	  @protected_jmethods.each_key(putter)
	end
  end

  ##########################################################################
  # Exception holder for the exceptions occurred in Java 
  ##########################################################################

  class JException < RuntimeError

	attr_reader :klass,:message,:detail

	def initialize(klass,message,detail)
	  @klass = klass
	  @message = message
	  @detail = detail
	end

	def message
	  ret = ""
	  ret += @klass if @klass
	  ret += "\n"+@message if @message
	  ret += "\n"+@detail if @detail
	  return ret
	end

	alias :to_s :message

  end

  private
  
  ##########################################################################
  # GC manager: send unlink message to Java for removing the object reference
  ##########################################################################

  class JGCManager 
	include Singleton

	def initialize
	  @@object_id_table = Hash.new    # __id__ -> proxy_id
	  @@object_ref_counter = Hash.new # proxy_id -> refcounter
	  @@object_lock = Monitor.new     # lock for finalizer registration and deregistration

	  # I'm not sure that using lists without locking...
	  @@using_list1 = false
	  @@finalizable_id_list1 = []
	  @@using_list2 = false
	  @@finalizable_id_list2 = []

	  @@later_registration_list = []
	end

	public
	def exec_finalizable_objects
	  @@object_lock.synchronize do 
		@@using_list1 = true
		@@finalizable_id_list1.each {|i| finalize_jobject(i)}
		@@finalizable_id_list1 = []
		@@using_list1 = false
		@@using_list2 = true
		@@finalizable_id_list2.each {|i| finalize_jobject(i)}
		@@finalizable_id_list2 = []
		@@using_list2 = false
	  end
	end

	private
	def finalize_jobject(proxy_id)
	  begin
		counter = @@object_ref_counter[proxy_id]
		if counter > 1 then
		  counter = counter - 1
		  @@object_ref_counter[proxy_id] = counter
		  print_debug "     ----GC: decrease ref count [#{counter}] : #{proxy_id}"
		  return nil
		end
		@@object_ref_counter.delete(proxy_id)
		print_debug "     ----GC: sending unlink... : #{proxy_id}"
		__send_message_to_java("unlink",proxy_id)
		print_debug "     ----GC: sent ok."
	  rescue Exception => e
		p e
		raise RuntimeError.new("Failed to finalize object: [#{proxy_id}] => #{e.class.to_s} : #{e.message}")
	  ensure
		print_debug "     ----GC: exiting finalizer."
	  end
	end

	def self.register_gc_object_id(pid)
	  if !@@using_list1 then
		@@finalizable_id_list1 << pid
		if @@later_registration_list.size > 0 then
		  @@finalizable_id_list1.concat @later_registration_list
		  @@later_registration_list = []
		end
	  elsif !@@using_list2 then
		@@finalizable_id_list2 << pid
		if @@later_registration_list.size > 0 then
		  @@finalizable_id_list2.concat @later_registration_list
		  @@later_registration_list = []
		end
	  else
		print_debug "     ----GC: finalize later: #{pid}"
		@@later_registration_list << pid
	  end
	end
	
	def self.get_finalizer_proc
	  return Proc.new {|id|
		proxy_id = @@object_id_table[id]
		begin
		  print_debug "     ----GC: register unlink: #{proxy_id}"
		  JGCManager.register_gc_object_id( proxy_id )
		rescue Exception => e
		  p e
		ensure
		  @@object_id_table.delete(id)
		end
	  }
	end

	public
	def register_finalizer(proxy)
	  @@object_lock.synchronize {
		@@object_id_table[proxy.__id__] = proxy.__object_id
		counter = @@object_ref_counter[proxy.__object_id]
		if counter then
		  counter += 1
		else 
		  counter = 1
		end
		print_debug "     ----GC: #{counter} : #{proxy.__object_id}"
		@@object_ref_counter[proxy.__object_id] = counter
		if (!proxy.kind_of?(JObject)) then
		  raise RuntimeError.new("GC: different object: #{proxy.to_s}")
		end
		ObjectSpace.define_finalizer(proxy, JGCManager.get_finalizer_proc)
	  }
	  exec_finalizable_objects
	end

	public
	def cancel_all_finalizer
	  @@object_lock.synchronize {
		print_debug "      ----GC: begin cancelling finalizer: #{@@object_id_table.size}"
		@@object_id_table.reject! {|key,value|
		  begin
			obj = ObjectSpace._id2ref(key)
			ObjectSpace.undefine_finalizer(obj)
			print_debug "      ----GC:   cancel: #{obj.__object_id}"
		  rescue RangeError => e
		  end
		  true
		}
	  }
	end
  end

end #module

# Ruby like new methods
class Symbol
  def jext(*args,&impl)
	JavaBridge::jextend(self, *args, &impl)
  end
  
  def jnew(*args)
	JavaBridge::jnew(self, *args)
  end
  
  def jclass
	JavaBridge::jstatic(self)
  end
end

# Extension for data transfer
class Object
  def to_trans_obj
	raise IOError.new("Can not export complex object: #{self.class}")
  end
end

class String
  def to_trans_obj
	self
  end
end

class Numeric
  def to_trans_obj
	self
  end
end

class FalseClass
  def to_trans_obj
	self
  end
end

class TrueClass
  def to_trans_obj
	self
  end
end

class NilClass
  def to_trans_obj
	self
  end
end

class Array
  def to_trans_obj
	self
  end

end

