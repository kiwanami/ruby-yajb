package jbridge;


import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import inou.net.Utils;
import javassist.CannotCompileException;
import javassist.ClassMap;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class ObjectManager {

	private static String FIELD_OBJECT_SUPERCLASS = "__super";
	private static String FIELD_OBJECT_ID = "__objectId";
	private static String FIELD_METHOD_FINDER = "__methodFinder";
	private static String FIELD_SESSION_MANAGER = "__sessionManager";
	static String FIELD_IMPL_FLAG_PREFIX = "__impl_";

	static final String METHOD_SEND_MESSAGE = "__sendMessage";

	static final String METHOD_ALIAS_PREFIX = "__org__";
	static final String METHOD_SUPERCLASS_METHOD = "__callSuperclassMethod";
	static final String METHOD_INJECT_FIELDS = "__injectFields";

	private Logger monitor = Logger.getLogger(this.getClass());
	private CodeGenerator codegen;
	private ClassFinder classFinder;
	private IObjectTransformer transformer;
	private MethodFinder2 methodFinder;

	private ClassPool pool = ClassPool.getDefault();
	private SessionManager sessionManager;
	private ObjectRegistry objectRegistry = new ObjectRegistry();

	private static int currentExtendId = 0;
	
	private static synchronized String makeUniqueClassname(String hint) {
		currentExtendId++;
		return "JBCustomClass"+currentExtendId;
	}

	public ObjectManager(SessionManager rb,IObjectTransformer ot) {
		this.sessionManager = rb;
		this.transformer = ot;
		this.classFinder = new ClassFinder();
		this.methodFinder = new MethodFinder2();

		HashMap map = new HashMap();
		map.put("f_super",FIELD_OBJECT_SUPERCLASS);
		map.put("f_id",FIELD_OBJECT_ID);
		map.put("f_mfinder",FIELD_METHOD_FINDER);
		map.put("f_session",FIELD_SESSION_MANAGER);
		map.put("f_impl_prefix",FIELD_IMPL_FLAG_PREFIX);
		map.put("m_inject",METHOD_INJECT_FIELDS);
		map.put("m_org_prefix",METHOD_ALIAS_PREFIX);
		map.put("m_send",METHOD_SEND_MESSAGE);
		map.put("m_super",METHOD_SUPERCLASS_METHOD);
		try {
			this.codegen = new CodeGenerator(map);
		} catch (Throwable e) {
			monitor.fatal("BUG: Can not read template code.",e);
			System.exit(1);
		}
	}

	/**
	 * create an instance and return the object key.
	 * The given class must not be abstract nor interface.
	 * 
	 * @param cn full qualified class name
	 * @param args argument array. if null is given, default constructor will be selected.
	 * @return the key to access the created instance 
	 * @exception ClassNotFoundException if an error occurs
	 * @exception InstantiationException if an error occurs
	 * @exception NoSuchMethodException if an error occurs
	 * @exception IllegalAccessException if an error occurs
	 * @exception InvocationTargetException if an error occurs
	 */
	public Object createObject(String cn,Object[] args) throws ClassNotFoundException,InstantiationException, NoSuchMethodException,IllegalAccessException, InvocationTargetException {
		Class cl = classFinder.findClass(cn);
		if (isAbstract(cl)) {
			throw new RuntimeException("Can not instantiate abstract class. : "+cn);
		}
		return getInstanceId(cl,args);
	}

	/**
	 * create a proxy implementation class.
	 *
	 * @param cns full qualified class names, separated by comma.
	 * @param args argument array. if null is given, default constructor will be selected.
	 * @return the key to access the created instance
	 * @exception ClassNotFoundException if an error occurs
	 * @exception InstantiationException if an error occurs
	 * @exception NoSuchMethodException if an error occurs
	 * @exception IllegalAccessException if an error occurs
	 * @exception InvocationTargetException if an error occurs
	 */
	public Object extendObject(String cns,Object[] args) throws ClassNotFoundException,InstantiationException, NoSuchMethodException,IllegalAccessException, InvocationTargetException {
		Object id = getInstanceId(buildClass(cns),args);
		Object obj = objectRegistry.getObject(id);
		injectParams(obj,id);
		return id;
	}

	/**
	 * return a static reference to the specified class object.
	 *
	 * @param cn class name
	 * @return class object
	 * @exception ClassNotFoundException if an error occurs
	 */
	public Object staticReference(String cn) throws ClassNotFoundException {
		Class cl = classFinder.findClass(cn);
		return objectRegistry.registerObject(cl);
	}

	/**
	 * add import class.
	 * the format of the import line is the same as to the java source.
	 * Ex:
	 * "java.awt.Color" : explicit import
	 * "java.awt.*" : using wildcard
	 * "java.awt.Component,java.awt.Panel" : comma deliminated
	 * "java.awt.List,java.util.List" : the later entry is given a priority
	 * 
	 * @param lines import class
	 */
	public void addImport(String lines) {
		classFinder.addImport(lines);
	}

	/**
	 * add a classloader object.
	 * the later classloader object is
	 *
	 * @param cl a <code>ClassLoader</code> value
	 */
	public void addClassLoader(ClassLoader cl) {
		classFinder.addClassLoader(cl);
	}

	/**
	 * remove the classloader object.
	 *
	 * @param cl a <code>ClassLoader</code> value
	 */
	public void removeClassLoader(ClassLoader cl) {
		classFinder.removeClassLoader(cl);
	}

	/**
	 * get the registered object indicated by the given key.
	 *
	 * @param key key object
	 * @return the registered object. if the key is wrong, this method returns null.
	 */
	public Object getObject(Object key) {
		return objectRegistry.getObject(key);
	}

	/**
	 * Remove the registered object indicated by given key.
	 *
	 * @param key an <code>Object</code> value
	 */
	public void removeObject(Object key) {
		objectRegistry.removeObject(key);
	}

	/**
	 * @param obj is it the key to some object?
	 * @return if the given object is registered , this method returns true.
	 */
	public boolean isKey(Object key) {
		return objectRegistry.isKey(key);
	}

	/**
	 * Describe <code>getAllObjectKeys</code> method here.
	 *
	 * @return an <code>Object[]</code> value
	 */
	public Object[] getAllObjectKeys() {
		return objectRegistry.getAllObjectKeys();
	}
	
	/**
	 * Describe <code>inspectClassnameByKey</code> method here.
	 *
	 * @param key an <code>Object</code> value
	 * @return a <code>String</code> value
	 */
	public String inspectClassnameByKey(Object key) {
		Object obj = objectRegistry.getObject(key);
		if (obj == null) return null;
		return inspectClassname(obj);
	}

	/**
	 * Describe <code>inspectClassname</code> method here.
	 *
	 * @param obj an <code>Object</code> value
	 * @return a <code>String</code> value
	 */
	public String inspectClassname(Object obj) {
		if (obj instanceof Class) {
			return ((Class)obj).getName();
		}
		return obj.getClass().getName();
	}

	private Object getInstanceId(Class cl,Object[] args) throws ClassNotFoundException,InstantiationException, NoSuchMethodException,IllegalAccessException, InvocationTargetException {
		Object obj = null;
		if (args == null || args.length == 0) {
			obj = cl.newInstance();
		} else {
			Constructor cnst = methodFinder.searchConstructur(cl,args);
			obj = cnst.newInstance(args);
		}
		Object id = objectRegistry.registerObject(obj);
		monitor.debug("OM::createObject  key:"+id+", class:"+cl.getName());
		return id;
	}

	private boolean isAbstract(Class cl) {
		return 
			( Modifier.isAbstract(cl.getModifiers()) ) ||
			( Modifier.isInterface(cl.getModifiers()) );
	}

	private void injectParams(Object obj,Object key) {
		try {
			Object[] args = new Object[]{key,sessionManager,methodFinder};
			Method m = methodFinder.searchPublicMethod(obj.getClass(),METHOD_INJECT_FIELDS,args);
			m.invoke(obj,args);
			monitor.debug("injected object ID ["+key+"] and SessionManager ["+sessionManager+"].");
		} catch (InvocationTargetException e) {
			throw new RuntimeException("BUG: Can not inject fields. ",e);
		} catch (IllegalAccessException e) {
			//do nothing
		} catch (NoSuchMethodException e) {
			//do nothing
		}
	}

	private Class buildClass(String _cns) throws ClassNotFoundException {
		CtClass cc = makeClass(_cns);
		ClassMap classmap = null;
		try {
			addConstructor(cc,classmap);
			addCustomFields(cc);
			addImplFlags(cc);
			addSendMessageMethod(cc);
			addSuperClassMethodCaller(cc);
			addAliasMethods(cc,classmap);
			addOverrideMethods(cc,classmap);
			return cc.toClass(classFinder.getCustomClassLoader());
		} catch (CannotCompileException e) {
			monitor.fatal("BUG.",e);
			System.exit(0);
		}
		return null;
	}

	private static HashMap makeMap(String k1,String v1) {
		return makeMap(new String[]{k1,v1});
	}

	private static HashMap makeMap(String[] source) {
		int i=0;
		HashMap map = new HashMap();
		while(i < source.length) {
			map.put(source[i++],source[i++]);
		}
		return map;
	}

	private void addConstructor(CtClass cc,ClassMap classmap) throws CannotCompileException {
		cc.addConstructor(CtNewConstructor.defaultConstructor(cc));
		CtClass superclass = null;
		try {
			superclass = cc.getSuperclass();
			if (superclass == null) {
				return;
			}
		} catch (NotFoundException e) {
			monitor.error(e);
			return;
		}
		CtConstructor[] inits = superclass.getConstructors();
		monitor.debug("===add constructor: "+inits.length);
		for(int i=0;i<inits.length;i++) {
			CtConstructor copy = null;
			try {
				copy = CtNewConstructor.make(getParamList(inits[i],cc),
											 inits[i].getExceptionTypes(),
											 cc);
				copy.setBody(codegen.getCode("SuperConstructor"));
				cc.addConstructor(copy);
				monitor.debug("  Add:Constructor:"+copy.toString());
			} catch (NotFoundException e) {
				throw new RuntimeException(e);
			} catch (CannotCompileException e) {
				if (e.getMessage() != null && 
					(e.getMessage().indexOf("duplicate method:") >= 0)) {
					monitor.debug("    duplicate constructor : "+copy);
					continue;
				}
				throw e;
			}
		}
	}

	private CtClass[] getParamList(CtBehavior method,CtClass subclass) {
		try {
			CtClass[] list = method.getParameterTypes();
			CtClass superclass = subclass.getSuperclass();
			if (superclass == null) {
				return list;
			}
			return replaceParamList(list,subclass,superclass);
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private CtClass[] replaceParamList(CtClass[] list,CtClass oldc,CtClass newc) {
		CtClass[] ret = new CtClass[list.length];
		for(int i=0;i<ret.length;i++) {
			ret[i] = (list[i].equals(oldc)) ? newc : list[i];
		}
		return ret;
	}

	private CtClass getReturnType(CtMethod method,CtClass subclass) {
		try {
			CtClass ret = method.getReturnType();
			CtClass superclass = subclass.getSuperclass();
			if (superclass == null) {
				return ret;
			}
			return replaceParamList(new CtClass[]{ret},subclass,superclass)[0];
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void addOverrideMethods(CtClass cc,ClassMap classmap) throws CannotCompileException {
		monitor.debug("===add override method");
		CtMethod[] ms = getOverrideMethod(cc);
		for(int i=0;i<ms.length;i++) {
			if (!canBeOverrided(ms[i])) continue;
			CtMethod method = null;
			try {
				method = CtNewMethod.make(ms[i].getModifiers(),
										  getReturnType(ms[i],cc),
										  ms[i].getName(),
										  getParamList(ms[i],cc),
										  ms[i].getExceptionTypes(),
										  null,cc);
				Map map = makeMap("name",method.getName());
				String checkCode = "";
				if (!Modifier.isAbstract(ms[i].getModifiers())) {
					checkCode = codegen.getCode("OverrideMethod_check",null);
				}
				map.put("check",checkCode);
				method.setBody(codegen.getCode("OverrideMethod_return",map));
				method.setModifiers(method.getModifiers() & ~Modifier.ABSTRACT);
				cc.addMethod(method);
				monitor.debug("  Add:Override:"+method.toString());
				method = null;
			} catch (NotFoundException e) {
				throw new RuntimeException(e);
			} catch (CannotCompileException e) {
				if (e.getMessage() != null && 
					(e.getMessage().indexOf("duplicate method:") >= 0)) {
					monitor.debug("   duplicate method : "+method);
					continue;
				}
				throw e;
			}
		}
	}

	private boolean canBeOverrided(CtMethod m) {
		if ("finalize".equals(m.getName())) return false;
		int a = m.getModifiers();
		return !((a & (Modifier.FINAL | Modifier.PRIVATE | 
					   Modifier.STATIC | Modifier.NATIVE)) > 0);
	}

	private void addAliasMethods(CtClass cc,ClassMap classmap) throws CannotCompileException {
		monitor.debug("===add alias method");
		CtMethod[] ms = getOverrideMethod(cc);
		for(int i=0;i<ms.length;i++) {
			if (!doesGenAliasMethod(ms[i])) continue;
			CtMethod method = null;
			try {
				Map map = makeMap("name",ms[i].getName());
				method = CtNewMethod.make(ms[i].getModifiers(),
										  getReturnType(ms[i],cc),
										  METHOD_ALIAS_PREFIX+ms[i].getName(),
										  getParamList(ms[i],cc),
										  ms[i].getExceptionTypes(),
										  codegen.getCode("OriginalMethod",map),cc);
				cc.addMethod(method);
				monitor.debug("  Add:Alias:"+method.toString());
				method = null;
			} catch (NotFoundException e) {
				throw new RuntimeException(e);
			} catch (CannotCompileException e) {
				monitor.error(e.getClass().getName()+" : "+e.getMessage());
				if (method != null) {
					monitor.error("  Problem:"+method.toString());
				}
			}
		}
	}

	private boolean doesGenAliasMethod(CtMethod m) {
		if ("finalize".equals(m.getName())) return false;
		int a = m.getModifiers();
		return !((a & (Modifier.ABSTRACT | Modifier.PRIVATE | 
					   Modifier.STATIC | Modifier.NATIVE)) > 0);
		// Alias methods decorated by the final modifier should be 
		// generated, because of the algorithm of MethodFiner#searchAllMethod.
	}

	private void addImplFlags(CtClass cc) throws CannotCompileException {
		monitor.debug("===add implementation flag");
		List list = new ArrayList();
		CtMethod[] ms = getOverrideMethod(cc);
		for(int i=0;i<ms.length;i++) {
			if (!doesGenAliasMethod(ms[i])) continue;
			String name = ms[i].getName();
			if (list.contains(name)) {
				monitor.debug("    duplicate method: "+name);
				continue;
			}
			list.add(name);
			CtField f = CtField.make(codegen.getCode("ImplFlagField","name",name),cc);
			cc.addField(f);
			monitor.debug("  Add:Flag:"+f.toString());
		}
	}

	private CtMethod[] getOverrideMethod(CtClass cc) {
		List list = new ArrayList();
		collectMethods(cc.getMethods(),list);
		while(true) {
			try {
				cc = cc.getSuperclass();
				if (cc == null) break;
				collectMethods(cc.getDeclaredMethods(),list);
			} catch (NotFoundException e) {
				monitor.error(e);
				break;
			}
		}
		return (CtMethod[])list.toArray(new CtMethod[list.size()]);
	}

	private void collectMethods(CtMethod[] ms,List list) {
		for(int i=0;i<ms.length;i++) {
			String name = ms[i].getName();
			if (Modifier.isPrivate(ms[i].getModifiers()) ||
				METHOD_SEND_MESSAGE.equals(name) || 
				METHOD_SUPERCLASS_METHOD.equals(name) ||
				METHOD_INJECT_FIELDS.equals(name) ||
				name.indexOf(METHOD_ALIAS_PREFIX) == 0) {
				continue;
			}
			if (!list.contains(ms[i])) {
				list.add(ms[i]);
			}
		}
	}

	private void addCustomFields(CtClass cc) throws CannotCompileException {
		try {
			CtClass sc = cc.getSuperclass();
			if (sc != null) {
				CtField fsp = CtField.make(codegen.getCode("SuperClassField","name",sc.getName()),cc);
				cc.addField(fsp);
				monitor.debug("  Add:Field:"+fsp.toString());
			}
			CtField frb = CtField.make(codegen.getCode("SessionManagerField"),cc);
			cc.addField(frb);
			monitor.debug("  Add:Field:"+frb.toString());
			CtField fid = CtField.make(codegen.getCode("IdField"),cc);
			cc.addField(fid);
			monitor.debug("  Add:Field:"+fid.toString());
			CtField fmf = CtField.make(codegen.getCode("MethodFinderField"),cc);
			cc.addField(fmf);
			monitor.debug("  Add:Field:"+fmf.toString());
			CtMethod im = CtNewMethod.make(codegen.getCode("InjectMethod"),cc);
			cc.addMethod(im);
			monitor.debug("  Add:Inject:"+im.toString());
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void addSendMessageMethod(CtClass cc) throws CannotCompileException {
		CtMethod gm = CtNewMethod.make(codegen.getCode("SendMessage"),cc);
		cc.addMethod(gm);
		monitor.debug("  Add:Send:"+gm.toString());
	}

	/**
	   Note that the proxy objects of arguments should be translated by BridgeServer.
	*/
	private void addSuperClassMethodCaller(CtClass cc) throws CannotCompileException{
		CtMethod gm = CtNewMethod.make(codegen.getCode("SuperCaller"),cc);
		cc.addMethod(gm);
		monitor.debug("  Add:SuperCaller:"+gm.toString());
	}

	/**
	   Just make a new class from the given class and interfaces.
	   This method is called by buildClass.
	*/
	private CtClass makeClass(String _cns) throws ClassNotFoundException {
		monitor.debug("Generating derived class: "+_cns);
		String[] cns = _cns.split(",");
		CtClass superClass = null;
		List interfaces = new LinkedList();
		for(int i=0;i<cns.length;i++) {
			try {
				CtClass cls = pool.get(classFinder.fqcn(cns[i]));
				if (cls.isInterface()) {
					interfaces.add(cls);
				} else if (superClass != null) {
					throw new RuntimeException("Given another super class : "+superClass.getName()+" and "+cls.getName());
				} else {
					superClass = cls;
				}
			} catch (NotFoundException e) {
				monitor.error(e);
				throw new ClassNotFoundException(e.getMessage());
			}
		}
		CtClass cc = null;
		if (superClass != null) {
			cc = pool.makeClass(makeUniqueClassname(superClass.getName()),superClass);
			for(Iterator it = interfaces.iterator();it.hasNext();) {
				cc.addInterface((CtClass)it.next());
			}
		} else {
			for(Iterator it = interfaces.iterator();it.hasNext();) {
				CtClass c = (CtClass)it.next();
				if (cc == null) {
					cc = pool.makeClass(makeUniqueClassname(c.getName()));
				}
				cc.addInterface(c);
			}
		}
		return cc;
	}

	public Object[] obj2ids(Object[] args) {
		Object[] ret = new Object[args.length];
		for(int i=0;i<ret.length;i++) {
			ret[i] = obj2id(args[i]);
		}
		return ret;
	}

	public Object obj2id(Object arg) {
		Object targ = transformer.exportFilter(arg);
		if (targ != null) {
			return targ;
		} else if (arg == null) {
			return null;
		} else {
			if (arg.getClass().isArray()) {
				return obj2id_array(arg);
			} else {
				return objectRegistry.registerObject(arg);
			}
		}
	}

	private Object obj2id_array(Object arg) {
		int sz = Array.getLength(arg);
		Object[] ret = new Object[sz];
		for(int i=0;i<sz;i++) {
			ret[i] = obj2id(Array.get(arg,i));
		}
		return ret;
	}

	public Object[] id2objs(Object[] args) {
		if (args == null) return new Object[0];
		if (args.length == 0) return args;
		for(int i=0;i<args.length;i++) {
			args[i] = id2obj(args[i]);
		}
		return args;
	}

	public Object id2obj(Object arg) {
		if (arg == null) return null;
		if (objectRegistry.isKey(arg)) {
			String pre = arg.getClass().getName();
			arg = objectRegistry.getObject(arg);
			Utils.writeArray(monitor,Level.DEBUG,new Object[]{"  ARG TR: ",pre," -> ",arg.getClass().getName()});
			return arg;
		}
		return transformer.importFilter(arg);
	}

	public Class findClass(String cn) throws ClassNotFoundException {
		return classFinder.findClass(cn);
	}

	public Method searchPublicMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		return methodFinder.searchPublicMethod(cls,name,args);
	}

	public Method searchAllMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		return methodFinder.searchAllMethod(cls,name,args);
	}

	public Method searchStaticMethod(Class cls,String name,Object[] args) throws NoSuchMethodException {
		return methodFinder.searchStaticMethod(cls,name,args);
	}

	public ClassPool getClassPool() {
		return pool;
	}
}
