package jbridge;

import java.util.HashMap;


public class ObjectRegistry {

	private int currentKeyId = 0;

	private HashMap objectTable = new HashMap(); // key -> obj
	private HashMap reverseTable = new HashMap();// obj -> key


	/** 
	 * register a given object and return the corresponding key.
	 * @param obj the object to register
	 * @return registration key
	 */
	public Object registerObject(Object obj) {
		synchronized(reverseTable) {
			ConsCell header = (ConsCell)reverseTable.get(obj);
			if (header != null) {
				ConsCell cons = header.searchCellByObject(obj);
				if (cons != null) {
					return cons.key();
				}
			} else {
				header = new ConsCell();
				reverseTable.put(obj,header);
			}
			Object key = "%%%JBOM:"+(currentKeyId++)+":"+obj.getClass().getName()+"%%%";
			header.append(new ConsCell(key,obj));
			objectTable.put(key,obj);
			return key;
		}
	}

	/**
	 * Remove the registered object indicated by given key.
	 *
	 * @param key an <code>Object</code> value
	 */
	public void removeObject(Object key) {
		synchronized(reverseTable) {
			Object obj = objectTable.remove(key);
			if (obj == null) return;
			ConsCell header = (ConsCell)reverseTable.get(obj);
			if (header != null) {
				header.removeByObject(obj);
				if (header.size() == 1) {
					reverseTable.remove(obj);
				}
			}
		}
	}

	/**
	 * @param obj is it the key to some object?
	 * @return if the given object is registered , this method returns true.
	 */
	public boolean isKey(Object key) {
		synchronized(reverseTable) {
			return objectTable.containsKey(key);
		}
	}

	/**
	 * get the registered object indicated by the given key.
	 *
	 * @param key key object
	 * @return the registered object. if the key is wrong, this method returns null.
	 */
	public Object getObject(Object key) {
		synchronized(reverseTable) {
			return objectTable.get(key);
		}
	}

	/**
	 * Describe <code>getAllObjectKeys</code> method here.
	 *
	 * @return an <code>Object[]</code> value
	 */
	public Object[] getAllObjectKeys() {
		synchronized(reverseTable) {
			return objectTable.keySet().toArray();
		}
	}

	public static void main(String[] args) {
		Object o1 = new Integer(1),o2 = new Integer(2),o3 = new Integer(3);
		String k1 = "k1",k2 = "k2",k3 = "k3";
		ConsCell header = new ConsCell();
		header.append( new ConsCell(k1,o1) );
		System.out.println(header +" : "+header.size());
		header.append( new ConsCell(k2,o2) );
		System.out.println(header +" : "+header.size());
		header.append( new ConsCell(k3,o3) );
		System.out.println(header+" : "+header.size());
		System.out.println("search k2: "+header.searchCellByObject(o2).car());
		header.removeByObject(o2);
		System.out.println(header +" : "+header.size());
		header.append( new ConsCell(k2,o2) );
		System.out.println(header +" : "+header.size());
		header.removeByObject(o2);
		System.out.println(header +" : "+header.size());
		header.removeByObject(o1);
		System.out.println(header +" : "+header.size());
		header.removeByObject(o3);
		System.out.println(header +" : "+header.size());
	}

}

class ConsCell { // for the search : object -> key 
	private Object key,car;
	private ConsCell cdr;
	ConsCell() {
		this.key = null;
		car = "ENTRY CELL";
	}
	ConsCell(Object key,Object obj) {
		this.key = key;
		car = obj;
	}
	Object car() {
		return car;
	}
	Object key() {
		return key;
	}
	private void setCdr(ConsCell target) {
		cdr = target;
	}
	void removeByObject(Object a) {
		if (cdr == null) return;
		if (cdr.car() == a) {
			if (cdr.cdr != null) {
				setCdr(cdr.cdr);
			} else {
				setCdr(null);
			}
		} else {
			cdr.removeByObject(a);
		}
	}
	ConsCell searchCellByObject(Object a) {
		if (a == car) {
			return this;
		} else if (cdr != null) {
			return cdr.searchCellByObject(a);
		} else {
			return null;
		}
	}
	void append(ConsCell cell) {
		if (cdr == null) {
			setCdr(cell);
		} else {
			cdr.append(cell);
		}
	}
	int size() {
		if (cdr == null) return 1;
		return 1+cdr.size();
	}

	void toString(StringBuffer sb) {
		if (key != null) {
			sb.append("(").append(key.toString()).append(", ").append(car.toString()).append(")");
		}
		if (cdr != null) cdr.toString(sb);
	}
	public String toString() {
		StringBuffer sb = new StringBuffer("(");
		toString(sb);
		sb.append(")");
		return sb.toString();
	}
}
