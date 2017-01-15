package main.java.unbbayes;

import java.util.ArrayList;
import java.util.Collection;

import unbbayes.TextModeRunner;
import unbbayes.TextModeRunner.QueryNodeNameAndArguments;
import unbbayes.prs.bn.ProbabilisticNetwork;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.kb.KnowledgeBase;

/**
 * 
 * @author Pablo Murillo
 *
 */
public class MEBNQuery {

	private static ProbabilisticNetwork net;
	
	public MEBNQuery() {}

	public static ProbabilisticNode executeMEBNQuery(KnowledgeBase kb, TextModeRunner tmr, MultiEntityBayesianNetwork mebn, String findingsFile, QueryNodeNameAndArguments qnnaa) throws Exception {
		ProbabilisticNode n = null;
	// fill findings
		kb = tmr.fillFindings(mebn, kb);
	// execute query
		Collection<QueryNodeNameAndArguments> qnnaaCollection = new ArrayList<QueryNodeNameAndArguments>();
		qnnaaCollection.add(qnnaa);
		try {
			net = tmr.callLaskeyAlgorithm(mebn, kb, qnnaaCollection);
		} catch (Exception e) {
			throw e;
		}
//TODO: MORE THAN 1 ARG SUPPORT NEEDED
		n = (ProbabilisticNode) net.getNode(qnnaa.getNodeName()+"__"+qnnaa.getArguments()[0]);
		return n;
	}
	
}
