# 
# RubyUnit jlambda test program
# 

require 'test/unit'
require 'yajb'

JBRIDGE_OPTIONS = {
#  :jvm_log_file => "jlog.txt",
#  :jvm_log_level => "verbose",
#  :bridge_log => true,
  :jvm_stdout => :never
}

include JavaBridge

class JlambdaUtilTest < Test::Unit::TestCase

  def test_jlambda1
	p1 = jlambda("int num", " return new Integer(num*2);")
	p2 = jlambda("int num", <<JAVA )
	int[] a = new int[num];
	for(int i=0;i<num;i++) do 
	  a[i] = i; 
	end 
	return a;
JAVA
	assert_equal(10,p1.call(5))
	assert_equal([0,1,2],p2.call(3))
  end

  def test_jlambda_block
	p1 = jlambda("int i", "return new Integer(i*2);")
	p2 = lambda {|i| i*2}
	sample = [1,2,3,4]
	assert_equal(sample.map(&p2), sample.map(&p1))

	c1 = jlambda("String i", "return (i.indexOf(\"H\") > 0) ? i : null;")
	c2 = lambda {|i| i =~ /H/ }
	sample = ["JAVA","RUBY","PYTHON","SCHEME","OCAML","PHP"]
	assert_equal(sample.select(&c2), sample.select(&c1))
  end

  def test_make_class1
	c = JClassBuilder.new("AAA")
	c.add_field("private int num;")
	c.add_constructor("public AAA(int a) {num = a;}")
	c.add_method("public int square() { return num * num;}")
	assert_equal(c.new_instance(5).square,25)
  end

  def test_make_class2
	jimport "java.util.*"
	c = JClassBuilder.new("NumComp")
	c.add_interface("java.util.Comparator") # must be full qualified class name
	c.add_constructor("public NumComp() {}")
	c.add_method(<<JAVA)
	public int compare(Object o1, Object o2) do
	  int i1 = ((Number)o1).intValue();
	  int i2 = ((Number)o2).intValue();
	  return i1-i2;
	end
JAVA
	sample = [9,1,8,2,7,3,6,4,5,0,10]
	list = :ArrayList.jnew
	sample.each {|i|
	  list.add( i )
	}
	:Collections.jclass.sort(list, c.new_instance)
	assert_equal(sample.sort, list.toArray)
  end

end
