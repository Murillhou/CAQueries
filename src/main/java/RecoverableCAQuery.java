package main.java;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import unbbayes.prs.mebn.entity.exception.CategoricalStateDoesNotExistException;
import unbbayes.prs.mebn.entity.exception.TypeException;

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

	private RecoverableCAQuery() {
	}

	public static void main(String[] args) {
		long start = System.nanoTime();
		mebnFile = (args[1] != null && args[1] != "") ? args[1] : mebnFile;
		findingsFile = (args[2] != null && args[2] != "") ? args[2] : findingsFile;
		ontologyFile = (args[3] != null && args[3] != "") ? args[3] : ontologyFile;
		ontNamespace = (args[4] != null && args[4] != "") ? args[4] : ontNamespace;

		log.println("\nRUNNING RECOVERABLE CA QUERY");
		CAQuery caQuery = null;
		try {
			caQuery = new CAQuery(ontNamespace, ontologyFile, mebnFile, findingsFile);
			// fill findings file from OWL ontology
			String[] aux = new String[2];
			aux[0] = args[0];
			aux[1] = "AutonomousRobot";

			log.println("\nRECOVERABLECAQUERY: GETTING FINDINGS.\n");
			long start1 = System.nanoTime();
			caQuery.getFindings(1, aux, node, "autRob");
			long end1 = System.nanoTime();
			log.println("\nRECOVERABLECAQUERY: FINDINGS STORED ON " + (end1 - start1) * 0.000000001 + ". SECONDS.\n");

			// execute query
			log.println("\nRECOVERABLECAQUERY: EXECUTING MEBN QUERY\n");
			long start2 = System.nanoTime();
			Map<String, Float> res = caQuery.executeQuery(node, args[0]);
			long end2 = System.nanoTime();
			log.println("\nRECOVERABLECAQUERY: QUERY EXECUTED ON " + (end2 - start2) * 0.000000001 + ". SECONDS.\n");

			// print result
			log.println("\n\n*********************RESULT*********************");
			log.print("*                                              *\n*");
			for (String s : res.keySet()) {
				log.print(" " + s + " -> " + res.get(s) + ",");
			}
			log.println("*\n*                                              *");
			log.println("************************************************");

			// TODO
			// save findings for posterior uses (disabled due to temporal
			// references absence)
			// caQuery.saveFindings();

			long end = System.nanoTime();
			log.println("\nTOTAL RUNTIME: " + ((end - start) * 0.000000001));
		} catch (IOException e) {
			log.println(e.getLocalizedMessage());
		} catch (TypeException e) {
			log.println(e.getLocalizedMessage());
		} catch (CategoricalStateDoesNotExistException e) {
			log.println(e.getLocalizedMessage());
		} catch (Exception e) {
			log.println(e.getLocalizedMessage());
		}
	}
}
