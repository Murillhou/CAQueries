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
public class RecoverableCAQuery {

	private static String mebnFile = "src/main/resources/MEBN/Recoverable.ubf";
	private static String findingsFile = "src/main/resources/KnowledgeBase/recoverable_findings.plm";
	private static TextModeRunner tmr;
	private static KnowledgeBase kb;	
	private static PrintStream so = System.out;
	
	public RecoverableCAQuery() {}	
	
	public static void main(String[] args) {
		String vehicle = args[0];		
		tmr = new TextModeRunner();
	// load ubf/owl
		UbfIO ubf = UbfIO.getInstance();
		MultiEntityBayesianNetwork mebn = null;
		try {
			mebn = ubf.loadMebn(new File(mebnFile));
		} catch (IOException e) {
			e.printStackTrace();
		}
	// fill findings file from OWL ontology
		fillFindingsFile(vehicle, mebn);
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
		QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments("Recoverable",vehicle);
		ProbabilisticNode pn = null;
		try {
			pn = MEBNQuery.executeMEBNQuery(kb, tmr, mebn, findingsFile, qnnaa);
		} catch (Exception e) {
			e.printStackTrace();
		}
	// print result
		so.println("********RESULT********\n"+pn.getName());
		for(int i = 0; i < pn.getStatesSize(); i++) so.print(pn.getStateAt(i)+" -> "+pn.getMarginalAt(i)+", ");
		so.print("\n");
	}	
	
	private static void fillFindingsFile(String vehicle, MultiEntityBayesianNetwork mebn) {
	// initialize variables	
		Map<OrdinaryVariable,String> argsMap = new HashMap<OrdinaryVariable,String>();
		MultiMap<OrdinaryVariable,String> globalMap = new MultiMap<OrdinaryVariable,String>();
		PrintWriterHelper pw = null;
		try {pw = new PrintWriterHelper(findingsFile, "UTF-8");} 
		catch (UnsupportedEncodingException | FileNotFoundException e) {e.printStackTrace();}
	// get MFrag
		MFrag mfrag = mebn.getMFragByName("Recoverable_MFrag");
//	// print all
//		CAQuery.printAll(mebn.getDomainResidentNodes());
	
	// EXECUTE QUERY IN ORDER TO KNOW IF THE VEHICLE ON THE ARGUMENT EXISTS IN THE ONTOLOGY
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		sqb.addToSelect("?autRob");
		sqb.addTripleToWhere("?autRob", "rdf:type", "ns:AutonomousRobot");
	List<QuerySolution> l = SPARQLQuery.executeArgQuery(sqb.getQuery());
		// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY WITH THE DESIRED VEHICLE(INDEX 0) 
		QuerySolution qs = l.get(0);
		String solutionIRI = qs.get("?autRob").toString();
	// IF THE VEHICLE EXISTS AND IT IS AN AUTONOMOUS ROBOT (THEN autRob ENTITY INSTANCE IN MTHEORY REFERS TO THAT VEHICLE)
		if(solutionIRI.equals(SPARQLQuery.ontNamespace+vehicle)){
		// STORE "AUTROB->AUTONOMOUSROBOT ARG" ON ORDINARY VARIABLES MAPS
			argsMap.put(mfrag.getOrdinaryVariableByName("autRob"), solutionIRI);
			globalMap.put(mfrag.getOrdinaryVariableByName("autRob"), solutionIRI);
		// ADD TO THE FINDINGS FILE:"(ASSERT (AUTONOMOUSROBOT_LABEL AUTROB1))" 
			pw.println("(ASSERT (AUTONOMOUSROBOT_LABEL "+vehicle+"))");
		// CALL RECURSIVE FUNCTION ON AUTONOMY_MFRAG{
			so.println("**************************EXECUTING RECURSIVE FUNCTION ON MFRAG: :"+mfrag);
			CAQuery.recursiveFunction(mfrag, pw, argsMap, globalMap);
		}else {
			so.println("THE ARGUMENT VEHICLE WASNT FOUND.");
		}
		pw.close();
	}
			
	
}
