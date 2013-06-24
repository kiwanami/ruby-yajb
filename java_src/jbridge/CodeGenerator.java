package jbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import inou.util.StringUtil;
import org.apache.log4j.Logger;

/**
   Simple code repository and format service.
*/

public class CodeGenerator {

	private HashMap codeTable = new HashMap();
	private Map defaultMap;
	private Logger monitor = Logger.getLogger(this.getClass());

	CodeGenerator(Map dm) throws IOException {
		defaultMap = dm;
		load();
	}

	private Pattern header = Pattern.compile("^###+[^#](.*)$");
	private Pattern comment = Pattern.compile("^//.*$");

	private void load() throws IOException {
		InputStream is = getClass().getResourceAsStream("/jbridge/code.txt");
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		StringBuffer codeBuffer = null;
		String id = null;
		while(true) {
			String line = in.readLine();
			if (line == null) break;
			if (comment.matcher(line).find()) continue;
			//header
			Matcher m = header.matcher(line);
			if (m.find()) {
				if (codeBuffer != null) {
					if (id == null) {
						throw new RuntimeException("Can not found id.");
					}
					codeTable.put(id,codeBuffer.toString());
					monitor.debug("CodeGen: "+id);
				}
				id = m.group(1).trim();
				codeBuffer = new StringBuffer();
				continue;
			}
			if (codeBuffer != null) {
				codeBuffer.append(line).append("\n");
			}
		}
		codeTable.put(id,codeBuffer.toString());
		monitor.debug("CG: "+id);
		in.close();
	}

	public String getCode(String id) {
		return getCode(id,null);
	}

	public String getCode(String id,String key,String val) {
		HashMap map = new HashMap(1);
		map.put(key,val);
		return getCode(id,map);
	}

	public String getCode(String id,Map replaceHash) {
		String ret = (String)codeTable.get(id);
		if (ret == null) {
			throw new RuntimeException("BUG: Not found code: "+id);
		}
		ret = apply(ret,defaultMap);
		return apply(ret,replaceHash);
	}

	private String apply(String source,Map m) {
		if (m == null) return source;
		for(Iterator it = m.keySet().iterator();it.hasNext();) {
			String pre = (String)it.next();
			String post = (String)m.get(pre);
			source = StringUtil.replace(source,"%"+pre+"%", post);
		}
		return source;
	}

}
