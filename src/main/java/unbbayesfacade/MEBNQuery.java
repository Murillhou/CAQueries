package main.java.unbbayesfacade;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import unbbayes.TextModeRunner;
import unbbayes.TextModeRunner.QueryNodeNameAndArguments;
import unbbayes.io.exception.UBIOException;
import unbbayes.io.mebn.UbfIO;
import unbbayes.io.mebn.exceptions.IOMebnException;
import unbbayes.prs.bn.ProbabilisticNetwork;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.RandomVariableFinding;
import unbbayes.prs.mebn.ResidentNode;
import unbbayes.prs.mebn.entity.Entity;
import unbbayes.prs.mebn.entity.ObjectEntityInstance;
import unbbayes.prs.mebn.entity.exception.TypeException;
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
	private KnowledgeBase kb;
	private MultiEntityBayesianNetwork mebn;

	/**
	 * Constructor
	 * 
	 * @param mebnFile
	 *            String MEBN .ubf file path
	 * @param findingsFile
	 *            String findings .plm file path
	 * @throws IOException 
	 * @throws IOMebnException 
	 */
	public MEBNQuery(String mebnFile, String findingsFile) throws IOException {
		this.mebnFile = mebnFile;
		this.findingsFile = findingsFile;
		LOG.println("\nMEBNQUERY: LOADING MEBN FILE: " + mebnFile + "\n");
		this.mebn = UbfIO.getInstance().loadMebn(new File(this.mebnFile));
		LOG.println("\nMEBNQUERY: INITIALIZING KNOWLEDGE BASE\n");
		TextModeRunner tmr = new TextModeRunner();
		this.kb = PowerLoomKB.getNewInstanceKB();
		this.kb = tmr.createKnowledgeBase(this.kb, this.mebn);
	}

	/**
	 * Returns the MEBN object 
	 * 
	 * @return MultiEntityBayesianNetwork the MEBN
	 * @throws IOException
	 *             error loading .ubf MEBN file
	 */
	public MultiEntityBayesianNetwork getMEBN() {
		return this.mebn;
	}

	/**
	 * Returns the knowledge base
	 * 
	 * @return KNowledgeBase PowerLoomKB instance with the findings file loaded.
	 * @throws IOException
	 *             error loading .plm findings file
	 */
	public KnowledgeBase getKB() {
		return this.kb;
	}

	public void addEntityInstanceFinding(String owlClassName, String owlIndividualName) throws TypeException {
		this.kb.insertEntityInstance(this.mebn.getObjectEntityContainer().getObjectEntityByName(owlClassName).addInstance(owlIndividualName.toUpperCase()));
	}
	
	public void addRandomVariableFinding(String irnName, String arg, String ein) {
		ObjectEntityInstance[] aux = new ObjectEntityInstance[1];
		aux[0] = mebn.getObjectEntityContainer().getEntityInstanceByName(arg);
		ResidentNode rn = mebn.getDomainResidentNode(irnName);
		Entity e = mebn.getObjectEntityContainer().getEntityInstanceByName(ein);
		if(aux[0]!=null && rn!=null && e!=null){
			this.kb.insertRandomVariableFinding(new RandomVariableFinding(rn, aux, e, this.mebn));
		}
	}
	
	/**
	 * Load previous findings or knowledge base states from the findings file.
	 * 
	 */
	public void loadFindings() throws UBIOException {
		this.kb.loadModule(new File(findingsFile), true);
	}
	
	/**
	 * Save the the knowledge base state of things on the findings file.
	 */
	public void saveFindings() {
		this.kb.saveFindings(this.mebn, new File(this.findingsFile));
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
	public ProbabilisticNode executeMEBNQuery(String node,
			String arg) throws Exception {
		TextModeRunner tmr = new TextModeRunner();
		// fill findings stored on kb
		KnowledgeBase filled = tmr.fillFindings(this.mebn, this.kb);
		// execute query
		QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments(node, arg);
		Collection<QueryNodeNameAndArguments> qnnaaCollection = new ArrayList<>();
		qnnaaCollection.add(qnnaa);
		LOG.println("\nMEBNQUERY: EXECUTING LASKEY´S ALGORYTHM\n");
		ProbabilisticNetwork net = tmr.callLaskeyAlgorithm(this.mebn, filled, qnnaaCollection);
		// TODO FUTURE WORK: IMPLEMENT SOME METHOD LIKE THIS ONE TO ENABLE
		// LEARNING
		learnMEBNQueryResult(net);
		// TODO: MORE THAN 1 ARG SUPPORT NEEDED
		return (net == null) ? null
				: (ProbabilisticNode) net.getNode(qnnaa.getNodeName() + "__" + qnnaa.getArguments()[0]);
	}

	/**
	 * Would somehow learn the obtained query result, modifying the CPTs
	 * accordingly to it and the previous learned results.
	 * 
	 * @param net
	 */
	public void learnMEBNQueryResult(ProbabilisticNetwork net) {

	}
}
