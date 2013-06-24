package jbridge;

import inou.util.StringUtil;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javassist.*;
import javax.swing.JButton;
import junit.framework.TestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class MethodFinderTest extends TestCase {

	private Logger mon = Logger.getLogger(this.getClass());
	private MethodFinder2 finder;

	public void setUp() {
	}

	public void testFindingProtectedMethod() throws Exception {
		ClassLoader cl = new ClassLoader() {
				protected Class findClass(String filename) {
					File cf = new File(filename);
					byte[] ar = new byte[(int)cf.length()];
					FileInputStream in = null;
					try {
						in = new FileInputStream(cf);
						try {
							in.read(ar);
						} finally {
							if (in != null) {
								in.close();
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					String className = cf.getName();
					className = className.substring(0,className.length()-6);
					return defineClass(className,ar,0,ar.length);
				}
			};
		MethodFinder2 mf = new MethodFinder2();
		Method fm = mf.searchAllMethod(cl.getClass(),"findClass",new Object[]{"sample/Sample.class"});
		assertEquals("findClass",fm.getName());//override method
		fm = mf.searchAllMethod(cl.getClass(),"defineClass",new Object[]{"",new byte[0],new Integer(0),new Integer(0)});
		assertEquals("defineClass",fm.getName());//final method
	}

	public void testBasicMethodFinding() throws Exception {
		MethodFinder2 mf = new MethodFinder2();
		Constructor c = mf.searchConstructur(Integer.class,new Object[]{new Integer(1)});
		assertEquals(c.getName(),"java.lang.Integer");
		assertEquals(c.getDeclaringClass(),Integer.class);
		Method sm = mf.searchPublicMethod(Math.class,"sin",new Object[]{new Double(1.0)});
		assertEquals(sm.getName(),"sin");
		assertEquals(sm.getDeclaringClass(),Math.class);
		Method pm = mf.searchPublicMethod(Integer.class,"intValue",new Object[0]);
		assertEquals(pm.getName(),"intValue");
		assertEquals(pm.getDeclaringClass(),Integer.class);
		pm = mf.searchPublicMethod(Integer.class,"wait",new Object[0]);
		assertEquals(pm.getName(),"wait");
		assertEquals(pm.getDeclaringClass(),Object.class);
		Object[] aa = {new Byte((byte)25),new Short((short)200),new Integer(255)};
		c = mf.searchConstructur(java.awt.Color.class,aa);
		assertEquals(c.getName(),"java.awt.Color");
		assertEquals(c.getDeclaringClass(),Color.class);
		Object[] bb = {new Float(0f),new Float(0.5f),new Float(1.0f)};
		c = mf.searchConstructur(java.awt.Color.class,bb);
		assertEquals(c.getName(),"java.awt.Color");
		assertEquals(c.getDeclaringClass(),Color.class);
		Method fm = mf.searchPublicMethod(javax.imageio.plugins.jpeg.JPEGImageWriteParam.class,"setCompressionQuality",new Object[]{new Double(0.9)});
		assertEquals(fm.getName(),"setCompressionQuality");
		assertTrue(fm.getDeclaringClass().isAssignableFrom(javax.imageio.plugins.jpeg.JPEGImageWriteParam.class));
	}

	public void testMethodFinder() throws Exception {
		Boolean t = Boolean.TRUE, f = Boolean.FALSE;
		Object 
			ob = new Byte((byte)-1),os = new Short((short)-1),oi = new Integer(-1),
			ol = new Long(-1),of = new Float(-1.0f),od = new Double(-1);
		Object 
			ob2 = new Byte((byte)127),os2 = new Short((short)515),
			oi2 = new Integer(65539),ol2 = new Long(4294967596L);
		MethodFinder2 mf = new MethodFinder2();
		Object[] list = {
			"Byte",ob,t, "Byte",os,t, "Byte",oi,t, "Byte",ol,t, "Byte",of,f, "Byte",od,f,
			"byte",ob,t, "byte",os,t, "byte",oi,t, "byte",ol,t, "byte",of,f, "byte",od,f,

			"Short",ob,t, "Short",os,t, "Short",oi,t, "Short",ol,t, "Short",of,f, "Short",od,f,
			"short",ob,t, "short",os,t, "short",oi,t, "short",ol,t, "short",of,f, "short",od,f,

			"Integer",ob,t, "Integer",os,t, "Integer",oi,t, "Integer",ol,t, "Integer",of,f, "Integer",od,f,
			"int",ob,t, "int",os,t, "int",oi,t, "int",ol,t, "int",of,f, "int",od,f,

			"Long",ob,t, "Long",os,t, "Long",oi,t, "Long",ol,t, "Long",of,f, "Long",od,f,
			"long",ob,t, "long",os,t, "long",oi,t, "long",ol,t, "long",of,f, "long",od,f,

			"Float",ob,t, "Float",os,t, "Float",oi,t, "Float",ol,t, "Float",of,t, "Float",od,t,
			"float",ob,t, "float",os,t, "float",oi,t, "float",ol,t, "float",of,t, "float",od,t,

			"Double",ob,t, "Double",os,t, "Double",oi,t, "Double",ol,t, "Double",of,t, "Double",od,t,
			"double",ob,t, "double",os,t, "double",oi,t, "double",ol,t, "double",of,t, "double",od,t,

			"Byte",ob2,t, "Byte",os2,f, "Byte",oi2,f, "Byte",ol2,f,
			"byte",ob2,t, "byte",os2,f, "byte",oi2,f, "byte",ol2,f,

			"Short",ob2,t, "Short",os2,t, "Short",oi2,f, "Short",ol2,f,
			"short",ob2,t, "short",os2,t, "short",oi2,f, "short",ol2,f,

			"Integer",ob2,t, "Integer",os2,t, "Integer",oi2,t, "Integer",ol2,f,
			"int",ob2,t, "int",os2,t, "int",oi2,t, "int",ol2,f,

			"Long",ob2,t, "Long",os2,t, "Long",oi2,t, "Long",ol2,t,
			"long",ob2,t, "long",os2,t, "long",oi2,t, "long",ol2,t,

			"Float",ob2,t, "Float",os2,t, "Float",oi2,t, "Float",ol2,t, 
			"float",ob2,t, "float",os2,t, "float",oi2,t, "float",ol2,t, 

			"Double",ob2,t, "Double",os2,t, "Double",oi2,t, "Double",ol2,t,
			"double",ob2,t, "double",os2,t, "double",oi2,t, "double",ol2,t,

			"Boolean",Boolean.TRUE,t, "Boolean",Boolean.FALSE,t,
			"boolean",Boolean.TRUE,t, "boolean",Boolean.FALSE,t,
		};

		int counter = 0;
		while (true) {
			String name = (String)list[counter++];
			Object arg = list[counter++];
			boolean ss = (list[counter++] == Boolean.TRUE);
			MethodFinderTester test = new MethodFinderTester(name,arg,ss);
			assertTrue("MD Test: name="+name+" ("+arg.getClass().getName()+") : "+ss,
					   test.execTest(mf));
			if (counter == list.length) break;
		}
	}

	class MethodFinderTester {
		private String name;
		private Object arg;
		private boolean ss;
		MethodFinderTester(String n,Object a,boolean s) {
			name = n; arg = a; ss = s;
		}
		boolean execTest(MethodFinder2 mf) throws Exception {
			boolean test = false;
			try {
				Method m = mf.searchPublicMethod(MethodFinderTest.class,"mf_"+name,new Object[]{arg});
				test = (m != null);
			} catch (NoSuchMethodException e) {
				//e.printStackTrace();
				test = false;
			}
			return test == ss;
		}
	}

	public boolean mf_boolean(boolean a) { return a; }
	public Boolean mf_Boolean(Boolean a) { return a; }

	public byte mf_byte(byte a) { return a; }
	public short mf_short(short a) { return a; }
	public int mf_int(int a) { return a; }
	public long mf_long(long a) { return a; }
	public float mf_float(float a) { return a; }
	public double mf_double(double a) { return a; }

	public Byte mf_Byte(Byte a) { return a; }
	public Short mf_Short(Short a) { return a; }
	public Integer mf_Integer(Integer a) { return a; }
	public Long mf_Long(Long a) { return a; }
	public Float mf_Float(Float a) { return a; }
	public Double mf_Double(Double a) { return a; }

	//=====================================

	public void testFloatMethod() throws Exception {
		MethodFinder2 mf = new MethodFinder2();
		Method m = mf.searchPublicMethod(javax.imageio.plugins.jpeg.JPEGImageWriteParam.class,"setCompressionQuality",new Object[]{new Double(0.9)});
		assertEquals("setCompressionQuality",m.getName());
	}

}
