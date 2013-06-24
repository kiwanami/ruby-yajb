#!/usr/bin/ruby

# 
# Custom ClassLoader script: 
# 

JBRIDGE_OPTIONS = {
#  :jvm_log_file => "jlog.txt",
#  :jvm_log_level => "debug",
  :bridge_log => true,
#  :bridge_driver => :xmlrpc,
#  :bridge_driver => :bstream,
  :jvm_stdout => :never
}

require 'yajb'
require 'fileutils'
include JavaBridge

open("TestClass.java","w") do |fio|
  fio.puts("
public class TestClass {
	private String id,name;
	public TestClass() {id = DEFAULT_ID;}
	public final String getId() {return id;}
	public final void setId(final String newId) {this.id = newId;}
	public final String getName() {return name;}
	public final void setName(final String newName) {this.name = newName;}
	public static final String DEFAULT_ID = \"000000\";
}")
end
begin
  `javac TestClass.java`
  return unless FileTest.exist?("TestClass.class")

  fileClassLoader = :ClassLoader.jext

  class << fileClassLoader
	def findClass(classname)
	  filename = classname+".class"
	  return unless File.exist?(filename)
	  ar = [:t_byte]
	  open(filename).each_byte{|ch|
		ar << ch
	  }
	  return defineClass(classname,ar,0,ar.size-1)
	end
  end

  jadd_classloader(fileClassLoader)

  klass = :TestClass.jclass

  puts klass.getName
  puts klass.__object_id

  puts "=====================(field)"
  klass.getDeclaredFields.each {|i|
	puts i.getName
  }

  puts "=====================(method)"
  klass.getDeclaredMethods.each {|i|
	puts i.getName
  }
 
  obj = :TestClass.jnew

  puts obj.getId
  obj.setId("123456")
  puts obj.getId

  ## Following code does not work, because of yajb's classloader.

  #eobj = :TestClass.jext
  #puts eobj.getId
  #def obj.getId
  #  "Override"
  #end
  #puts eobj.getId

ensure
  FileUtils.rm('TestClass.class',{:force => true})
  FileUtils.rm('TestClass.java',{:force => true})
end
