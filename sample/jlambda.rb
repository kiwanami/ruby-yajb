#!/usr/bin/ruby

# 
# jlambda programming sample script: 
#       Building Java class and jlambda
# 

require 'yajb'

include JavaBridge

################################
# 
# makeing Java classes
#

# simple class

c = JClassBuilder.new("AAA")
c.add_field("private int num;")
c.add_constructor("public AAA(int a) {num = a;}")
c.add_method("public int square() { return num * num;}")
puts "Simple class: #{c.new_instance(5).square}"

# implementation of an interface 

jimport "java.util.*"
c = JClassBuilder.new("NumComp")
c.add_interface("java.util.Comparator") # must be full qualified class name
c.add_method(<<'JAVA')
public int compare(Object o1, Object o2) do
  int i1 = ((Number)o1).intValue();
  int i2 = ((Number)o2).intValue();
  return i2-i1;
end
JAVA
sample = [9,1,8,2,7,3,6,4,5,0,10]
list = :ArrayList.jnew
sample.each {|i|
  list.add( i )
}
:Collections.jclass.sort(list, c.new_instance)
p "Sort:",list.toArray


################################
# 
# jlambda

# simple usage : transport data to java scriptlet

p1 = jlambda("int num", " return new Integer(num*2);") # one line
puts "jldambda : #{p1.call(5)}"

p2 = jlambda("int num", <<JAVA ) #"do" and "end" keyword will be replaced "{" and "}".
int[] a = new int[num];
for(int i=0;i<num;i++) do
  a[i] = i; 
end 
return a;
JAVA
p "Making array:",p2.call(3)

# jlambda sample2 : using the jlambda as a block

p1 = jlambda("int i", "return new Integer(i*2);")
p2 = lambda {|i| i*2}
sample = 1..5
p "Map:",sample.map(&p1)

c1 = jlambda("String i", "return (i.indexOf(\"H\") > 0) ? i : null;")
c2 = lambda {|i| i =~ /H/ }
sample = ["JAVA","RUBY","PYTHON","SCHEME","OCAML","PHP"]
p "Select:",sample.select(&c1)
