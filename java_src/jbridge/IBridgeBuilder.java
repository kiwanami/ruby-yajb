package jbridge;

import inou.util.CommandLine;

public interface IBridgeBuilder {

	public void setConfig(CommandLine config) throws Exception;

	public IOverrideCall getOverrideCaller();

	public IObjectTransformer getObjectTransformer();

	public void start(BridgeServer bserver) throws Exception;

}
