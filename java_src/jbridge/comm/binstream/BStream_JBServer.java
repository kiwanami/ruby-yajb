package jbridge.comm.binstream;


import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import inou.net.rpc.BinServer;
import inou.net.rpc.ICommunicator;
import inou.net.rpc.IMessageHandler;
import inou.util.CommandLine;
import jbridge.BridgeServer;
import jbridge.IBridgeBuilder;
import jbridge.IObjectTransformer;
import jbridge.IOverrideCall;
import jbridge.ISessionProcedure;
import org.apache.log4j.Logger;


public class BStream_JBServer implements IBridgeBuilder {

	private Logger monitor = Logger.getLogger(this.getClass());
	private BridgeServer bridgeServer;
	private ICommunicator bserver;
	private IOverrideCall overrideCaller;
	private HashMap handlerMap;

	public BStream_JBServer() {
	}
	
	public void setConfig(CommandLine cli) throws IOException {
		int port = cli.getOptionInteger("remoteport",-1);
		if (port == -1) {
			throw new RuntimeException("Port number is not specified.");
		}
		bserver = new BinServer(port);
		overrideCaller = new BStream_OverrideCall(bserver);
		registHandlers();
	}

	public IObjectTransformer getObjectTransformer() {
		return transformer;
	}

	public IOverrideCall getOverrideCaller() {
		return overrideCaller;
	}

	public void start(BridgeServer bs) {
		this.bridgeServer = bs;
		bserver.start();
	}

	private static void printHelp() {
		System.out.println("=== BinStream BridgeBuilder ");
		System.out.println("     -remotehost:localhost (client hostname)\n");
		System.out.println("     -remoteport:9009 (client service port)\n");
	}

	private IObjectTransformer transformer = new IObjectTransformer() {
			public Object exportFilter(Object arg) {
				if (arg == null) return null;
				if (arg.getClass().isPrimitive() || arg instanceof String || 
					arg instanceof Integer || arg instanceof Double ||
					arg instanceof Byte || arg instanceof Float ||
					arg instanceof Long || arg instanceof Short ||
					arg instanceof Boolean) {
					return arg;
				}
				return null;
			}

			public Object importFilter(Object obj) {
				return obj;
			}
		};

	private void registHandlers() {
		handlerMap = new HashMap();
		handlerMap.put("call",h_call);
		handlerMap.put("new",h_new);
		handlerMap.put("superCall",h_superCall);
		handlerMap.put("classname",h_classname);
		handlerMap.put("static",h_static);
		handlerMap.put("ref",h_ref);
		handlerMap.put("set",h_set);
		handlerMap.put("sessionCall",h_sessionCall);
		handlerMap.put("extend",h_extend);
		handlerMap.put("classinfo",h_classinfo);
		handlerMap.put("impl",h_impl);
		handlerMap.put("import",h_import);
		handlerMap.put("unlink",h_unlink);
		handlerMap.put("addClassLoader",h_addClassLoader);
		handlerMap.put("removeClassLoader",h_removeClassLoader);
		handlerMap.put("exit",h_exit);
		handlerMap.put("allObjects",h_allObjects);
		handlerMap.put("dump",h_dump);

		for(Iterator it = handlerMap.keySet().iterator();it.hasNext();) {
			String k = (String)it.next();
			IMessageHandler h = (IMessageHandler)handlerMap.get(k);
			bserver.addHandler(k,h);
		}
	}

	private Object dispach(String method,Object[] args) throws Exception {
		IMessageHandler h = (IMessageHandler)handlerMap.get(method);
		if (h != null) {
			return h.send(args);
		} else {
			throw new IOException("No such remote method: "+method);
		}
	}

	private void checkArgNumbers(int num,Object[] args) throws IOException {
		if (num == 0 && args == null) return;
		if (args.length == num) return;
		throw new IOException("Wrong argument number: expected="+num+"  but received="+args.length);
	}

