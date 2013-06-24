package jbridge;

import java.util.Arrays;
import java.util.List;
import inou.util.StringUtil;
import inou.net.Utils;
import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;


public class BridgeServerTest extends TestCase {
	
	private BridgeServer bs;

	private Logger mon = Logger.getLogger(this.getClass());

	public void setUp() {
		IOverrideCall broker = new IOverrideCall() {
				public Object call(Object sid,Object objectId,String methodName,Object[] args,Class rt) {
					String[] ss = new String[args.length];
					for(int i=0;i<ss.length;i++) {
						ss[i] = args[i].toString();
					}
					Utils.writeArray(mon,Level.DEBUG,new Object[]{"## IR.call(",sid,": ",objectId,": ",methodName,": ",StringUtil.conbine(ss, ","),") : ",rt.toString()});
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
		bs = new BridgeServer(broker,transformer);
	}

	private Integer to_i(int i) {
		return new Integer(i);
	}

	public void testNormalObject() throws Exception {
		Object id = bs.jnew("java.awt.Point",new Object[]{to_i(10),to_i(20)});
		assertTrue(bs.isProxyId(id));
		assertEquals("java.awt.Point",bs.inspectClassname(id));
		assertEquals(to_i(10),bs.ref(id,"x"));
		bs.set(id,"x",to_i(20));
		assertEquals(to_i(20),bs.ref(id,"x"));
		bs.call(id,"translate",new Object[]{to_i(-10),to_i(-20)});
		assertEquals(to_i(10),bs.ref(id,"x"));
		assertEquals(to_i(0), bs.ref(id,"y"));
	}

	private Double to_f(double d) {
		return new Double(d);
	}

	public void testStatic() throws Exception {
		Object id = bs.getStaticClass("java.lang.Math");
		assertTrue(bs.isProxyId(id));
		assertEquals(to_f(Math.PI),bs.ref(id,"PI"));
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{to_f(0)}));
	}

	public void testReturnValue() throws Exception {
		Object srt = bs.getStaticClass("java.lang.Runtime");
		assertTrue(bs.isProxyId(srt));
		Object rt = bs.call(srt,"getRuntime",null);
		assertTrue(bs.isProxyId(rt));
		assertEquals("java.lang.Runtime",bs.inspectClassname(rt));
	}

	public void testArgument() throws Exception {
		Object str = bs.jnew("java.lang.String",new Object[]{"10"});
		assertTrue(bs.isProxyId(str));

		Object it = bs.getStaticClass("java.lang.Integer");
		assertTrue(bs.isProxyId(it));

		Object[] args = {str,new Integer(16)};
		Object rt = bs.call(it,"parseInt",args);
		assertEquals(to_i(16),rt);

		try {
			Object[] argse = {"aaa"};
			Object ret = bs.call(it,"parseInt",argse);
			fail(ret.toString());
		} catch (NumberFormatException e) {
			assertTrue(e.getMessage(), e.getMessage().indexOf("aaa") >= 0);
		}
	}

	public void testOverride() throws Exception {
		Object id = bs.jextend("jbridge.BridgeServerTest",null);
		assertTrue(bs.isProxyId(id));
		Object[] args = {};
		String ret = (String)bs.call(id,"sampleMethod",args);
		assertEquals("SUPERCLASS",ret);
		bs.setImplementFlag(id,"sampleMethod",Boolean.TRUE);
		ret = (String)bs.call(id,"sampleMethod",args);
		assertEquals("SUBCLASS",ret);

		ret = (String)bs.superCall(id,"protectedMethod",args);
		assertEquals("PROTECTED",ret);
	}

	public void testTranslateArgument() throws Exception {
		Object id = bs.getStaticClass("java.lang.Math");
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{new Float(0)}));
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{new Integer(0)}));
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{new Long(0)}));
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{new Short((short)0)}));
		assertEquals(to_f(0),bs.call(id,"sin",new Object[]{new Byte((byte)0)}));
	}

	public void testNullArgument1() throws Exception {
		Object lid = bs.jnew("java.util.ArrayList",null);
		bs.call(lid,"add",new Object[]{null});
		assertEquals(to_i(1), bs.call(lid,"size",null));
	}

	public void testNullArgument2() throws Exception {
		Object tid = bs.jnew("java.util.HashMap",null);
		bs.call(tid,"put",new Object[]{"key",null});
		assertEquals(to_i(1), bs.call(tid,"size",null));
		assertNull( bs.call(tid,"get",new Object[]{"key"}) );
	}

	public void testConstructor() throws Exception {
		Object id = bs.jnew("java.awt.Point",new Object[]{to_i(12),to_i(15)});
		assertTrue("Normal Constructor",bs.isProxyId(id));

		Object obj0 = bs.jextend("java.awt.Point",null);
		assertTrue("Default Constructor",bs.isProxyId(obj0));

		Object obj2 = bs.jextend("java.awt.Point",new Object[]{to_i(12),to_i(15)});
		assertTrue("Primitive Constructor",bs.isProxyId(obj2));

		Object obj1 = bs.jextend("java.awt.Point",new Object[]{id});
		assertTrue("Object Constructor",bs.isProxyId(obj1));
	}

	public void testClassinfo() throws Exception {
		Object id = bs.jnew("java.lang.Integer",new Object[]{to_i(12)});
		assertTrue("Normal Constructor",bs.isProxyId(id));
		Object obj = bs.getObject(id);

		List info = Arrays.asList(bs.getClassInfo( obj.getClass().getName() ).split(","));
		assertTrue("Contains fields",info.contains("====Superclass"));
		assertTrue("Contains superclass",info.contains("java.lang.Number"));
		assertTrue("Contains fields",info.contains("====Field"));
		assertTrue("Contains public field: MAX_VALUE",info.contains("MAX_VALUE"));
		assertTrue("Contains public method",info.contains("====PublicMethod"));
		assertTrue("Contains public method: intValue",info.contains("intValue"));
		assertTrue("Contains protected method",info.contains("====ProtectedMethod"));
		assertFalse("Not contains artifacted method",info.contains("__"));
	}

	protected String protectedMethod() {
		return "PROTECTED";
	}

	public String sampleMethod() {
		return "SUPERCLASS";
	}
}
