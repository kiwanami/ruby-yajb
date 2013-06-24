# 
# RubyUnit test program
#   Ex: $ ant run-jb
# 

require 'test/unit'
require 'fileutils'
require 'yajb'

JBRIDGE_OPTIONS = {
#  :bridge_log => true,
#  :bridge_driver => :xmlrpc,
#  :bridge_driver => :bstream,
#  :jvm_stdout => :never
}

include JavaBridge


class BridgeTest < Test::Unit::TestCase
  
  def setup
	:System.jclass
  end

  def test_normal
	p = jnew("java.awt.Point",10,20)
	assert_equal("java.awt.Point",__jclassname(p.__object_id))
	assert_equal(10,p.x)
	p.x = 20
	assert_equal(20,p.x)
	p.translate(-10,-20)
	assert_equal(10,p.x)
	assert_equal(0,p.y)
  end

  def test_static
	m = jstatic("Math")
	assert_equal("java.lang.Math",__jclassname(m.__object_id))
	assert_equal(Math::PI, m.PI)
	assert_equal(0, m.sin(0))
  end

  def test_returnValue
	srt = jstatic("Runtime")
	assert_equal("java.lang.Runtime", __jclassname(srt.__object_id))
	rt = srt.getRuntime()
	assert_equal("java.lang.Runtime", __jclassname(rt.__object_id))
  end

  def test_argument
	str = jnew("String","10")
	assert_equal("java.lang.String",__jclassname(str.__object_id))
	it = jstatic("Integer")
	assert_equal("java.lang.Integer",__jclassname(it.__object_id))
	rt = it.parseInt(str,16)
	assert_equal(16,rt);
  end

  def test_override1
	id = jextend("java.awt.Point",10,10)
	id.translate(10,10)
	assert_equal(20,id.x)
	assert_equal(20,id.y)

	def id.translate(dx,dy)
	  self.x= self.x + dx*2
	  self.y= self.y + dy*2
	end

	id.move(0,0)
	id.translate(10,20)
	assert_equal(20,id.x)
	assert_equal(40,id.y)
	
	id.move(0,0)
	id.__super__("translate",10,20)
	assert_equal(10,id.x)
	assert_equal(20,id.y)
  end

  def test_override2
	runnable = jextend("Runnable")
	class << runnable
	  def init(t)
		@value = 0
		@thread = t
	  end
	  def result
		@value
	  end
	  def run
		sleep 0.5
		@value = 1
		@thread.wakeup
		nil
	  end
	end
	runnable.init(Thread.current)
	thread = jnew("Thread",runnable)
	thread.start
	Thread.stop
	assert_equal(1,runnable.result)
  end

  def test_null
	li = :java_util_ArrayList.jnew
	li.add(nil)
	assert_nil( li.get(0) )

	h = :java_util_HashMap.jnew
	assert_nil( h.get(1) )
  end

  def test_primitive
	a = -100
	ja = :Integer.jnew(a.to_s)
	assert_equal(a, ja.intValue)

	b = 1<<32
	jb = :Long.jnew(b.to_s)
	assert_equal(b, jb.longValue)

	list = :java_util_ArrayList.jnew
	list.add( 1 )
	list.add( 2 )
	list.add( 3 )
	assert_equal( 3, list.size )
	assert( !list.isEmpty )
	assert( list.contains(2) )
  end

  def test_simple_exception
	#not primitive type
	assert_raises(IOError) {
	  :Integer.jnew(Hash.new)
	  flunk
	}
	#different type
	assert_raises(INOURPC::RPCException) {
	  :Integer.jnew("aaa")
	  flunk
	}
	#application error
	assert_raises(INOURPC::RPCException) {
	  :Integer.jclass.parseInt("aaa")
	  flunk
	}
  end

  def test_callback_normal
	q = Queue.new

	jp = :Runnable.jext {|name,args|
	  assert_equal(name,"run")
	  q << "run"
	  nil
	}
	:Thread.jnew(jp).start

	jp = :Runnable.jext
	jp.jdef(:run) {
	  q << "run"
	  nil
	}
	:Thread.jnew(jp).start

	assert_equal(q.pop,"run")
	assert_equal(q.pop,"run")
  end

  def test_callback_exception1
	runnable = jextend("Runnable")
	class << runnable
	  include Test::Unit::Assertions
	  def init(t)
		@thread = t
	  end
	  def run
		begin
		  assert_raises(INOURPC::RPCException) {
			:Integer.jclass.parseInt("aaa")
			flunk
		  }
		ensure
		  @thread.wakeup
		end
		nil
	  end
	end
	runnable.init(Thread.current)
	thread = jnew("Thread",runnable)
	Thread.exclusive {
	  thread.start
	  Thread.stop
	}
  end

  def test_callback_exception2
	jimport "javassist.*"
	pool = :ClassPool.jclass.getDefault
	ct = pool.makeClass("CallbackTest")

	m = :CtNewMethod.jclass.make("public void forward() {}",ct)
	ct.addMethod m
	src = <<EOS
public String test() {
  try {
	forward();
  } catch (Throwable t) {
    return t.getClass().getName()+":"+t.getMessage();
  }
  return "NG";
}
EOS
	src = src.split("\n").join
	m = :CtNewMethod.jclass.make(src,ct)
	ct.addMethod m

	m = :CtNewMethod.jclass.make("public void forward2() {}",ct)
	ct.addMethod m
	src = <<EOS
public String test2() {
  forward2();
  return "NG";
}
EOS
	src = src.split("\n").join
	m = :CtNewMethod.jclass.make(src,ct)
	ct.addMethod m
	
	cls = ct.toClass

	obj = jextend("CallbackTest")
	assert_equal("NG",obj.test)

	def obj.forward
	  raise "callback error"
	end

	assert_match(/callback error/,obj.test)

	def obj.forward2
	  raise "callback error2"
	end

	begin
	  obj.test2
	  flunk
	rescue => e
	  assert_match(/callback error2/,e.message)
	end

  end

  def test_primitive
	a = :Integer.jclass.TYPE
	assert_equal("int",__jclassname(a.__object_id))
	b = :boolean.jclass
	assert_equal("boolean",__jclassname(b.__object_id))
  end

  def test_zero_array
	a = :java_util_ArrayList.jnew
	a.add( [:t_byte] )
	assert_equal(0,a.get(0).size)
	b = :java_util_ArrayList.jnew
	b.add( [] )
	assert_equal(0,b.get(0).size)
  end

  def test_classloader
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

	  jadd_classloader fileClassLoader

	  obj = :TestClass.jnew

	  assert_match(/TestClass/,obj.getClass.getName)
	  assert_equal("000000",obj.getId)
	  obj.setId("123456")
	  assert_equal("123456",obj.getId)
	ensure
	  FileUtils.rm('TestClass.class',{:force => true})
	  FileUtils.rm('TestClass.java',{:force => true})
	end
  end
end
