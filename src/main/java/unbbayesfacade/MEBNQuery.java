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
	private final static PrintStream LOG = System.out;
	private String mebnFile;
	private String findingsFile;

	/**
	 * Constructor
	 * 
	 * @param mebnFile
	 *            String MEBN .ubf file path
	 * @param findingsFile
	 *            String findings .plm file path
	 */
	public MEBNQuery(String mebnFile, String findingsFile) {
		this.mebnFile = mebnFile;
		this.findingsFile = findingsFile;
	}

	/**
	 * Returns the MEBN object for the current UBF file
	 * 
	 * @return MultiEntityBayesianNetwork the MEBN
	 * @throws IOException
	 *             error loading .ubf MEBN file
	 */
	public MultiEntityBayesianNetwork getMEBN() throws IOException {
		LOG.println("\nMEBNQUERY: LOADING MEBN FILE: " + mebnFile + "\n");
		return UbfIO.getInstance().loadMebn(new File(this.mebnFile));
	}

	/**
	 * Create and returns a knowledge base for the given MEBN and loading the
	 * current findings file
	 * 
	 * @param mebn
	 *            MEBN to use. Should be created with MEBNQUery.getMEBN()
	 * @return KNowledgeBase PowerLoomKB instance with the findings file loaded.
	 * @throws IOException
	 *             error loading .plm findings file
	 */
	public KnowledgeBase getKB(MultiEntityBayesianNetwork mebn) throws IOException {
		TextModeRunner tmr = new TextModeRunner();
		LOG.println("\nMEBNQUERY: INITIALIZING KNOWLEDGE BASE\n");
		KnowledgeBase kb = PowerLoomKB.getNewInstanceKB();
		kb = tmr.createKnowledgeBase(kb, mebn);
		kb.loadModule(new File(findingsFile), true);
		return kb;
	}

	/**
	 * Save the the knowledge base state of things as a file with a
	 * set of findings.
	 */
	public void saveFindings(KnowledgeBase kb, MultiEntityBayesianNetwork mebn) {
		kb.saveFindings(mebn, new File(findingsFile));
	}
	
	/**
	 * Cals Laskey´s algorithm on the given MEBN and using the given KB,
	 * querying the node and args to that node passed as arguments too.
	 * 
	 * @param kb
	 *            KnowledgeBase to use
	 * @param mebn
	 *            MultiEntityBayesianNetwork to use
	 * @param node
	 *            Resident Node Random Variable name
	 * @param arg
	 *            Resident Node argument Ordinary Variable name
	 * @return ProbabilisticNode containing the query results
	 * @throws Exception
	 *             Laskey´s algorithm error
	 */
	public ProbabilisticNode executeMEBNQuery(KnowledgeBase kb, MultiEntityBayesianNetwork mebn, String node,
			String arg) throws Exception {
		TextModeRunner tmr = new TextModeRunner();
		// fill findings stored on kb
		KnowledgeBase filled = tmr.fillFindings(mebn, kb);
		// execute query
		QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments(node, arg);
		Collection<QueryNodeNameAndArguments> qnnaaCollection = new ArrayList<>();
		qnnaaCollection.add(qnnaa);
		LOG.println("\nMEBNQUERY: EXECUTING LASKEY´S ALGORYTHM\n");
		ProbabilisticNetwork net = tmr.callLaskeyAlgorithm(mebn, filled, qnnaaCollection);
		// TODO FUTURE WORK: IMPLEMENT SOME METHOD LIKE THIS ONE TO ENABLE
		// LEARNING
		learnMEBNQueryResult(mebn, kb, net);
		// TODO: MORE THAN 1 ARG SUPPORT NEEDED
		return (net == null) ? null
				: (ProbabilisticNode) net.getNode(qnnaa.getNodeName() + "__" + qnnaa.getArguments()[0]);
	}

	/**
	 * Would somehow learn the obtained query result, modifying the CPTs
	 * accordingly to it and the previous learned results.
	 * 
	 * @param mebn
	 * @param kb
	 * @param net
	 */
	public void learnMEBNQueryResult(MultiEntityBayesianNetwork mebn, KnowledgeBase kb, ProbabilisticNetwork net) {

	}
}
