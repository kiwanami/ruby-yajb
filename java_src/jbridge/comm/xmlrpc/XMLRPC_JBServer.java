package jbridge.comm.xmlrpc;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Vector;

import inou.net.Utils;
import inou.util.CommandLine;
import inou.util.StringUtil;
import jbridge.BridgeServer;
import jbridge.IBridgeBuilder;
import jbridge.IObjectTransformer;
import jbridge.IOverrideCall;
import jbridge.ISessionProcedure;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.XmlRpcServer;


/**
   connect XMLRPC <--> BridgeServer
*/
public class XMLRPC_JBServer implements IBridgeBuilder {

	public static final String NULL_SYMBOL = "$$$NULL$$$";
	public static final String LONG_SYMBOL = "$$$LONG$$$";
	public static final String TYPED_ARRAY_SYMBOL = "$$$TYPED_A$$$";
	public static final String EXCEPTION_SYMBOL = "$$$EXCEPTION$$$";
	public static final String EXCEPTION_SEP = "__$$$__";

	private static final String TA_INT1 =     "t_int1";
	private static final String TA_INT2 =     "t_int2";
	private static final String TA_INT4 =     "t_int4";
	private static final String TA_INT8 =     "t_int8";
	private static final String TA_FLOAT =    "t_float";
	private static final String TA_DOUBLE =   "t_double";
	private static final String TA_DECIMAL =  "t_decimal";
	private static final String TA_STRING =   "t_string";
	private static final String TA_BOOLEAN =  "t_boolean";

	private static final String TA_INT1J =    "t_byte";
	private static final String TA_INT2J =    "t_short";
	private static final String TA_INT4J =    "t_int";
	private static final String TA_INT8J =    "t_long";

	private BridgeServer bridgeServer;
	private WebServer webserver;
	private IOverrideCall overrideCaller;

	private Logger monitor = Logger.getLogger(this.getClass());

	public XMLRPC_JBServer() {
		XmlRpc.setEncoding("UTF-8");
		try {
			XmlRpc.setDriver("xerces");
		} catch (Exception e) {
			monitor.error(e);
		}
	}

	public void setConfig(CommandLine cli) throws IOException {
		int port = cli.getOptionInteger("javaport",9010);
		webserver = new WebServer(port);
		webserver.addHandler("jb",handler);

		String rhost = cli.getOptionString("remotehost","localhost");
		int rport = cli.getOptionInteger("remoteport",9009);
		overrideCaller = new XMLRPC_OverrideCall(rhost,rport);
	}

	public IObjectTransformer getObjectTransformer() {
		return transformer;
	}

	public IOverrideCall getOverrideCaller() {
		return overrideCaller;
	}

	public void start(BridgeServer bs) {
		this.bridgeServer = bs;
		webserver.start();
	}

	private static void printHelp() {
		System.out.println("=== XMLRPC BridgeBuilder ");
		System.out.println("     -remotehost:localhost (client hostname)\n");
		System.out.println("     -remoteport:9009 (client service port)\n");
		System.out.println("     -javaport:9010 (my service port)");
	}

	private static Object[] cdr(Vector v) {
		return cdr(v,1);
	}

	private static Object[] cdr(Vector v,int skip) {
		Object[] ret = new Object[v.size()-skip];
		for (int i=0;i<ret.length;i++) {
			ret[i] = v.elementAt(i+skip);
		}
		return ret;
	}

	private static String pid2str(Object pid) {
		return pid.toString();
	}

