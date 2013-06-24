package jbridge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import inou.net.Utils;
import inou.util.StringUtil;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class BridgeServer {

	private Logger monitor = Logger.getLogger(this.getClass());
	private ObjectManager objectManager;
	private SessionManager sessionManager;

	public BridgeServer(IOverrideCall rb,IObjectTransformer ot) {
		sessionManager = new SessionManager(rb);
		objectManager = new ObjectManager(sessionManager,ot);
		sessionManager.init(objectManager);
	}

	public Object jnew(String fqcn,Object[] args) throws ClassNotFoundException,InstantiationException,NoSuchMethodException,IllegalAccessException,InvocationTargetException {
		try {
			args = objectManager.id2objs(args);
			Utils.writeArguments(monitor,Level.INFO,"!! [  NEW ] "+fqcn,args);
			return objectManager.createObject(fqcn,args);
		} catch (InvocationTargetException e) {
			monitor.warn(e);
			Throwable t = e.getCause();
			if (t != null && t instanceof RuntimeException) {
				throw (RuntimeException)t;
			} else {
				throw e;
			}
		}
	}

	public Object jextend(String fqcns,Object[] args) throws ClassNotFoundException,InstantiationException,NoSuchMethodException,IllegalAccessException,InvocationTargetException {
		try {
			args = objectManager.id2objs(args);
			Utils.writeArguments(monitor,Level.INFO,"!! [EXTEND] "+fqcns,args);
			return objectManager.extendObject(fqcns,args);
		} catch (InvocationTargetException e) {
			monitor.warn(e);
			Throwable t = e.getCause();
			if (t != null && t instanceof RuntimeException) {
				throw (RuntimeException)t;
			} else {
				throw e;
			}
		}
	}

	public String inspectClassname(Object proxyId) {
		return objectManager.inspectClassnameByKey(proxyId);
	}

	public Object ref(Object proxyId,String fieldName) throws IllegalAccessException {
		Object obj = objectManager.getObject(proxyId);
		if (obj == null) {
			throw new RuntimeException("Object not found, key="+proxyId);
		}
		try {
			Object ret = null;
			String className = null;
			if (obj instanceof Class) {
				Field f = ((Class)obj).getField(fieldName);
				ret = f.get(null);
				className = ((Class)obj).getName();
			} else {
				Field f = obj.getClass().getField(fieldName);
				ret = f.get(obj);
				className = obj.getClass().getName();
			}
			Utils.writeArray(monitor,Level.INFO,
							 new Object[]{"!! [  REF ] ", ret, " <= ",
										  className,"#",fieldName});
			return returnFilter(ret);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("No such field: "+fieldName);
		}
	}

	public void set(Object proxyId,String fieldName,Object value) throws IllegalAccessException {
		value = objectManager.id2obj(value);
		Object obj = objectManager.getObject(proxyId);
		if (obj == null) {
			throw new RuntimeException("Object not found, key="+proxyId);
		}
		try {
			Object ret = null;
			String className = null;
			if (obj instanceof Class) {
				Field f = ((Class)obj).getField(fieldName);
				f.set(null,value);
				className = ((Class)obj).getName();
			} else {
				Field f = obj.getClass().getField(fieldName);
				f.set(obj,value);
				className = obj.getClass().getName();
			}
			Utils.writeArray(monitor,Level.INFO,
							 new Object[]{"!! [  SET ] ", className,"#",fieldName," <= ",value});
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("No such field: "+fieldName);
		}
	}

	private Object generalCall(Object proxyId,String methodName,Object[] targs) throws IllegalAccessException,InvocationTargetException {
		Object obj = objectManager.getObject(proxyId);
		if (obj == null) {
			throw new RuntimeException("Object not found, key="+proxyId);
		}
		try {
			Object ret = null;
			String className = null;
			if (obj instanceof Class) {
				//static call
				try {
					ret = objectManager.searchStaticMethod((Class)obj,methodName,targs).invoke(null,targs);
					className = ((Class)obj).getName();
				} catch (NoSuchMethodException e) {
					try {
						//for Class object
						ret = objectManager.searchPublicMethod(obj.getClass(),methodName,targs).invoke(obj,targs);
						className = obj.getClass().getName();
					} catch (NoSuchMethodException ex) {
						throw e;
					}
				}
			} else {
					//instance call
				ret = objectManager.searchPublicMethod(obj.getClass(),methodName,targs).invoke(obj,targs);
				className = obj.getClass().getName();
			}
			Utils.writeArguments(monitor,Level.INFO,new Object[]{"!! [ CALL ] ",ret," <= ",className,"#",methodName},targs);
			return returnFilter(ret);
		} catch (NoSuchMethodException e) {
			monitor.warn(e);
			if (obj instanceof Class) {
				throw new RuntimeException("No such method: "+methodName+" in "+((Class)obj).getName()+", "+obj.getClass().getName());
			} else {
				throw new RuntimeException("No such method: "+methodName+" in "+obj.getClass().getName());
			}
		} catch (Throwable e) {
			Utils.writeArguments(monitor,Level.INFO,
								 new Object[]{"!! [=EXCEPTION=]  ",
											  e.getClass().getName()," | ",e.getMessage(),
											  " <= ",obj.getClass().getName(),"#",
											  methodName},targs);
			Throwable t = e;
			while (true) {
				t = e.getCause();
				if (t != null) {
					monitor.debug("  -> "+t.getClass().getName());
					e = t;
					continue;
				}
				break;
			}
			if (e != null && e instanceof RuntimeException) {
				monitor.info("    => "+e.getClass().getName());
				throw (RuntimeException)e;
			} else {
				monitor.warn(e);
				throw new RuntimeException(e);
			}
		}
	}

	private Object returnFilter(Object ret) {
		if (ret == null) return null;
		return objectManager.obj2id(ret);
	}

	public Object call(Object proxyId,String methodName,Object[] args) throws IllegalAccessException,InvocationTargetException {
		return generalCall(proxyId,methodName,objectManager.id2objs(args));
	}

	public Object superCall(Object proxyId,String methodName,Object[] args) throws IllegalAccessException,InvocationTargetException {
		Object[] rargs = {methodName,objectManager.id2objs(args)};
		return generalCall(proxyId,ObjectManager.METHOD_SUPERCLASS_METHOD,rargs);
	}

	public Object sessionCall(Object sid,ISessionProcedure _sp) throws IllegalAccessException,InvocationTargetException {
		final ISessionProcedure sp = _sp;
		try {
			return sessionManager.sessionCall(sid,sp);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setImplementFlag(Object proxyId,String methodName,Boolean flag) throws NoSuchMethodException, IllegalAccessException {
		Object obj = objectManager.getObject(proxyId);
		if (obj == null) {
			throw new RuntimeException("Object not found, key="+proxyId);
		}
		try {
			Field f = obj.getClass().getField(ObjectManager.FIELD_IMPL_FLAG_PREFIX+methodName);
			f.set(obj,flag);
			Utils.writeArray(monitor,Level.INFO,new Object[]{"!! [ IMPL ] ",proxyId.toString(),methodName," <= ",flag.toString()});
		} catch (IllegalAccessException e) {
			throw new RuntimeException("BUG: can not set an impl-flag: "+methodName,e);
		} catch (NoSuchFieldException e) {
			//Maybe abstract class
		}
	}

	public void jimport(String lines) {
		objectManager.addImport(lines);
		monitor.info("!! [IMPORT]  "+lines);
	}

	public void unlink(Object proxyId) {
		Object obj = objectManager.getObject(proxyId);
		if (obj != null) {
			objectManager.removeObject(proxyId);
			monitor.info("!! [UNLINK]  "+proxyId+"  ("+obj.toString()+")");
		} else {
			monitor.info("!! [UNLINK]  "+proxyId+"  (miss! object not found.)");
		}
	}

	public boolean isProxyId(Object obj) {
		return objectManager.isKey(obj);
	}

	public Object getObject(Object proxyId) {
		return objectManager.getObject(proxyId);
	}

	public void exit() {
		monitor.info("!! [ EXIT ]");
		new Thread(terminator).start();
	}

	public String getClassInfo(String classname) throws ClassNotFoundException {
		Class cls = objectManager.findClass(classname);
		return StringUtil.conbine(JBUtils.getClassInfo(cls),",");
	}

	public void addClassLoader(Object proxyId) {
		Object obj = getObject(proxyId);
		if (obj instanceof ClassLoader) {
			objectManager.addClassLoader((ClassLoader)obj);
		} else {
			throw new RuntimeException("The object ["+proxyId.toString()+" => "+obj+"] is not a ClassLoader." );
		}
	}

	public void removeClassLoader(Object proxyId) {
		Object obj = getObject(proxyId);
		if (obj instanceof ClassLoader) {
			objectManager.removeClassLoader((ClassLoader)obj);
		} else {
			throw new RuntimeException("The object ["+proxyId.toString()+" => "+obj+"] is not a ClassLoader." );
		}
	}

	private Runnable terminator = new Runnable() {
			public void run() {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
				System.exit(0);
			}
		};

	public Object getStaticClass(String classname) throws ClassNotFoundException {
		return objectManager.staticReference(classname);
	}

	public void dumpObjects() {
		StringBuffer sb = new StringBuffer("---(Object Dump)------------\n");
		Object[] ret = getAllObjectKeys();
		for (int i=0; i<ret.length; i++) {
			sb.append("[").append(ret[i].toString()).append("] ");
			Object obj = objectManager.getObject(ret[i]);
			if (obj == null) {
				sb.append("null");
			} else {
				sb.append(obj.getClass().getName()).append(" | ");
				sb.append(obj.toString());
			}
			sb.append("\n");
		}
		sb.append("--------------------------------");
		monitor.info(sb.toString());
	}

	public Object[] getAllObjectKeys() {
		return objectManager.getAllObjectKeys();
	}

}
