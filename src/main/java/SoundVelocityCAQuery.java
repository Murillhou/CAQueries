package main.java;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.jenafacade.SPARQLQuery;
import unbbayes.prs.mebn.entity.exception.CategoricalStateDoesNotExistException;
import unbbayes.prs.mebn.entity.exception.TypeException;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class SoundVelocityCAQuery {
	private static PrintStream log = System.out;
	private static String mebnFile = "src/main/resources/MEBN/SoundVelocity.ubf";
	private static String findingsFile = "src/main/resources/MEBN/soundvelocity_findings.plm";
	private static String ontNamespace = "http://www.semanticweb.org/SWARMs/ontology/";
	private static String ontologyFile = "src/main/resources/Ontology/SWARMsontologyMerged_v1_rdf.owl";
	private static String node = "SoundVelocity";

	private SoundVelocityCAQuery() {
	}

	public static void main(String[] args) {
		long start = System.nanoTime();
		mebnFile = (args[3] != null && args[3] != "") ? args[3] : mebnFile;
		findingsFile = (args[4] != null && args[4] != "") ? args[4] : findingsFile;
		ontologyFile = (args[5] != null && args[5] != "") ? args[5] : ontologyFile;
		ontNamespace = (args[6] != null && args[6] != "") ? args[6] : ontNamespace;

		List<Map<String, Float>> results = null;
		int i = 0;
		String[] positions = null;
		SPARQLQuery sparqlQuery = new SPARQLQuery(ontNamespace, ontologyFile);
		CAQuery caQuery = null;
		try {
			caQuery = new CAQuery(ontNamespace, ontologyFile, mebnFile, findingsFile);
			// get the spatial context of the coordinates passed as arguments
			// witha
			// radius constant of 2.0
			log.println("\nSOUNDVELOCITYCAQUERY: GETTING SPATIAL CONTEXT REFERENCES\n");
			List<QuerySolution> lqs = sparqlQuery.getSpatialContext(args[0], args[1], args[2], 2);
			// if there is knowledge about the spatial context
			if (!lqs.isEmpty()) {
				// for each gpsPosition on the result
				log.println("\nSOUNDVELOCITYCAQUERY: " + lqs.size() + " SPATIAL CONTEXT REFERENCES");
				results = new ArrayList<Map<String, Float>>();
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
					log.println("\nSOUNDVELOCITYCAQUERY: RUNNING SOUND VELOCITY CA QUERY FOR GPPOSITION [" + lat + ", "
							+ lon + ", " + alt + "].");

					// fill findings file from OWL ontology
					String[] aux = new String[3];
					aux[0] = lat;
					aux[1] = lon;
					aux[2] = alt;
					log.println("\nSOUNDVELOCITYCAQUERY: FILLING FINDINGS FILE FILE: " + findingsFile + "\n");
					long start1 = System.nanoTime();
					String gpsPos = caQuery.getFindings(0, aux, node, "gpsPos");
					long end1 = System.nanoTime();
					log.println("\nSOUNDVELOCITYCAQUERY: FINDINGS STORED ON " + (end1 - start1) * 0.000000001
							+ " SECONDS.\n");

					if (gpsPos != null) {
						// execute MEBN query
						log.println("\nSOUNDVELOCITYCAQUERY: EXECUTING MEBN QUERY\n");
						long start2 = System.nanoTime();
						Map<String, Float> res = caQuery.executeQuery(node, gpsPos);
						long end2 = System.nanoTime();
						log.println("\nSOUNDVELOCITYCAQUERY: QUERY EXECUTED ON " + (end2 - start2) * 0.000000001
								+ " SECONDS.\n");
						results.add(res);
					} else
						i--;
				}
			}
			// print results
			log.print("\n\n*****************************RESULTS*****************************\n"
					+ "*								*");
			Iterator<Map<String, Float>> it1 = results.iterator();
			int pi = -1;
			while (it1.hasNext()) {
				pi++;
				log.print("\n*               --GPS position: " + positions[pi] + "--               *\n  ");
				Map<String, Float> m = it1.next();
				Iterator<String> it2 = m.keySet().iterator();
				while (it2.hasNext()) {
					String s = it2.next();
					log.print(s + " -> " + m.get(s) + ", ");
				}
				log.print("\n");
			}
			log.print("*								*"
					+ "\n*****************************************************************\n");

			// TODO
			// save findings for posterior uses (disabled due to temporal
			// references absence)
			// caQuery.saveFindings();

			long end = System.nanoTime();
			log.println("TOTAL RUNTIME: " + ((end - start) * 0.000000001));
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
