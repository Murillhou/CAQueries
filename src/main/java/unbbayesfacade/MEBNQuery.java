package main.java.unbbayesfacade;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import unbbayes.TextModeRunner;
import unbbayes.TextModeRunner.QueryNodeNameAndArguments;
import unbbayes.io.mebn.UbfIO;
import unbbayes.prs.bn.ProbabilisticNetwork;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.kb.KnowledgeBase;
import unbbayes.prs.mebn.kb.powerloom.PowerLoomKB;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class MEBNQuery {
	private String mebnFile;
	private String findingsFile;
	
	public MEBNQuery(String mebnFile, String findingsFile){
		this.mebnFile = mebnFile;
		this.findingsFile = findingsFile;
	}

	public ProbabilisticNode executeMEBNQuery(String node, String arg) throws Exception {
		PrintStream log = System.out;
		TextModeRunner tmr = new TextModeRunner();

	// load ubf/owl
		UbfIO ubf = UbfIO.getInstance();
		MultiEntityBayesianNetwork mebn = null;
		log.println("\nMEBNQUERY: LOADING MEBN FILE: " + mebnFile + "\n");
		try {
			mebn = ubf.loadMebn(new File(mebnFile));
		} catch (IOException e) {
			log.println(e.getLocalizedMessage());
		}
		
	// initialize kb
		KnowledgeBase kb = PowerLoomKB.getNewInstanceKB();
		kb = tmr.createKnowledgeBase(kb, mebn);
		log.println("\nMEBNQUERY: INITIALIZING KNOWLEDGE BASE\n");
		try {
			kb.loadModule(new File(findingsFile), true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	// fill findings
		KnowledgeBase filled = tmr.fillFindings(mebn, kb);
	// execute query
		QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments(node, arg);
		Collection<QueryNodeNameAndArguments> qnnaaCollection = new ArrayList<>();
		qnnaaCollection.add(qnnaa);
		ProbabilisticNetwork net = null;
		log.println("\nMEBNQUERY: EXECUTING LASKEY´S ALGORYTHM\n");
		try {
			net = tmr.callLaskeyAlgorithm(mebn, filled, qnnaaCollection);
		} catch (Exception e) {
			throw e;
		}
//TODO: MORE THAN 1 ARG SUPPORT NEEDED
		return (net==null)? null : (ProbabilisticNode) net.getNode(qnnaa.getNodeName()+"__"+qnnaa.getArguments()[0]);
	}
	
}
