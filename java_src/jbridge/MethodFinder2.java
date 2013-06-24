package jbridge;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

import inou.net.Utils;
import inou.util.CacheHashMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class MethodFinder2 {

	private Logger monitor = Logger.getLogger(this.getClass());

	//classname -> declared methods
	private CacheHashMap methodCache = new CacheHashMap(300);

	//class#method -> method
	private CacheHashMap methodCache_public = new CacheHashMap(300);
	private CacheHashMap methodCache_all = new CacheHashMap(300);
	private CacheHashMap methodCache_static = new CacheHashMap(300);

	public MethodFinder2() {
		methodCache_public.setReplace(false);
		methodCache_all.setReplace(false);
		methodCache_static.setReplace(false);
	}

	//===================================================================
	// search the constructor
	//===================================================================

	/**
	 * search and return the constructor.
	 *
	 * @param cls class
	 * @param args argument array. if the value needs to be modified, the 
	 *             content value of the array is changed.
	 * @return a <code>Constructor</code> value
	 * @exception NoSuchMethodException if an error occurs
	 */
	public Constructor searchConstructur(Class cls,Object[] args) throws NoSuchMethodException {
		if (args == null) args = new Object[0];
		Constructor[] cs = cls.getConstructors();
		Utils.writeArguments(monitor,Level.DEBUG,"MF:SearchConstructor:",args);
		if (cs == null || cs.length == 0) {
			throw new NoSuchMethodException("No declared constructor.");
		}
		Constructor ckeep = null;
		ArgumentsHolder atkeep = null;

		for(int i=0;i<cs.length;i++) {
			Class[] types = cs[i].getParameterTypes();
			if (types.length != args.length) continue;
			ArgumentsHolder h = new ArgumentsHolder(types,args);
			int c = h.type();
			Utils.writeArguments(monitor,Level.DEBUG,"MF:  try["+c+"]",types);
			if (c == EX_NG) {
				continue;
			} else if (c == EX_OK) {
				h.translate();
				return cs[i];
			} else {
				ckeep = cs[i];
				atkeep = h;
			}
		}
		if (ckeep != null) {
			atkeep.translate();
			return ckeep;
		}
		return cls.getConstructor(Utils.object2class(args));
	}

	//===================================================================
	// search the method
	//===================================================================

	private boolean ignoreClass(Class c) {
		return ( !Modifier.isPublic(c.getModifiers()) || 
				 c == RandomAccess.class || 
				 c == Serializable.class);
	}

	private abstract class AbstractFinder {
		abstract boolean accept(Method m);
	}

	public Method searchStaticMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		if (args == null) args = new Object[0];
		Utils.writeArguments(monitor,Level.DEBUG,"MF:SearchStaticMethod:"+cls.getName()+"."+name,args);
		MethodResultSet result = searchMethodRecursive(cls,name,args,staticFinder,methodCache_static);
		if (result != null) {
			return result.get();
		}
		throw new NoSuchMethodException("Not found static method => "+cls.getName()+"#"+name+Utils.makeArgumentExp(args));
	}

	private Method[] getDeclaredMethods(Class klass) {
		Method[] ret = (Method[])methodCache.get(klass);
		if (ret != null) return ret;
		ret = klass.getDeclaredMethods();
		methodCache.put(klass,ret);
		return ret;
	}

	private List findMethod(Class klass,String name,AbstractFinder finder,Map cache) {
		Map map = (Map)cache.get(klass);
		if (map == null) {
			map = new HashMap();
			cache.put(klass,map);
		}
		List list = (List)map.get(name);
		if (list != null) return list;
		Method[] ret = getDeclaredMethods(klass);
		list = new LinkedList();
		for(int i=0;i<ret.length;i++) {
			if (ret[i].getName().equals(name) && finder.accept(ret[i])) {
				list.add(ret[i]);
			}
		}
		map.put(name,list);
		return list;
	}

	private AbstractFinder staticFinder = new AbstractFinder() {
			boolean accept(Method m) {
				return Modifier.isStatic(m.getModifiers());
			}
		};


	public Method searchPublicMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		if (args == null) args = new Object[0];
		MethodResultSet result = searchMethodRecursive(cls,name,args,publicFinder,methodCache_public);
		if (result != null) {
			return result.get();
		}
		throw new NoSuchMethodException("Not found public instance method => "+cls.getName()+"#"+name+Utils.makeArgumentExp(args));
	}

	private AbstractFinder publicFinder = new AbstractFinder() {
			boolean accept(Method m) {
				return Modifier.isPublic(m.getModifiers());
			}
		};

	public Method searchAllMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		if (args == null) args = new Object[0];
		MethodResultSet result = searchMethodRecursive(cls,ObjectManager.METHOD_ALIAS_PREFIX+name,args,allfinder,methodCache_all);
		if (result != null) {
			return result.get();
		}
		//try original method name...
		result = searchMethodRecursive(cls,name,args,allfinder,methodCache_all);
		if (result != null) {
			return result.get();
		}
		throw new NoSuchMethodException("Not found instance method => "+cls.getName()+"#"+name+Utils.makeArgumentExp(args));
	}

	private AbstractFinder allfinder = new AbstractFinder() {
			boolean accept(Method m) {
				return (!Modifier.isStatic(m.getModifiers()));
			}
		};

	private MethodResultSet searchMethodRecursive(Class cls,String name,Object[] args,AbstractFinder finder,Map cache) throws NoSuchMethodException {
		if (cls == null) return null;
		monitor.debug("MF:Searching : "+cls.getName() +" # "+name);
		MethodResultSet ret = null;
		if (!ignoreClass(cls)) {
			List list = findMethod(cls,name,finder,cache);
			for(Iterator it = list.iterator();it.hasNext();) {
				Method m = (Method)it.next();
				Class[] ts = m.getParameterTypes();
				if (ts.length != args.length) {
					if (monitor.isDebugEnabled()) {
						monitor.debug("MF:  reject: different argument num: "+ts.length+" => "+args.length);
					}
					continue;
				}
				ArgumentsHolder h = new ArgumentsHolder(ts,args);
				int c = h.type();
				if (c == EX_NG) {
					if (monitor.isDebugEnabled()) {
						Utils.writeArguments(monitor,Level.DEBUG,"MF:  reject: different argument types: "+cls.getName()+"."+name,ts);
					}
					continue;
				} else if (c == EX_OK) {
					if (monitor.isDebugEnabled()) {
						Utils.writeArguments(monitor,Level.DEBUG,"MF:   Found:"+cls.getName()+"."+name,ts);
					}
					return new MethodResultSet(m,h);
				}
				if (monitor.isDebugEnabled()) {
					Utils.writeArguments(monitor,Level.DEBUG,"MF:    Alpha:"+cls.getName()+"."+name,ts);
				}
				ret = new MethodResultSet(m,h);
			}
		}
		Class[] ifs = cls.getInterfaces();
		if (ifs != null || ifs.length > 0) {
			monitor.debug("MF:          Try interfaces.");
			for(int i=0;i<ifs.length;i++) {
				MethodResultSet mret = searchMethodRecursive(ifs[i],name,args,finder,cache);
				if (mret != null) {
					if (mret.type() == EX_OK) {
						return mret;
					} else if (mret.type() == EX_TRANS && ret == null) {
						ret = mret;
					}
				} 
			}
		}
		
		monitor.debug("MF:          Try superclass.");
		MethodResultSet mret = searchMethodRecursive(cls.getSuperclass(),name,args,finder,cache);
		if (mret != null) {
			if (mret.type() == EX_OK) {
				return mret;
			} else if (mret.type() == EX_TRANS && ret == null) {
				ret = mret;
			}
		}
		if (ret != null) {
			monitor.debug("MF:      return alpha method: "+ret.toString());
		} else {
			monitor.debug("MF:      return no method");
		}
		return ret;
	}

	//===================================================================
	// evaluate arguments
	//===================================================================

	//OK means that the method is applicable to the arguments.
	private static final int EX_OK    = 0;
	//TRANS means that the arguments are needed to be modified to apply the method.
	private static final int EX_TRANS = 1;
	//NG means that the method can not be applicable to the arguments.
	private static final int EX_NG    = 2;

	private class MethodResultSet {
		private ArgumentsHolder holder;
		private Method method;
		MethodResultSet(Method m,ArgumentsHolder a) {
			method = m; holder = a;
		}
		int type() {
			return holder.type();
		}
		Method get() {
			holder.translate();
			return method;
		}
		public String toString() {
			return method.toString();
		}
	}

	private abstract class ArgTraslator {
		abstract Object trans(Object in);
		abstract int type();
	}

	private ArgTraslator ARGT_NG = new ArgTraslator() {
			Object trans(Object in) { throw new RuntimeException("Bug"); }
			int type() { return EX_NG; }
		};

	private ArgTraslator ARGT_OK_BYTE = new ArgTraslator() {
			Object trans(Object in) { return new Byte(((Number)in).byteValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_BYTE = new ArgTraslator() {
			Object trans(Object in) { return new Byte(((Number)in).byteValue()); }
			int type() { return EX_TRANS; }
		};

	private ArgTraslator ARGT_OK_SHORT = new ArgTraslator() {
			Object trans(Object in) { return new Short(((Number)in).shortValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_SHORT = new ArgTraslator() {
			Object trans(Object in) { return new Short(((Number)in).shortValue()); }
			int type() { return EX_TRANS; }
		};

	private ArgTraslator ARGT_OK_INT = new ArgTraslator() {
			Object trans(Object in) { return new Integer(((Number)in).intValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_INT = new ArgTraslator() {
			Object trans(Object in) { return new Integer(((Number)in).intValue()); }
			int type() { return EX_TRANS; }
		};

	private ArgTraslator ARGT_OK_LONG = new ArgTraslator() {
			Object trans(Object in) { return new Long(((Number)in).longValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_LONG = new ArgTraslator() {
			Object trans(Object in) { return new Long(((Number)in).longValue()); }
			int type() { return EX_TRANS; }
		};

	private ArgTraslator ARGT_OK_FLOAT = new ArgTraslator() {
			Object trans(Object in) { return new Float(((Number)in).floatValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_FLOAT = new ArgTraslator() {
			Object trans(Object in) { return new Float(((Number)in).floatValue()); }
			int type() { return EX_TRANS; }
		};

	private ArgTraslator ARGT_OK_DOUBLE = new ArgTraslator() {
			Object trans(Object in) { return new Double(((Number)in).doubleValue()); }
			int type() { return EX_OK; }
		};
	private ArgTraslator ARGT_TR_DOUBLE = new ArgTraslator() {
			Object trans(Object in) { return new Double(((Number)in).doubleValue()); }
			int type() { return EX_TRANS; }
		};


	private class ArgumentsHolder {

		private Class[] signatureTypes;
		private Object[] argumentObjects;
		private Class[] argumentTypes;
		private ArgTraslator[] translators;
		private int type;

		ArgumentsHolder(Class[] ts,Object[] args) {
			signatureTypes = ts;
			argumentObjects = args;
			argumentTypes = new Class[args.length];
			translators = new ArgTraslator[args.length];
			for(int i=0;i<args.length;i++) {
				if (args[i] != null) {
					argumentTypes[i] = args[i].getClass();
				}
			}
			type = check();
		}

		int type() {
			return type;
		}

		private int check() {
			int ret = EX_OK;
			for (int j=0;j<argumentTypes.length;j++) {
				Object a = argumentObjects[j];
				Class t = signatureTypes[j];
				if (!t.isInstance(a)) {
					if (a == null) {
						if (t.isPrimitive()) {
							return EX_NG;
						} else {
							continue;
						}
					} else if ( (t.equals(Boolean.TYPE) || t.equals(Boolean.class)) &&
								(a instanceof Boolean)) {
						continue;
					} else if (a instanceof Number) {
						ArgTraslator at = tryConvert(a,t);
						if (at == null) {
							continue;
						} else if (at == ARGT_NG) {
							return EX_NG;
						} else if (at.type() == EX_OK) {
							translators[j] = at;
							continue;
						} else {
							translators[j] = at;
							ret = EX_TRANS;
							continue;
						}
					}
					return EX_NG;
				}
			}
			return ret;
		}

		private ArgTraslator tryConvert(Object src,Class type) {
			type = primitive2class(type);
			if (src instanceof Byte) {
				return tryConvertByte((Byte)src,type);
			} else if (src instanceof Short) {
				return tryConvertShort((Short)src,type);
			} else if (src instanceof Integer) {
				return tryConvertInt((Integer)src,type);
			} else if (src instanceof Long) {
				return tryConvertLong((Long)src,type);
			} else if (src instanceof Float) {
				return tryConvertFloat((Float)src,type);
			} else if (src instanceof Double) {
				return tryConvertDouble((Double)src,type);
			} else {
				return ARGT_NG;
			}
		}

		private Class primitive2class(Class c) {
			if (c.equals(Byte.TYPE)) {
				return Byte.class;
			} else if (c.equals(Double.TYPE)) {
				return Double.class;
			} else if (c.equals(Float.TYPE)) {
				return Float.class;
			} else if (c.equals(Integer.TYPE)) {
				return Integer.class;
			} else if (c.equals(Long.TYPE)) {
				return Long.class;
			} else if (c.equals(Short.TYPE)) {
				return Short.class;
			}
			return c;
		}

		private ArgTraslator tryConvertByte(Byte a,Class type) {
			if (type.equals(Byte.class)) {
				return null;
			} else if (type.equals(Short.class)) {
				return ARGT_OK_SHORT;
			} else if (type.equals(Integer.class)) {
				return ARGT_OK_INT;
			} else if (type.equals(Long.class)) {
				return ARGT_OK_LONG;
			} else if (type.equals(Float.class)) {
				return ARGT_TR_FLOAT;
			} else if (type.equals(Double.class)) {
				return ARGT_TR_DOUBLE;
			} else {
				return ARGT_NG;
			}
		}

		private ArgTraslator tryConvertShort(Short a,Class type) {
			if (type.equals(Byte.class)) {
				short s = a.shortValue();
				if ( ((byte)s) == s ) {
					return ARGT_TR_BYTE;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Short.class)) {
				return null;
			} else if (type.equals(Integer.class)) {
				return ARGT_OK_INT;
			} else if (type.equals(Long.class)) {
				return ARGT_OK_LONG;
			} else if (type.equals(Float.class)) {
				return ARGT_TR_FLOAT;
			} else if (type.equals(Double.class)) {
				return ARGT_TR_DOUBLE;
			} else {
				return ARGT_NG;
			}
		}

		private ArgTraslator tryConvertInt(Integer a,Class type) {
			int s = a.intValue();
			if (type.equals(Byte.class)) {
				if ( ((byte)s) == s ) {
					return ARGT_TR_BYTE;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Short.class)) {
				if ( ((short)s) == s ) {
					return ARGT_TR_SHORT;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Integer.class)) {
				return null;
			} else if (type.equals(Long.class)) {
				return ARGT_OK_LONG;
			} else if (type.equals(Float.class)) {
				return ARGT_TR_FLOAT;
			} else if (type.equals(Double.class)) {
				return ARGT_TR_DOUBLE;
			} else {
				return ARGT_NG;
			}
		}

		private ArgTraslator tryConvertLong(Long a,Class type) {
			long s = a.longValue();
			if (type.equals(Byte.class)) {
				if ( ((byte)s) == s ) {
					return ARGT_TR_BYTE;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Short.class)) {
				if ( ((short)s) == s ) {
					return ARGT_TR_SHORT;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Integer.class)) {
				if ( ((int)s) == s ) {
					return ARGT_TR_INT;
				} else {
					return ARGT_NG;
				}
			} else if (type.equals(Long.class)) {
				return null;
			} else if (type.equals(Float.class)) {
				return ARGT_TR_FLOAT;
			} else if (type.equals(Double.class)) {
				return ARGT_TR_DOUBLE;
			} else {
				return ARGT_NG;
			}
		}

		private ArgTraslator tryConvertFloat(Float a,Class type) {
			if (type.equals(Byte.class)) {
				return ARGT_NG;
			} else if (type.equals(Short.class)) {
				return ARGT_NG;
			} else if (type.equals(Integer.class)) {
				return ARGT_NG;
			} else if (type.equals(Long.class)) {
				return ARGT_NG;
			} else if (type.equals(Float.class)) {
				return null;
			} else if (type.equals(Double.class)) {
				return ARGT_OK_DOUBLE;
			} else {
				return ARGT_NG;
			}
		}

		private ArgTraslator tryConvertDouble(Double a,Class type) {
			if (type.equals(Byte.class)) {
				return ARGT_NG;
			} else if (type.equals(Short.class)) {
				return ARGT_NG;
			} else if (type.equals(Integer.class)) {
				return ARGT_NG;
			} else if (type.equals(Long.class)) {
				return ARGT_NG;
			} else if (type.equals(Float.class)) {
				return ARGT_TR_FLOAT;
			} else if (type.equals(Double.class)) {
				return null;
			} else {
				return ARGT_NG;
			}
		}

		void translate() {
			for(int i=0;i<argumentObjects.length;i++) {
				if (translators[i] != null) {
					argumentObjects[i] = translators[i].trans(argumentObjects[i]);
				}
			}
		}
	}

	
	//===================================================================
	// TEST
	//===================================================================

	public static void main(String[] args) throws Exception {
		performanceTest();
	}

	private static void performanceTest() throws Exception {
		Object[][] src = {
			new Object[]{java.util.ArrayList.class,
						 new Object[]{"add",new Object[]{new Integer(1),new Object()}},
						 new Object[]{"add",new Object[]{"aaa"}},
						 new Object[]{"get",new Object[]{new Integer(0)}},
						 new Object[]{"toArray",null},
						 new Object[]{"toArray",new Object[]{new Object[5]}},
						 new Object[]{"getClass",null},
						 new Object[]{"remove",new Integer(0)},
						 new Object[]{"iterator",null},
						 new Object[]{"hashCode",null},
			},
			new Object[]{java.util.LinkedList.class,
						 new Object[]{"add",new Object[]{new Integer(1),new Object()}},
						 new Object[]{"add",new Object[]{"aaa"}},
						 new Object[]{"get",new Object[]{new Integer(0)}},
						 new Object[]{"toArray",null},
						 new Object[]{"toArray",new Object[]{new Object[5]}},
						 new Object[]{"getClass",null},
						 new Object[]{"remove",new Integer(0)},
						 new Object[]{"iterator",null},
						 new Object[]{"hashCode",null},
			},
			new Object[]{java.util.HashMap.class,
						 new Object[]{"put",new Object[]{"key","content"}},
						 new Object[]{"remove",new Object[]{"aaa"}},
						 new Object[]{"get",new Object[]{"aaa"}},
						 new Object[]{"values",null},
						 new Object[]{"getClass",null},
						 new Object[]{"hashCode",null},
			},
			new Object[]{javax.swing.JButton.class,
						 new Object[]{"updateUI",null},
						 new Object[]{"setText",new Object[]{"aaa"}},
						 new Object[]{"getText",null},
						 new Object[]{"addActionListener",new java.awt.event.ActionListener() {public void actionPerformed(java.awt.event.ActionEvent e) {}} },
						 new Object[]{"paintImmediately",new Object[]{new Integer(1),new Integer(1),new Integer(1),new Integer(1),}},
						 new Object[]{"setBounds",new Object[]{new Integer(1),new Integer(1),new Integer(1),new Integer(1),}},
						 new Object[]{"hashCode",null},
			},
			new Object[]{javax.swing.JDialog.class,
						 new Object[]{"getRootPane",null},
						 new Object[]{"setTitle",new Object[]{"aaa"}},
						 new Object[]{"setLayout",new Object[]{new java.awt.BorderLayout()}},
						 new Object[]{"setModal",new Object[]{Boolean.TRUE}},
						 new Object[]{"add",new Object[]{new java.awt.Label("")}},
						 new Object[]{"setBounds",new Object[]{new Integer(1),new Integer(1),new Integer(1),new Integer(1),}},
						 new Object[]{"hashCode",null},
			},
			new Object[]{java.lang.System.class,
						 new Object[]{"getProperty","aaa"},
						 new Object[]{"setProperty",new Object[]{"key","content"}},
						 new Object[]{"getClass",null},
						 new Object[]{"wait",null},
						 new Object[]{"equals",new Object()},
						 new Object[]{"hashCode",null},
			},
		};
		//src[class][method] = {name, args}
		MethodFinder2 mf = new MethodFinder2();
		int num = 3000;
		int delta = num/10;
		long start = System.currentTimeMillis();
		for (int loop=0;loop<num;loop++) {
			for (int i=0;i<src.length;i++) {
				Class klass = (Class)(src[i][0]);
				for(int j=1;j<src[i].length;j++) {
					Object[] line = (Object[])src[i][j];
					String name = (String)line[0];
					Object[] args = null;
					if (line[1] == null) {
						args = new Object[0];
					} else if (line[1] instanceof Object[]) {
						args = (Object[])line[1];
					} else {
						args = new Object[]{line[1]};
					}
					Method m = mf.searchPublicMethod(klass,name,args);
					if (m.getName().equals(name) && 
						m.getParameterTypes().length == args.length) {
						continue;
					}
					System.out.println(klass.getName()+"#"+name+Utils.makeArgumentExp(args));
				}
			}
			if ( (loop%delta) == 0) System.out.print(".");
		}
		long end = System.currentTimeMillis();
		System.out.println();
		System.out.println("Time: "+(end-start));
	}

}
