package main.java.jena;

import java.util.Iterator;
import java.util.LinkedList;

public class SPARQLQueryBuilder {

	LinkedList<String> select;
	LinkedList<String> where;
	StringBuilder sparqlQueryWhere;
	
	public SPARQLQueryBuilder() {
		select = new LinkedList<String>();
		where = new LinkedList<String>();
	}
	
	public void addToSelect(String x) {
		select.addLast(x);
	}
	
	public void addTripleToWhere(String x, String y, String z) {
		where.addLast(x);
		where.addLast(y);
		where.addLast(z);
	}
	
	public void addFilterToWhere(String x, String operator, String value, String valuetype) {
		where.addLast("FILTER");
		where.addLast("("+x+" "+operator+" \""+value+"\"^^xsd:"+valuetype+")");
		where.addLast(" ");
	}
	
	public void append(SPARQLQueryBuilder sqb) {
		this.select.addAll(sqb.select);
		this.where.addAll(sqb.where);
	}
	public String getQuery() {
		if(!select.isEmpty() && !where.isEmpty()){
			StringBuffer sb = new StringBuffer();
			sb.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
				"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
				"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
				"PREFIX ns: <http://www.semanticweb.org/SWARMs/ontology/>\n");
			sb.append("SELECT ");
			Iterator<String> it = select.iterator();
			while(it.hasNext()){
				sb.append(it.next()+" ");
			}
			sb.deleteCharAt(sb.length()-1);
			sb.append("\nWHERE {\n");
			it = where.iterator();
			while(it.hasNext()){
				sb.append(it.next()+" "+it.next()+" "+it.next()+" . \n");
			}
			sb.delete(sb.length()-4, sb.length()-1);
			sb.append("}");
			return sb.toString();
		}else return null;
	}

}
