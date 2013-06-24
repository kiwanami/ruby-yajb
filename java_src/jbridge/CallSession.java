package jbridge;

import inou.net.Utils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


public class CallSession {

	private Logger monitor = Logger.getLogger(this.getClass());

	private Object sid;
	private Object proxyId;
	private String method;
	private Object[] args;
	private Class returnType;

	private Object returnObject;
	private Exception exception;

	private ISessionProcedure call_procedure = null;

	private Object call_returnObject = null;
	private Exception call_exception = null;

	private Object lock = new Object();
	private String TAB = "    ";

	CallSession(Object sid,Object proxyId,String method,Object[] args,Class returnType) {
		this.sid = sid;
		this.proxyId = proxyId;
		this.method = method;
		this.args = args;
		this.returnType = returnType;
		TAB = "    |"+sid+"|";
	}

	Object overrideCall(IOverrideCall _rcall) throws Exception {
		Utils.writeArguments(monitor,Level.INFO,TAB+"===== New Session "+method,args);
		final IOverrideCall rcall = _rcall;
		Thread thread = new Thread(new Runnable() {
				public void run() {
					monitor.debug(TAB+"TOP.Start:"+method);
					try {
						setResultValue(rcall.call(sid,proxyId,method,args,returnType));
					} catch (Exception e) {
						setException(e);
					} finally {
						monitor.debug(TAB+"TOP.Terminate");
					}
				}
			});
		thread.start();
		while (returnObject == null && exception == null) {
			synchronized (lock) {
				try {
					monitor.debug(TAB+"SESSION.Stop");
					lock.wait();
				} catch (InterruptedException e) {
					monitor.error(e);
					break;
				}
			}
			monitor.debug(TAB+"SESSION.Wakeup");
			if (call_procedure != null) {
				monitor.debug(TAB+"SESSION.ExecProc");
				try {
					call_returnObject = call_procedure.exec();
					if (call_returnObject == null) {
						call_returnObject = NULL;
					}
				} catch (Exception e) {
					call_exception = e;
				}
				synchronized (lock) {
					lock.notifyAll();
				}
			}
		}
		if (exception != null) {
			monitor.info(TAB+"SESSION.Exception:"+exception);
			monitor.info(TAB+"===== End Session :"+method);
			throw exception;
		} else {
			monitor.info(TAB+"SESSION.Finished:"+returnObject);
			monitor.info(TAB+"===== End Session :"+method);
		}
		if (returnObject ==  NULL) {
			return null;
		}
		return returnObject;
	}

	private void setResultValue(Object obj) {
		monitor.debug(TAB+"TOP.Finished:"+returnObject);
		if (obj == null) {
			obj = NULL;
		}
		returnObject = obj;
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	private void setException(Exception e) {
		monitor.debug(TAB+"TOP.Exception:"+e);
		exception = e;
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	public Object sessionCall(ISessionProcedure p) throws Exception {
		monitor.debug(TAB+"Call.Start:"+p.getTitle());
		call_procedure = p;
		synchronized (lock) {
			lock.notifyAll();
		}
		while(call_returnObject == null && call_exception == null) {
			synchronized (lock) {
				monitor.debug(TAB+"Call.Stop");
				lock.wait();
			}
			monitor.debug(TAB+"Call.Wakeup");
		}
		try {
			if (call_exception != null) {
				monitor.info(TAB+"Call.Exception:"+call_exception);
				throw call_exception;
			} else {
				monitor.debug(TAB+"Call.Finished:"+call_returnObject);
			}
			if (call_returnObject == NULL) {
				return null;
			}
			return call_returnObject;
		} finally {
			call_procedure = null;
			call_returnObject = null;
			call_exception = null;
		}
	}

	private static final Object NULL = new Object();
}
