package jbridge;

import inou.util.CacheHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import org.apache.log4j.Logger;
import java.util.Iterator;

public class ClassFinder {

	private CacheHashMap cacheTable = new CacheHashMap(200);
	private HashMap primitiveTable = new HashMap();
	private ArrayList importList = new ArrayList();
	private Logger monitor = Logger.getLogger(this.getClass());

	public ClassFinder() {
		importList.add("java.lang.");
		cacheTable.setReplace(true);
		primitiveTable.put("byte",   Byte.TYPE);
		primitiveTable.put("short",  Short.TYPE);
		primitiveTable.put("int",    Integer.TYPE);
		primitiveTable.put("long",   Long.TYPE);
		primitiveTable.put("float",  Float.TYPE);
		primitiveTable.put("double", Double.TYPE);
		primitiveTable.put("boolean",Boolean.TYPE);
	}

	public void addImport(String line) {
		String[] lines = line.split(",");
		for(int i=0;i<lines.length;i++) {
			String ai = lines[i];
			if (!importList.contains(ai)) {
				if (ai.endsWith("*")) {
					ai = ai.substring(0,ai.length()-1);
				}
				importList.add(0,ai);
				monitor.debug("ClassFinder: import : "+ai);
			}
		}
		cacheTable.clear();
	}

	public Class findClass(String cn) throws ClassNotFoundException {
		if (cn.length() < 8) {
			Class c = (Class)primitiveTable.get(cn);
			if (c != null) return c;
		}
		Class ret = getClass(cn);
		if (ret != null) return ret;
		
		ret = (Class)cacheTable.get(cn);
		if (ret != null) return ret;

		for(int i=0;i<importList.size();i++) {
			String prefix = (String)importList.get(i);
			if (prefix.endsWith(".")) {
				String fqcn = prefix+cn;
				monitor.debug("ClassFinder: try : "+fqcn);
				ret = getClass(fqcn);
			} else if (prefix.endsWith(cn)) {
				ret = getClass(prefix);
			} else {
				continue;
			}
			if (ret != null) {
				cacheTable.put(cn,ret);
				return ret;
			}
		}
		throw new ClassNotFoundException("Not found class: "+cn);
	}

	public String fqcn(String cn) throws ClassNotFoundException {
		return findClass(cn).getName();
	}

	private Class getClass(String fqcn) {
		try {
			return classLoader.loadClass(fqcn);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	//========================================
	
	private HashMap customClassTable = new HashMap();
	private LinkedList classLoaderList = new LinkedList();

	public void addClassLoader(ClassLoader cl) {
		if (!classLoaderList.contains(cl)) {
			classLoaderList.addFirst(cl);
		}
	}

	public void removeClassLoader(ClassLoader cl) {
		classLoaderList.remove(cl);
	}

	/**
	   This method is employed to create a new class by javassist in 
	   ObjectManager#buildClass.
	*/
	ClassLoader getCustomClassLoader() {
		return classLoader;
	}

	private ClassLoader classLoader = new ClassLoader() {
			protected Class findClass(String name) throws ClassNotFoundException {
				Object obj = customClassTable.get(name);
				if (obj != null) {
					return (Class)obj;
				}
				Class cl = buildCustomClass(name);
				if (cl == null) {
					throw new ClassNotFoundException(name);
				} else {
					customClassTable.put(name,cl);
					if (!name.equals(cl.getName())) {
						customClassTable.put(cl.getName(),cl);
					}
				}
				resolveClass(cl);
				return cl;
			}

			private Class buildCustomClass(String name) throws ClassNotFoundException {
				ClassNotFoundException ex = null;
				for(Iterator it = classLoaderList.iterator();it.hasNext();) {
					ClassLoader cl = (ClassLoader)it.next();
					try {
						Class c = cl.loadClass(name);
						if (c != null) {
							return c;
						}
					} catch (ClassNotFoundException e) {
						ex = e;
					}
				}
				if (ex != null) {
					throw ex;
				}
				return null;
			}
		};

}