	private void checkArgNumbersPlus(int num,Object[] args) throws IOException {
		if (num == 0 && args == null) return;
		if (args.length >= num) return;
		throw new IOException("Wrong argument number: expected="+num+"  but received="+args.length);
	}
	
	private static Object[] cdr(Object[] v) {
		return cdr(v,1);
	}

	private static Object[] cdr(Object[] v,int skip) {
		Object[] ret = new Object[v.length-skip];
		for (int i=0;i<ret.length;i++) {
			ret[i] = v[i+skip];
		}
		return ret;
	}

	private IMessageHandler h_call = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbersPlus(2,args);
				Object proxyId = args[0];
				String methodName = (String)args[1];
				return bridgeServer.call(proxyId,methodName,cdr(args,2));
			}
		};

	private IMessageHandler h_new = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbersPlus(1,args);
				String fqcn = (String)args[0];
				return bridgeServer.jnew(fqcn,cdr(args));
			}
		};

	private IMessageHandler h_superCall = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbersPlus(2,args);
				Object proxyId = args[0];
				String methodName = (String)args[1];
				return bridgeServer.superCall(proxyId,methodName,cdr(args,2));
			}
		};


	private IMessageHandler h_classname = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				return bridgeServer.inspectClassname(args[0]);
			}
		};

	
	private IMessageHandler h_static = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				String fqcn = (String)args[0];
				return bridgeServer.getStaticClass(fqcn);
			}
		};

	private IMessageHandler h_ref = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(2,args);
				Object proxyId = args[0];
				String fieldName = (String)args[1];
				return bridgeServer.ref(proxyId,fieldName);
			}
		};

	private IMessageHandler h_set = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(3,args);
				Object proxyId = args[0];
				String fieldName = (String)args[1];
				Object val = args[2];
				bridgeServer.set(proxyId,fieldName,val);
				return null;
			}
		};

	private IMessageHandler h_sessionCall = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbersPlus(2,args);
				Object sid = args[0];
				final String nestedMethod = (String)args[1];
				final Object[] targs = cdr(args,2);
				ISessionProcedure sp = new ISessionProcedure() {
						public String getTitle() { return nestedMethod; }
						public Object exec() throws Exception {
							return dispach(nestedMethod,targs);
						}
					};
				return bridgeServer.sessionCall(sid,sp);
			}
		};

	private IMessageHandler h_extend = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbersPlus(1,args);
				String fqcn = (String)args[0];
				return bridgeServer.jextend(fqcn,cdr(args));
			}
		};

	private IMessageHandler h_classinfo = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				String fqcn = (String)args[0];
				return bridgeServer.getClassInfo(fqcn);
			}
		};

	private IMessageHandler h_impl = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(3,args);
				Object proxyId = args[0];
				String name = (String)args[1];
				Boolean flag = (Boolean)args[2];
				bridgeServer.setImplementFlag(proxyId,name,flag);
				return null;
			}
		};

	private IMessageHandler h_import = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				String lines = (String)args[0];
				bridgeServer.jimport(lines);
				return null;
			}
		};

	private IMessageHandler h_unlink = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				Object proxyId = args[0];
				bridgeServer.unlink(proxyId);
				return null;
			}
		};

	private IMessageHandler h_addClassLoader = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				Object proxyId = args[0];
				bridgeServer.addClassLoader(proxyId);
				return null;
			}
		};

	private IMessageHandler h_removeClassLoader = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(1,args);
				Object proxyId = args[0];
				bridgeServer.removeClassLoader(proxyId);
				return null;
			}
		};

	private IMessageHandler h_exit = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				//checkArgNumbers(0,args);
				bridgeServer.exit();
				return "EXIT";
			}
		};

	private IMessageHandler h_allObjects = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				return bridgeServer.getAllObjectKeys();
			}
		};

	private IMessageHandler h_dump = new IMessageHandler() {
			public Object send(Object[] args) throws Exception {
				checkArgNumbers(0,args);
				bridgeServer.dumpObjects();
				return "DUMP";
			}
		};

}
