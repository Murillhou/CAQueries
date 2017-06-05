package main.java;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.jenafacade.SPARQLQuery;
import main.java.unbbayesfacade.MEBNQuery;
import unbbayes.prs.bn.ProbabilisticNode;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class SoundVelocityCAQuery {
	private static String mebnFile = "src/main/resources/MEBN/SoundVelocity.ubf";
	private static String findingsFile = "src/main/resources/MEBN/soundvelocity_findings.plm";
	private static String ontNamespace = "http://www.semanticweb.org/SWARMs/ontology/";
	private static String ontologyFile = "src/main/resources/Ontology/SWARMsontologyMerged_v1_rdf.owl";
	private static PrintStream log = System.out;
	private static String node = "SoundVelocity";
	
	private SoundVelocityCAQuery() {
	}

	public static void main(String[] args) {
		long start = System.nanoTime();
		ProbabilisticNode[] results = null;
		int i = 0;
		String[] positions = null;
		SPARQLQuery sparqlQuery = new SPARQLQuery(ontNamespace, ontologyFile);
		CAQuery caQuery = new CAQuery(ontNamespace, ontologyFile);

		// get the spatial context of the coordinates passed as arguments witha
		// radius constant of 2.0
		log.println("\nSOUNDVELOCITYCAQUERY: GETTING SPATIAL CONTEXT REFERENCES\n");
		long start1 = System.nanoTime();
		List<QuerySolution> lqs = sparqlQuery.getSpatialContext(args[0], args[1], args[2], 2);
		long end1 = System.nanoTime();
		log.println("SPARQL QUERY FOR GETTING SPATIAL CONTEXT TOTAL RUNTIME: "+((end1 - start1)*0.000000001));
		// if there is knowledge about the spatial context
		if (!lqs.isEmpty()) {
			// for each gpsPosition on the result
			log.println("\nSOUNDVELOCITYCAQUERY: " + lqs.size() + " SPATIAL CONTEXT REFERENCES");
			results = new ProbabilisticNode[lqs.size()];
			positions = new String[lqs.size()];
			for (QuerySolution qs : lqs) {
				i++;
				String lat = qs.get("?lat").toString();
				lat = lat.substring(0, lat.indexOf("^^"));
				String lon = qs.get("?lon").toString();
				lon = lon.substring(0, lon.indexOf("^^"));
				String alt = qs.get("?alt").toString();
				alt = alt.substring(0, alt.indexOf("^^"));
				positions[i - 1] = "[" + lat + ", " + lon + ", " + alt + "]";
				log.println("\nSOUNDVELOCITYCAQUERY: RUNNING SOUND VELOCITY CA QUERY FOR GPPOSITION [" + lat + ", " + lon
						+ ", " + alt + "].");

				// fill findings file from OWL ontology
				String[] aux = new String[3];
				aux[0] = lat;
				aux[1] = lon;
				aux[2] = alt;
				String gpsPos = null;
				log.println("\nSOUNDVELOCITYCAQUERY: FILLING FINDINGS FILE FILE: " + args[4] + "\n");
				try {
					long start2 = System.nanoTime();
					gpsPos = caQuery.fillFindings(args[4], args[3], 0, aux, node, "gpsPos");
					long end2 = System.nanoTime();
					log.println("\nSOUNDVELOCITYCAQUERY: FINDINGS FILE FILLED ON "+(end2-start2)*0.000000001+" SECONDS.\n");
				} catch (IOException e) {
					log.println(e.getLocalizedMessage());
				}

				if (gpsPos != null) {

					// execute MEBN query
					MEBNQuery mebnQuery = new MEBNQuery(mebnFile, findingsFile);
					ProbabilisticNode pn = null;
					log.println("\nSOUNDVELOCITYCAQUERY: EXECUTING MEBN QUERY\n");
					try {
						long start3 = System.nanoTime();
						pn = mebnQuery.executeMEBNQuery(node, gpsPos);
						long end3 = System.nanoTime();
						log.println("\nSOUNDVELOCITYCAQUERY:MEBN QUERY EXECUTED ON "+(end3-start3)*0.000000001+" SECONDS.\n");
						results[i - 1] = pn;
					} catch (Exception e) {
						log.println(e.getLocalizedMessage());
					}
				} else
					i--;
			}
		}

		// print results
		log.print("\n\n*****************************RESULTS*****************************\n"
				+ "*								*");
		for (int j = 0; j < i; j++) {
			log.print("\n*               --GPS position: " + positions[j] + "--               *\n  ");
			for (int k = 0; k < results[j].getStatesSize(); k++) {
				log.print(results[j].getStateAt(k) + " -> " + results[j].getMarginalAt(k) + ", ");
			}
			log.print("\n");
		}
		log.print("*								*"
				+ "\n*****************************************************************\n");
		long end = System.nanoTime();
		log.println("TOTAL RUNTIME: "+((end - start)*0.000000001));

	}
}
