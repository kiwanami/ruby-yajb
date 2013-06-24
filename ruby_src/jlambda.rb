require 'thread'
require 'monitor'

module JavaBridge

  @@pool = nil

  @@cuid = 1
  @@cuid_lock = Monitor.new

  private

  def get_pool
	@@pool = :javassist_ClassPool.jclass.getDefault unless @@pool
	return @@pool
  end

  def get_unique_classname
	@@cuid_lock.synchronize {
	  @@cuid += 1
	  return "JMetaUtilUID#{@@cuid.to_i}"
	}
  end

  def normalize_block_exp(src)
	src = src.split("\n").join
	src = src.gsub("\t","")
	src.gsub!(/(-?do|-?end)/) {|i|
	  case i
	  when "do"
		"{"
	  when "end"
		"}"
	  else
		i[1..-1]
	  end
	}
	return src
  end

  public

  #Make a proc object in Java.
  def jlambda(arg_src, body_src)
	arg_src = arg_src.split("\n").join
	body_src = normalize_block_exp body_src
	body_src += "return null;" unless body_src =~ /return/

	classname = get_unique_classname
	cls = get_pool.makeClass(classname, get_pool.get("java.lang.Object"))
	cls.setModifiers(cls.getModifiers() & ~(:javassist_Modifier.jclass.ABSTRACT))

	src = "public Object call(#{arg_src}) throws Exception {#{body_src}}"
	m = :javassist_CtNewMethod.jclass.make(src,cls)
	cls.addMethod(m)
	cls.toClass

	jobj = jnew(classname)
	return lambda {|*args| jobj.call(*args) }
  end

  class JClassBuilder
	
	def initialize(name, superclass=nil)
	  begin
		@frosted = false
		@classname = name
		@ctclass = get_pool.makeClass @classname
	  rescue => e
		raise RuntimeError.new("Can not make a class [#{name}] => #{e.message}")
	  end
		
	  begin
		if superclass then
		  @ctclass.setSuperclass( get_pool.get( superclass ) )
		end
	  rescue => e
		raise RuntimeError.new("Can not set the superclass [#{superclass}] => #{e.message}")
	  end
	end

	attr_reader :ctclass

	def add_interface(name)
	  begin
		@ctclass.addInterface( get_pool.get( name ) )
	  rescue => e
		raise RuntimeError.new("Can not add the interface [#{name}] => #{e.message}")
	  end
	end

	def add_constructor(src)
	  begin
		c = :javassist_CtNewConstructor.jclass.make(normalize_block_exp(src),@ctclass)
		@ctclass.addConstructor(c)
	  rescue => e
		raise RuntimeError.new("Can not make a constructor of #{@classname} => #{e.message}")
	  end
	end

	def add_field(src)
	  src += ";" unless src =~ /;/
	  begin
		f = :javassist_CtField.jclass.make(src.split("\n").join,@ctclass)
		@ctclass.addField(f)
	  rescue => e
		raise RuntimeError.new("Can not make a field of #{@classname} => #{e.message}")
	  end
	end

	def add_method(src)
	  begin
		m = :javassist_CtNewMethod.jclass.make(normalize_block_exp(src),@ctclass)
		@ctclass.addMethod(m)
	  rescue => e
		raise RuntimeError.new("Can not make a method of #{@classname} => #{e.message}")
	  end
	end

	def frost
	  if !@frosted then
		@ctclass.toClass 
		@frosted = true
	  end
	  nil
	end

	def new_instance(*args)
	  begin
		frost
		return jnew(@classname,*args)
	  rescue => e
		raise RuntimeError.new("Can not make an instance of #{@classname} => #{e.message}")
	  end
	end
  end
end