	private XmlRpcHandler handler = new XmlRpcHandler() {

			private String dumpParams(Vector params) {
				StringBuffer sb = new StringBuffer();
				for(int i=0;i<params.size();i++) {
					sb.append(params.elementAt(i)).append(" ");
				}
				return sb.toString();
			}

			public Object execute(String method,Vector params) throws Exception {
				if (method.startsWith("jb.")) {
					method = method.substring(3);
				}
				Utils.writeArguments(monitor,Level.DEBUG,"> "+method,params);
				try {
					Object ret = null;
					if ("call".equals(method)) {
						Object proxyId = params.elementAt(0);
						String methodName = (String)params.elementAt(1);
						ret = bridgeServer.call(proxyId,methodName,cdr(params,2));
					} else if ("new".equals(method)) {
						String fqcn = (String)params.elementAt(0);
						ret = pid2str(bridgeServer.jnew(fqcn,cdr(params)));
					} else if ("superCall".equals(method)) {
						Object proxyId = params.elementAt(0);
						String methodName = (String)params.elementAt(1);
						ret = bridgeServer.superCall(proxyId,methodName,cdr(params,2));
					} else if ("classname".equals(method)) {
						ret = bridgeServer.inspectClassname(params.elementAt(0));
					} else if ("static".equals(method)) {
						String fqcn = (String)params.elementAt(0);
						ret = bridgeServer.getStaticClass(fqcn);
					} else if ("ref".equals(method)) {
						Object proxyId = params.elementAt(0);
						String fieldName = (String)params.elementAt(1);
						ret = bridgeServer.ref(proxyId,fieldName);
					} else if ("set".equals(method)) {
						Object proxyId = params.elementAt(0);
						String fieldName = (String)params.elementAt(1);
						Object val = params.elementAt(2);
						bridgeServer.set(proxyId,fieldName,val);
					} else if ("sessionCall".equals(method)) {
						Object sid = params.elementAt(0);
						final String nestedMethod = (String)params.elementAt(1);
						final Vector args = new Vector();
						args.addAll(Arrays.asList(cdr(params,2)));
						ISessionProcedure sp = new ISessionProcedure() {
								public String getTitle() { return nestedMethod; }
								public Object exec() throws Exception {
									return execute(nestedMethod,args);
								}
							};
						ret = bridgeServer.sessionCall(sid,sp);
					} else if ("extend".equals(method)) {
						String fqcn = (String)params.elementAt(0);
						ret = pid2str(bridgeServer.jextend(fqcn,cdr(params)));
					} else if ("classinfo".equals(method)) {
						String fqcn = (String)params.elementAt(0);
						ret = bridgeServer.getClassInfo(fqcn);
					} else if ("impl".equals(method)) {
						Object proxyId = params.elementAt(0);
						String name = (String)params.elementAt(1);
						Boolean flag = (Boolean)params.elementAt(2);
						bridgeServer.setImplementFlag(proxyId,name,flag);
					} else if ("import".equals(method)) {
						String lines = (String)params.elementAt(0);
						bridgeServer.jimport(lines);
					} else if ("unlink".equals(method)) {
						Object proxyId = params.elementAt(0);
						bridgeServer.unlink(proxyId);
					} else if ("addClassLoader".equals(method)) {
						Object proxyId = params.elementAt(0);
						bridgeServer.addClassLoader(proxyId);
					} else if ("removeClassLoader".equals(method)) {
						Object proxyId = params.elementAt(0);
						bridgeServer.removeClassLoader(proxyId);
					} else if ("exit".equals(method)) {
						bridgeServer.exit();
						ret = "EXIT";
					} else if ("allObjects".equals(method)) {
						ret = bridgeServer.getAllObjectKeys();
					} else if ("dump".equals(method)) {
						bridgeServer.dumpObjects();
						ret = "DUMP";
					} else {
						throw new RuntimeException("Not supported method. "+method);
					}
					if (ret == null) {
						return NULL_SYMBOL;
					}
					return ret;
				} catch (Exception e) {
					Throwable t = e.getCause();
					if (t == null) {
						t = e;
					}
					monitor.debug("Exporting exception : "+t.getClass().getName());
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					sw.flush();
					String[] res = {
						EXCEPTION_SYMBOL,
						t.getClass().getName(),
						t.getMessage(), sw.toString()
					};
					return StringUtil.conbine(res,EXCEPTION_SEP);
				}
			}

		};

