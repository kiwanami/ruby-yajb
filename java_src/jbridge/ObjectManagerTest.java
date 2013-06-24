package jbridge;


import inou.util.StringUtil;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javassist.*;
import javax.swing.JButton;
import junit.framework.TestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class ObjectManagerTest extends TestCase {

	private Logger mon = Logger.getLogger(this.getClass());
	private ObjectManager manager;
	private SessionManager sessionManager;

	public void setUp() {
		IOverrideCall broker = new IOverrideCall() {
				public Object call(Object sid,Object objectId,String methodName,Object[] args,Class rt) {
				String[] ss = new String[args.length];
				for(int i=0;i<ss.length;i++) {
					ss[i] = args[i].toString();
				}
				mon.debug("## IR.call("+sid+": "+objectId+": "+methodName+": "+StringUtil.conbine(ss, ",")+") : "+rt.toString());
				return "SUBCLASS";
			}
		};
		IObjectTransformer transformer = new IObjectTransformer() {
				public Object exportFilter(Object arg) {
					if (arg == null) return null;
					if (arg.getClass().isPrimitive() || arg instanceof String || 
						arg instanceof Integer || arg instanceof Double || 
						arg instanceof Long || arg instanceof Short ||
						arg instanceof Byte || arg instanceof Float || 
						arg instanceof Long) {
						return arg;
					}
					return null;
				}
				public Object importFilter(Object obj) {
					return obj;
				}
			};
		sessionManager = new SessionManager(broker);
		manager = new ObjectManager(sessionManager,transformer);
		sessionManager.init(manager);
	}

	public void testNormalObject() throws Exception {
		//test normal object.call
		Object obj = manager.getObject(manager.createObject("jbridge.ObjectManagerTest",null));
		
		String ret = (String)manager.searchPublicMethod(obj.getClass(),"sampleMethod",null).invoke(obj,null);
		assertEquals("call:","SUPERCLASS",ret);
	}

	public void testDerivedObject() throws Exception {
		//test extend object.call -> subclass (not implemented)
		Object ext = manager.getObject(manager.extendObject("jbridge.ObjectManagerTest,java.awt.event.ActionListener",null));
		String ret = (String)manager.searchPublicMethod(ext.getClass(),"sampleMethod",null).invoke(ext,null);
		assertEquals("extend_call(not impl):","SUPERCLASS",ret);
		
		//test extend object.call -> subclass (implemented)
		Field f = ext.getClass().getField(ObjectManager.FIELD_IMPL_FLAG_PREFIX+"sampleMethod");
		f.set(ext,Boolean.TRUE);
		ret = (String)manager.searchPublicMethod(ext.getClass(),"sampleMethod",null).invoke(ext,null);
		assertEquals("extend_call(impl):","SUBCLASS",ret);

		//test extend object.call -> interface subclass 
		Object[] args = new Object[]{new ActionEvent(new JButton(),0,null)};
		ret = (String)manager.searchPublicMethod(ext.getClass(),"actionPerformed",args).invoke(ext,args);
		assertNull("interface_call:",ret);

		//test extend object.call -> super
		Object[] args2 = new Object[]{"sampleMethod",new Object[0]};
		Method mm = manager.searchPublicMethod(ext.getClass(),ObjectManager.METHOD_SUPERCLASS_METHOD,args2);
		ret = (String)mm.invoke(ext,args2);
		assertEquals("super_call:","SUPERCLASS",ret);

		//test extend object.call -> super.protected
		Object[] args1 = new Object[]{"protectedMethod",new Object[0]};
		mm = manager.searchPublicMethod(ext.getClass(),ObjectManager.METHOD_SUPERCLASS_METHOD,args1);
		ret = (String)mm.invoke(ext,args1);
		assertEquals("protected_call:","PROTECTED",ret);
	}

	public void testImport() throws Exception {
		assertNotNull(manager.staticReference("System"));

		Object id = null;
		try {
			id = manager.createObject("Point",null);
		} catch (ClassNotFoundException e) {
		}
		assertNull(id);
		manager.addImport("java.awt.*");
		id = manager.createObject("Point",null);
		Object obj = manager.getObject(id);
		assertEquals(java.awt.Point.class,obj.getClass());
	}

	public void testImportOverride() throws Exception {
		Object id = null;
		try {
			id = manager.staticReference("List");
		} catch (ClassNotFoundException e) {
		}
		assertNull(id);
		manager.addImport("java.awt.*");
		id = manager.staticReference("List");
		Object obj = manager.getObject(id);
		assertTrue(java.awt.List.class.equals(obj));

		manager.addImport("java.util.List");
		id = null;
		try {
			id = manager.staticReference("List");//can not create instance
		} catch (RuntimeException e){
		}
		obj = manager.getObject(id);
		assertTrue(java.util.List.class.equals(obj));
	}

	public void testSameHashObject() throws Exception {
		ClassPool pool = ClassPool.getDefault();
		
		CtClass ct1 = pool.makeClass("AAA");
		CtMethod m1 = CtNewMethod.make("public String call() { return \"aaa\";}",ct1);
		Object k1 = manager.obj2id(m1);
	
		CtClass ct2 = pool.makeClass("BBB");
		CtMethod m2 = CtNewMethod.make("public String call() { return \"bbb\";}",ct2);
		Object k2 = manager.obj2id(m2);

		assertTrue(m1 != m2);
		assertTrue(m1.equals(m2));
		assertFalse(k1.equals(k2));
	}

	public void testNonPublicObject() throws Exception {
		Object[] aa = {new Integer(1), new Integer(2), new Integer(3),};
		List list = Arrays.asList(aa);
		MethodFinder2 mf = new MethodFinder2();
		Method m = mf.searchPublicMethod(list.getClass(),"toArray",null);
		Object ret = m.invoke(list,null);
		assertTrue(ret instanceof Object[]);
		assertEquals(aa[0], ((Object[])ret)[0] );
	}

	public void testArgumentChoise() throws Exception {
		Object[] aa = {new Byte((byte)25),new Short((short)200),new Integer(255)};
		Object obj = manager.getObject(manager.createObject("java.awt.Color",aa));
		assertTrue(new java.awt.Color(25,200,255).equals(obj));
		Object[] bb = {new Float(0f),new Float(0.5f),new Float(1.0f)};
		obj = manager.getObject(manager.createObject("java.awt.Color",bb));
		assertTrue(new java.awt.Color(0f,0.5f,1.0f).equals(obj));
	}

	public void testClassLoader() throws Exception {
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
		manager.addClassLoader(cl);
		Object obj = manager.getObject(manager.createObject("sample/Sample.class",null));
		assertEquals("Sample",obj.getClass().getName());
	}

	//=============================

	protected String protectedMethod() {
		return "PROTECTED";
	}

	public String sampleMethod() {
		return "SUPERCLASS";
	}


}
