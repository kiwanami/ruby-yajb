package jbridge;


public interface ISessionProcedure {

	public String getTitle();

	public Object exec() throws Exception;

}