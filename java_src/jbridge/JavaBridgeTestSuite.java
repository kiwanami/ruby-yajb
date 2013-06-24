package jbridge;

import junit.framework.TestSuite;
import junit.framework.Test;

public class JavaBridgeTestSuite {

	public static Test suite() {
		TestSuite suite= new TestSuite();
		suite.addTestSuite(ObjectManagerTest.class);
		suite.addTestSuite(BridgeServerTest.class);
		suite.addTestSuite(MethodFinderTest.class);
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}
