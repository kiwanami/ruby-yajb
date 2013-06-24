package jbridge;

public interface IObjectTransformer {

	/**
	   
	   @param obj object to export to out side.
	   @return a transformed object. If null, the object is registered as proxy object.
	*/
	public Object exportFilter(Object obj);

	/**
	   @param obj object to import to java environment
	*/
	public Object importFilter(Object obj);

}