/**
 * 
 */
package main.java.jena;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.mindswap.pellet.jena.PelletReasonerFactory;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.util.FileManager;

/**
 * @author Pablo Murillo
 *
 */
public class SPARQLQuery {

	public static String ontNamespace = "http://www.semanticweb.org/SWARMs/ontology/";
	public static String ontologyFile = "src/main/resources/Ontology/SWARMsontologyMerged_v1_rdf.owl";

	/**
	 * 
	 */
	public SPARQLQuery() {
		// TODO Auto-generated constructor stub
	}
	
	public static List<QuerySolution> executeArgQuery(String querystring){
		InputStream is = null;
		Model model = ModelFactory.createOntologyModel(PelletReasonerFactory.THE_SPEC);
		try{
			is = FileManager.get().open(ontologyFile);
			if(is!=null){
				model.read(is,ontNamespace);
				is.close();
			}
		}catch(Exception e){
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		Query query = QueryFactory.create(querystring);
		QueryExecution qexec = QueryExecutionFactory.create(query, model);
		ResultSet results = null;
		List<QuerySolution> rl = new ArrayList<QuerySolution>();
		try {
            results = qexec.execSelect();
            while(results.hasNext()){
            	rl.add(results.next());
            }
        //    ResultSetFormatter.outputAsXML(System.out, results);
        } finally {
            qexec.close();
        }
		return rl;
	}
	
	
}


