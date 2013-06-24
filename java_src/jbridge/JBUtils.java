package jbridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;



public class JBUtils {

	public static Object convertObjectType(Object src,Class type) {
		type = primitive2class(type);
		if (src instanceof Byte) {
			return canConvert((Byte)src,type);
		} else if (src instanceof Short) {
			return canConvert((Short)src,type);
		} else if (src instanceof Integer) {
			return canConvert((Integer)src,type);
		} else if (src instanceof Long) {
			return canConvert((Long)src,type);
		} else if (src instanceof Float) {
			return canConvert((Float)src,type);
		} else if (src instanceof Double) {
			return canConvert((Double)src,type);
		} else {
			return null;
		}
	}

	private static Class primitive2class(Class c) {
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

	private static Object canConvert(Byte a,Class type) {
		if (type.equals(Byte.class)) {
			return a;
		} else if (type.equals(Short.class)) {
			return new Short(a.shortValue());
		} else if (type.equals(Integer.class)) {
			return new Integer(a.intValue());
		} else if (type.equals(Long.class)) {
			return new Long(a.longValue());
		} else if (type.equals(Float.class)) {
			return new Float(a.floatValue());
		} else if (type.equals(Double.class)) {
			return new Double(a.doubleValue());
		} else {
			return null;
		}
	}

	private static Object canConvert(Short a,Class type) {
		if (type.equals(Byte.class)) {
			short s = a.shortValue();
			if ( ((byte)s) == s ) {
				return new Byte((byte)s);
			} else {
				return null;
			}
		} else if (type.equals(Short.class)) {
			return a;
		} else if (type.equals(Integer.class)) {
			return new Integer(a.intValue());
		} else if (type.equals(Long.class)) {
			return new Long(a.longValue());
		} else if (type.equals(Float.class)) {
			return new Float(a.floatValue());
		} else if (type.equals(Double.class)) {
			return new Double(a.doubleValue());
		} else {
			return null;
		}
	}

	private static Object canConvert(Integer a,Class type) {
		int s = a.intValue();
		if (type.equals(Byte.class)) {
			if ( ((byte)s) == s ) {
				return new Byte((byte)s);
			} else {
				return null;
			}
		} else if (type.equals(Short.class)) {
			if ( ((short)s) == s ) {
				return new Short((short)s);
			} else {
				return null;
			}
		} else if (type.equals(Integer.class)) {
			return a;
		} else if (type.equals(Long.class)) {
			return new Long(a.longValue());
		} else if (type.equals(Float.class)) {
			return new Float(a.floatValue());
		} else if (type.equals(Double.class)) {
			return new Double(a.doubleValue());
		} else {
			return null;
		}
	}

	private static Object canConvert(Long a,Class type) {
		long s = a.longValue();
		if (type.equals(Byte.class)) {
			if ( ((byte)s) == s ) {
				return new Byte((byte)s);
			} else {
				return null;
			}
		} else if (type.equals(Short.class)) {
			if ( ((short)s) == s ) {
				return new Short((short)s);
			} else {
				return null;
			}
		} else if (type.equals(Integer.class)) {
			if ( ((int)s) == s ) {
				return new Integer((int)s);
			} else {
				return null;
			}
		} else if (type.equals(Long.class)) {
			return a;
		} else if (type.equals(Float.class)) {
			return new Float(a.floatValue());
		} else if (type.equals(Double.class)) {
			return new Double(a.doubleValue());
		} else {
			return null;
		}
	}

	private static Object canConvert(Float a,Class type) {
		if (type.equals(Byte.class)) {
			return null;
		} else if (type.equals(Short.class)) {
			return null;
		} else if (type.equals(Integer.class)) {
			return null;
		} else if (type.equals(Long.class)) {
			return null;
		} else if (type.equals(Float.class)) {
			return a;
		} else if (type.equals(Double.class)) {
			return new Double(a.doubleValue());
		} else {
			return null;
		}
	}

	private static Object canConvert(Double a,Class type) {
		if (type.equals(Byte.class)) {
			return null;
		} else if (type.equals(Short.class)) {
			return null;
		} else if (type.equals(Integer.class)) {
			return null;
		} else if (type.equals(Long.class)) {
			return null;
		} else if (type.equals(Float.class)) {
			return new Float(a.floatValue());
		} else if (type.equals(Double.class)) {
			return a;
		} else {
			return null;
		}
	}

	public static String[] getClassInfo(Class cls) {
		List ret = new ArrayList();
		//super class
		if (cls.getSuperclass() != null) {
			ret.add("====Superclass");
			ret.add(cls.getSuperclass().getName());
		}
		ret.add("====Interfaces");
		Class[] ifs = cls.getInterfaces();
		for(int i=0;i<ifs.length;i++) {
			if (Modifier.isPublic(ifs[i].getModifiers())) {
				ret.add(ifs[i].getName());
			}
		}
		if (Modifier.isPublic(cls.getModifiers())) {
			//public method
			ret.add("====PublicMethod");
			List publicMethods = new ArrayList();
			collectMethods(cls.getDeclaredMethods(),publicMethods,true);
			ret.addAll(publicMethods);
			//protected method
			ret.add("====ProtectedMethod");
			List protectedMethod = new ArrayList();
			collectMethods(cls.getDeclaredMethods(),protectedMethod,false);
			ret.addAll(protectedMethod);
			//field
			ret.add("====Field");
			Field[] fs = cls.getDeclaredFields();
			for (int i=0;i<fs.length;i++) {
				ret.add(fs[i].getName());
			}
		}
		return (String[])ret.toArray(new String[ret.size()]);
	}

	private static void collectMethods(Method[] ms,List list,boolean isPublic) {
		for(int i=0;i<ms.length;i++) {
			String name = ms[i].getName();
			if (Modifier.isPrivate(ms[i].getModifiers()) ||
				ObjectManager.METHOD_SEND_MESSAGE.equals(name) || 
				ObjectManager.METHOD_SUPERCLASS_METHOD.equals(name) ||
				ObjectManager.METHOD_INJECT_FIELDS.equals(name) ||
				name.indexOf(ObjectManager.METHOD_ALIAS_PREFIX) == 0) {
				continue;
			}
			if ((isPublic && Modifier.isPublic(ms[i].getModifiers())) || //collect public methods
				(!isPublic && !Modifier.isPublic(ms[i].getModifiers()))) {//collect non-public methods
				if (!list.contains(ms[i].getName())) {
					list.add(ms[i].getName());
				}
			}
		}
	}
}
