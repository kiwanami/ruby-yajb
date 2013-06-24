package jbridge.comm.xmlrpc;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

import inou.net.RemoteRuntimeException;
import inou.net.Utils;
import inou.util.StringUtil;
import jbridge.IOverrideCall;
import jbridge.JBUtils;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;


public class XMLRPC_OverrideCall implements IOverrideCall {

	//private XmlRpcClient xmlrpc;

	private String host;
	private int port;

	public XMLRPC_OverrideCall(String host,int port)  {
		this.host = host;
		this.port = port;
	}

	public Object call(Object sid,Object objectId,String methodName,Object[] args,Class rt)  {
		Vector params = new Vector();
		params.addElement(sid);
		params.addElement(objectId);
		params.addElement(methodName);
		for(int i=0;i<args.length;i++) {
			params.addElement(args[i]);
		}
		try {
			XmlRpcClient xmlrpc = new XmlRpcClient ("http://"+host+":"+port+"/RPC2");
			Object obj = xmlrpc.execute("jb.call", params);
			if (obj != null) {
				if (obj instanceof String) {
					String retcode = (String)obj;
					if (retcode.equals(XMLRPC_JBServer.NULL_SYMBOL)) {
						obj = null;
					} else if (retcode.indexOf(XMLRPC_JBServer.EXCEPTION_SYMBOL)==0) {
						String[] ss = StringUtil.split(retcode,XMLRPC_JBServer.EXCEPTION_SEP);
						//0:symbol, 1:class, 2:message, 3:backtrace
						throw new RemoteRuntimeException(ss[1],ss[2],ss[3]);
					}
				} else if (obj != null && obj instanceof Number) {
					Object ret = JBUtils.convertObjectType(obj,rt);
					if (ret != null) {
						obj = ret;
					}
				}
			}
			return obj;
		} catch (XmlRpcException e) {
			throw new RuntimeException("Remote method calling:"+objectId.toString()+"."+methodName+StringUtil.conbine(args),e);
		} catch (IOException e) {
			throw new RuntimeException("Remote method calling:"+objectId.toString()+"."+methodName+StringUtil.conbine(args),e);
		}
	}
}
