package jbridge.comm.binstream;

import inou.net.Utils;
import inou.net.rpc.ICommunicator;
import jbridge.BridgeServer;
import jbridge.IBridgeBuilder;
import jbridge.IObjectTransformer;
import jbridge.IOverrideCall;
import jbridge.JBUtils;


public class BStream_OverrideCall implements IOverrideCall {

	private ICommunicator comm;

	public BStream_OverrideCall(ICommunicator c) {
		comm = c;
	}

	public Object call(Object sid,Object objectId,String methodName,Object[] args,Class rt) {
		try {
			Object obj = comm.send("call", new Object[]{sid,objectId,methodName,args});
			if (obj != null && obj instanceof Number) {
				Object ret = JBUtils.convertObjectType(obj,rt);
				if (ret != null) {
					obj = ret;
				}
			}
			return obj;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	

}
