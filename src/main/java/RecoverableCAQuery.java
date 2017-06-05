package main.java;

import java.io.IOException;
import java.io.PrintStream;

import main.java.unbbayesfacade.MEBNQuery;
import unbbayes.prs.bn.ProbabilisticNode;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class RecoverableCAQuery {
	private static String mebnFile = "src/main/resources/MEBN/Recoverable.ubf";
	private static String findingsFile = "src/main/resources/KnowledgeBase/recoverable_findings.plm";
	private static String ontNamespace = "http://www.semanticweb.org/SWARMs/ontology/";
	private static String ontologyFile = "src/main/resources/Ontology/SWARMsontologyMerged_v1_rdf.owl";
	private static PrintStream log = System.out;
	private static String node = "Recoverable";

	private RecoverableCAQuery() {}

	public static void main(String[] args) {
		long start = System.nanoTime();
		log.println("\nRUNNING RECOVERABLE CA QUERY");
		CAQuery caQuery = new CAQuery(ontNamespace, ontologyFile);
		
	// fill findings file from OWL ontology
		String[] aux = new String[2];
		aux[0] = args[0];
		aux[1] = "AutonomousRobot";
		log.println("\nRECOVERABLECAQUERY: FILLING FINDINGS FILE: " + args[2] + "\n");
		try {
			long start1 = System.nanoTime();
			caQuery.fillFindings(args[2], args[1], 1, aux, node, "autRob");
			long end1 = System.nanoTime();
			log.println("\nRECOVERABLECAQUERY: FINDINGS FILE FILLED ON "+(end1-start1)*0.000000001+" SECONDS.\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		}		
		
	// execute MEBN query
		MEBNQuery mebnQuery = new MEBNQuery(mebnFile, findingsFile);
		ProbabilisticNode pn = null;
		log.println("\nRECOVERABLECAQUERY: EXECUTING MEBN QUERY\n");
		try {
			long start2 = System.nanoTime();
			pn = mebnQuery.executeMEBNQuery(node, args[0]);
			long end2 = System.nanoTime();
			log.println("\nRECOVERABLECAQUERY: MEBN QUERY EXECUTED ON "+(end2-start2)*0.000000001+" SECONDS.\n");
		// print result
			log.println("\n\n*********************RESULT*********************");
			log.print("*                                              *\n*");
			for (int i = 0; i < pn.getStatesSize(); i++){
				log.print(" "+pn.getStateAt(i) + " -> " + pn.getMarginalAt(i) + ",");
			}
			log.println("*\n*                                              *");
			log.println("************************************************\n");
			long end = System.nanoTime();
			log.println("TOTAL RUNTIME: "+((end - start)*0.000000001));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}

}
