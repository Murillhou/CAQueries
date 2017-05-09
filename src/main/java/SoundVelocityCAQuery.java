package main.java;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.semanticweb.owlapi.util.MultiMap;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.helpers.PrintWriterHelper;
import main.java.jena.SPARQLQuery;
import main.java.jena.SPARQLQueryBuilder;
import main.java.unbbayes.MEBNQuery;
import unbbayes.TextModeRunner;
import unbbayes.TextModeRunner.QueryNodeNameAndArguments;
import unbbayes.io.exception.UBIOException;
import unbbayes.io.mebn.UbfIO;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.MFrag;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.OrdinaryVariable;
import unbbayes.prs.mebn.kb.KnowledgeBase;
import unbbayes.prs.mebn.kb.powerloom.PowerLoomKB;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class SoundVelocityCAQuery {

	private static String mebnFile = "src/main/resources/MEBN/SoundVelocity.ubf";
	private static String findingsFile = "src/main/resources/MEBN/soundvelocity_findings.plm";
	private static TextModeRunner tmr;
	private static KnowledgeBase kb;
	private static PrintStream so = System.out;

	public SoundVelocityCAQuery() {
	}

	public static void main(String[] args) {
		tmr = new TextModeRunner();

		// get the spatial context of the coordinates passed as arguments witha
		// radius constant of 2.0
		List<QuerySolution> lqs = SPARQLQuery.getSpatialContext(args[0], args[1], args[2], 2);
		// if there is knowledge about the spatial context
		if (lqs.size() > 0) {
			// for each gpsPosition on the result
			for (QuerySolution qs : lqs) {
				String lat = qs.get("?lat").toString();
				lat = lat.substring(0, lat.indexOf("^^"));
				String lon = qs.get("?lon").toString();
				lon = lon.substring(0, lon.indexOf("^^"));
				String alt = qs.get("?alt").toString();
				alt = alt.substring(0, alt.indexOf("^^"));
				String[] gpsPosCoord = { lat, lon, alt };
				// load ubf/owl
				UbfIO ubf = UbfIO.getInstance();
				MultiEntityBayesianNetwork mebn = null;
				try {
					mebn = ubf.loadMebn(new File(mebnFile));
				} catch (IOException e) {
					e.printStackTrace();
				}
				// fill findings file from OWL ontology
				String gpsPos = fillFindingsFile(gpsPosCoord, mebn);
				// initialize kb
				kb = PowerLoomKB.getNewInstanceKB();
				kb = tmr.createKnowledgeBase(kb, mebn);
				// load kb
				try {
					kb.loadModule(new File(findingsFile), true);
				} catch (UBIOException e) {
					e.printStackTrace();
				}
				// execute MEBN query
				QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments("SoundVelocity", gpsPos);
				ProbabilisticNode pn = null;
				try {
					pn = MEBNQuery.executeMEBNQuery(kb, tmr, mebn, findingsFile, qnnaa);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// print result
				so.println("********RESULT********\n" + pn.getName());
				for (int i = 0; i < pn.getStatesSize(); i++)
					so.print(pn.getStateAt(i) + " -> " + pn.getMarginalAt(i) + ", ");
				so.print("\n");
			}
		}

	}

	private static String fillFindingsFile(String[] gpsPosCoord, MultiEntityBayesianNetwork mebn) {
		// initialize variables
		Map<OrdinaryVariable, String> argsMap = new HashMap<OrdinaryVariable, String>();
		MultiMap<OrdinaryVariable, String> globalMap = new MultiMap<OrdinaryVariable, String>();
		PrintWriterHelper pw = null;
		try {
			pw = new PrintWriterHelper(findingsFile, "UTF-8");
		} catch (UnsupportedEncodingException | FileNotFoundException e) {
			e.printStackTrace();
		}
		// get MFrag
		MFrag mfrag = mebn.getMFragByName("SoundVelocity_MFrag");
		// // print all
		// CAQuery.printAll(mebn.getDomainResidentNodes());
		// EXECUTE TYPE SEARCH QUERY IN ORDER TO KNOW IF THERE IS A GPSPoasition
		// ENTITY IN THE ONTOLOGY WITH THE SAME COORDINATES SPECIFIED AS
		// ARGUMENTS
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		sqb.addToSelect("?gpsPos");
		sqb.addTripleToWhere("?gpsPos", "ns:gpsLatitude", "\"" + gpsPosCoord[0] + "\"^^xsd:float");
		sqb.addTripleToWhere("?gpsPos", "ns:gpsLongitude", "\"" + gpsPosCoord[1] + "\"^^xsd:float");
		sqb.addTripleToWhere("?gpsPos", "ns:gpsAltitude", "\"" + gpsPosCoord[2] + "\"^^xsd:float");
		List<QuerySolution> l = SPARQLQuery.executeArgQuery(sqb.getQuery());
		String sol = null;
		// IF THE POSITION EXISTS (THEN gpsPos OV IN MTHEORY REFERS TO THAT
		// ONTOLOGY INDIVIDUAL)
		if (l.size() > 0) {
			// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY WITH
			// THE DESIRED GPSPOSITION(INDEX 0)
			QuerySolution qs = l.get(0);
			// GET IRI
			String solutionIRI = qs.get("?gpsPos").toString();
			// GET INDIVIDUAL NAME
			sol = CAQuery.iriToName(solutionIRI);
			// STORE ON ORDINARY VARIABLES MAPS
			argsMap.put(mfrag.getOrdinaryVariableByName("gpsPos"), solutionIRI);
			globalMap.put(mfrag.getOrdinaryVariableByName("gpsPos"), solutionIRI);
			// ADD ENTITY INSTANCE TO THE FINDINGS FILE:"(ASSERT
			// (GPSPosition_LABEL gpsPos))"
			pw.println("(ASSERT (GPSPosition_LABEL " + sol + "))");
			// CALL RECURSIVE FUNCTION ON SOUNDVELOCITY_MFRAG{
			so.println("**************************EXECUTING RECURSIVE FUNCTION ON MFRAG: " + mfrag);
			CAQuery.recursiveFunction(mfrag, pw, argsMap, globalMap);
		} else {
			so.println("THE ARGUMENT GPSPOS WASNT FOUND.");
		}
		pw.close();
		return sol;
	}

}