	private IObjectTransformer transformer = new IObjectTransformer() {

			public Object exportFilter(Object arg) {
				if (arg == null) return NULL_SYMBOL;
				Class c = arg.getClass();
				monitor.debug("ExportFilter:"+c.getName());
				if (c == Integer.TYPE || c == Double.TYPE || c == Boolean.TYPE || 
					arg instanceof String ||
					arg instanceof Integer || arg instanceof Double || 
					arg instanceof Boolean ) {
					return arg;
				} else if (c.isArray()) {
					String at = c.getName();
					switch (at.charAt(1)) {
					case 'I':
					case 'D':
					case 'Z':
					case 'B':
					case 'S':
						return to_int(arg);
					case 'F':
						return to_double(arg);
					case 'J':
						return to_long(arg);
					case 'L':
						if (at.indexOf("java.lang.String")>0) return arg;
						if (at.indexOf("java.lang.Integer")>0) return arg;
						if (at.indexOf("java.lang.Byte")>0 || 
							at.indexOf("java.lang.Short")>0 ) return to_int(arg);
						if (at.indexOf("java.lang.Float")>0) return to_double(arg);
						if (at.indexOf("java.lang.Long")>0) return to_long(arg);
					}
					//the arrays for the other type will be transformed by ObjectManager
					return null;
				} else if (arg instanceof Short || c == Short.TYPE) {
					return new Integer(((Short)arg).intValue());
				} else if (arg instanceof Byte || c == Byte.TYPE) {
					return new Integer(((Byte)arg).intValue());
				} else if (arg instanceof Float || c == Float.TYPE) {
					return new Double(((Float)arg).doubleValue());
				} else if (arg instanceof Long || c == Long.TYPE) {
					return LONG_SYMBOL+arg.toString();
				}
				return null;
			}

			private Object to_int(Object arg) {
				int sz = Array.getLength(arg);
				Integer[] ret = new Integer[sz];
				for(int i=0;i<sz;i++) {
					ret[i] = new Integer(((Number)Array.get(arg,i)).intValue());
				}
				return ret;
			}

			private Object to_double(Object arg) {
				int sz = Array.getLength(arg);
				Double[] ret = new Double[sz];
				for(int i=0;i<sz;i++) {
					ret[i] = new Double(((Number)Array.get(arg,i)).doubleValue());
				}
				return ret;
			}

			private Object to_long(Object arg) {
				int sz = Array.getLength(arg);
				String[] ret = new String[sz];
				for(int i=0;i<sz;i++) {
					ret[i] = LONG_SYMBOL+Array.get(arg,i).toString();
				}
				return ret;
			}

			public Object importFilter(Object obj) {
				if (obj instanceof String) {
					String a = (String)obj;
					if (obj.equals(NULL_SYMBOL)) {
						return null;
					} else if (a.startsWith(LONG_SYMBOL)) {
						return new Long( a.substring(LONG_SYMBOL.length()));
					}
					return a;
				} else if (obj instanceof Vector) {
					obj = vector2array((Vector)obj);
					if (obj instanceof String[]) {
						String[] array = (String[])obj;
						if (array.length == 0 || array[0] == null || 
							(array[0].indexOf(TYPED_ARRAY_SYMBOL) == -1)) {
							return obj;
						}
						return transTypedArray(array);
					}
				}
				return obj;
			}

			private Object[] vector2array(Vector v) {
				if (v.size() == 0) return new Object[0];
				Object f = v.get(0);
				Class c = f.getClass();
				if (c.equals(String.class) && 
					( ((String)f).indexOf(TYPED_ARRAY_SYMBOL) == 0) ) {
					return (String[])v.toArray(new String[v.size()]);
				}
				for(int i=1;i<v.size();i++) {
					Class s = v.get(i).getClass();
					if (s != c) {
						Utils.writeArray(monitor,Level.DEBUG,new Object[]{" Transformed array: ",c.getName(),", ",s.getName()," ..."});
						return v.toArray();
					}
				}
				monitor.debug(" Transformed array: "+c.getName());
				return v.toArray((Object[])Array.newInstance(c,v.size()));
			}

			private Object transTypedArray(String[] array) {
				String type = array[0].substring(TYPED_ARRAY_SYMBOL.length());
				monitor.debug(" Transformed typed array: "+type);
				if (type.equals(TA_INT1) || type.equals(TA_INT1J)) {
					byte[] ret = new byte[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Byte.parseByte(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_INT2) || type.equals(TA_INT2J)) {
					short[] ret = new short[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Short.parseShort(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_INT4) || type.equals(TA_INT4J)) {
					int[] ret = new int[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Integer.parseInt(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_INT8) || type.equals(TA_INT8J)) {
					long[] ret = new long[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Long.parseLong(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_FLOAT)) {
					float[] ret = new float[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Float.parseFloat(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_DOUBLE)) {
					double[] ret = new double[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = Double.parseDouble(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_DECIMAL)) {
					BigDecimal[] ret = new BigDecimal[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = new BigDecimal(array[i+1]);
					}
					return ret;
				} else if (type.equals(TA_STRING)) {
					String[] ret = new String[array.length-1];
					for(int i=0;i<ret.length;i++) {
						ret[i] = array[i+1];
					}
					return ret;
				} else if (type.equals(TA_BOOLEAN)) {
					boolean[] ret = new boolean[array.length-1];
					for(int i=0;i<ret.length;i++) {
						String a = array[i+1];
						ret[i] = (a != null || a.equalsIgnoreCase("true"));
					}
					return ret;
				} else {
					throw new RuntimeException("Wrong array type symbol:"+type);
				}
			}
		};
	
}
