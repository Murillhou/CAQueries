package main.java;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.semanticweb.owlapi.util.MultiMap;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.jenafacade.SPARQLQuery;
import main.java.jenafacade.SPARQLQueryBuilder;
import main.java.unbbayesfacade.MEBNQuery;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.Argument;
import unbbayes.prs.mebn.ContextNode;
import unbbayes.prs.mebn.InputNode;
import unbbayes.prs.mebn.MFrag;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.OrdinaryVariable;
import unbbayes.prs.mebn.RandomVariableFinding;
import unbbayes.prs.mebn.ResidentNode;
import unbbayes.prs.mebn.entity.CategoricalStateEntity;
import unbbayes.prs.mebn.entity.Entity;
import unbbayes.prs.mebn.entity.ObjectEntity;
import unbbayes.prs.mebn.entity.ObjectEntityInstance;
import unbbayes.prs.mebn.entity.StateLink;
import unbbayes.prs.mebn.entity.exception.CategoricalStateDoesNotExistException;
import unbbayes.prs.mebn.entity.exception.TypeException;
import unbbayes.prs.mebn.kb.KnowledgeBase;

public class CAQuery {
	private final static PrintStream LOG = System.out;
	private final static String CATLABEL = "CategoryLabel";
	private final static String LABEL = "_LABEL ";
	private final static String TYPE = " TYPE: ";
	private final static String RDFTYPE = "rdf:type";
	/**
	 * IRI prefix name-space of the OWL ontology
	 */
	private String ontNamespace;
	/**
	 * UnBBayes and Jena API facades
	 */
	private MEBNQuery mebnQuery;
	private SPARQLQuery sparqlQuery;
	/**
	 * UnBBayes objects representing the MEBN to load and the knowledge base
	 * with the findings
	 */
	private MultiEntityBayesianNetwork mebn;
	private KnowledgeBase kb;
	/**
	 * Field used to store the "resolved OVs" (those with some result individual
	 * or value IRI mapped to it)
	 */
	private MultiMap<OrdinaryVariable, String> globalResolvedOVs;

	/**
	 * Constructor
	 * 
	 * @param ontNamespace
	 *            String OWL ontology IRI prefix name-space
	 * @param ontologyFile
	 *            String OWL ontology file path
	 * @param mebnFile
	 *            String MEBN .ubf file path
	 * @param findingsFile
	 *            String findings .plm file path
	 * @throws IOException
	 *             Error loading files
	 */
	public CAQuery(String ontNamespace, String ontologyFile, String mebnFile, String findingsFile) throws IOException {
		this.ontNamespace = ontNamespace;
		this.mebnQuery = new MEBNQuery(mebnFile, findingsFile);
		this.sparqlQuery = new SPARQLQuery(ontNamespace, ontologyFile);
		this.mebn = mebnQuery.getMEBN();
		this.kb = mebnQuery.getKB(mebn);
	}

	/**
	 * Executes Laskey´s algorithm query for the current state of both the MEBn
	 * and the KB
	 * 
	 * @param node
	 *            String Resident Node RV name
	 * @param arg
	 *            String Resident Node arg OV name
	 * @return ProbabilisticNode with the query solutions
	 * @throws Exception
	 *             Laskey´s algorithm error
	 */
	public Map<String,Float> executeQuery(String node, String arg) throws Exception {
		ProbabilisticNode pn = mebnQuery.executeMEBNQuery(this.kb, this.mebn, node, arg);
		Map<String,Float> res = new HashMap<>();
		for(int i = 0; i < pn.getStatesSize(); i++){
			res.put(pn.getStateAt(i), pn.getMarginalAt(i));
		}
		return res;
	}

	/**
	 * 
	 * For the current instance, get the current existing findings from the OWL ontology, that would help to
	 * run a MEBN query against the resident node of the a MFrag whose name is passed as
	 * argument(mfragName), and with the arguments specified by args and argOVName.
	 * 
	 * @param type
	 *            0 if it is an environmental CA query (GPS coordinates as
	 *            argument), 1 if it is a regular CA query
	 * @param args
	 *            argument value. [xsd:float latitude, xsd:float longitude,
	 *            xsd:float altitude] if type 0, [individualName,
	 *            individualClass] if type 1
	 * @param mfragName
	 *            queried MFrag name (same as its Resident Node and the RV)
	 * @param argOVName
	 *            argument OV name on the MEBN
	 * @return the argument individual IRI if found
	 * @throws IOException
	 * @throws TypeException
	 * @throws CategoricalStateDoesNotExistException
	 */
	public String getFindings(int type, String[] args, String mfragName, String argOVName)
			throws IOException, TypeException, CategoricalStateDoesNotExistException {

		// INITIALIZE VARIABLES
		this.globalResolvedOVs = new MultiMap<>();
		/**
		 * Helps to build SPARQL queries
		 */
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		/**
		 * Used to store the result individuals or values for the current MFrag
		 * RN
		 */
		Map<OrdinaryVariable, String> resolvedArgs = new HashMap<>();
		/**
		 * List used to store the query results.
		 */
		List<QuerySolution> qsl;
		/**
		 * Strin function result.
		 */
		String sol = null;

		// LOAD MFRAG
		MFrag mfrag = this.mebn.getMFragByName(mfragName + "_MFrag");

		// SWITCH TYPE
		if (type == 0) { // ENVIRONMENTAL
			// EXECUTE QUERY IN ORDER TO KNOW IF THERE IS A GPSPoasition ENTITY
			// IN THE ONTOLOGY
			// WITH THE SAME COORDINATES SPECIFIED AS ARGUMENTS
			sqb.addToSelect("?" + argOVName);
			String xsdFloat = "\"^^xsd:float";
			sqb.addTripleToWhere("?" + argOVName, "ns:gpsLatitude", "\"" + args[0] + xsdFloat);
			sqb.addTripleToWhere("?" + argOVName, "ns:gpsLongitude", "\"" + args[1] + xsdFloat);
			sqb.addTripleToWhere("?" + argOVName, "ns:gpsAltitude", "\"" + args[2] + xsdFloat);
			qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// IF THE POSITION EXISTS (THEN gpsPos OV IN MTHEORY REFERS TO THAT
			// ONTOLOGY INDIVIDUAL)
			if (!qsl.isEmpty()) {
				// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY
				String solutionIRI = qsl.get(0).get("?" + argOVName).toString();
				// GET INDIVIDUAL NAME
				sol = CAQuery.iriToName(solutionIRI);
				// STORE ON LOCAL ARGS AND GLOBAL ORDINARY VARIABLES MAPS
				resolvedArgs.put(mfrag.getOrdinaryVariableByName(argOVName), solutionIRI);
				this.globalResolvedOVs.put(mfrag.getOrdinaryVariableByName(argOVName), solutionIRI);
				// ADD ENTITY INSTANCE TO THE KB:"(ASSERT
				// (GPSPosition_LABEL gpsPos))"
				this.kb.insertEntityInstance(
						mebn.getObjectEntityContainer().getObjectEntityByName("GPSPosition").addInstance(sol));
				// CALL RECURSIVE FUNCTION ON SOUNDVELOCITY_MFRAG
				LOG.println("\nCAQUERY: **************************EXECUTING RECURSIVE FUNCTION ON MFRAG: " + mfrag);
				this.recursiveFunction(mfrag, resolvedArgs);
			} else {
				LOG.println(
						"\nCAQUERY: THE GPS POSITION [" + args[0] + ", " + args[1] + ", " + args[2] + "] WASNT FOUND.");
			}
		} else if (type == 1) { // INDIVIDUAL
			// EXECUTE QUERY IN ORDER TO KNOW IF THE INDIVIDUAL ON THE ARGUMENT
			// EXISTS IN THE ONTOLOGY
			sqb.addToSelect("?" + argOVName);
			sqb.addTripleToWhere("?" + argOVName, RDFTYPE, "ns:" + args[1]);
			qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY WITH
			// THE DESIRED INDIVIDUAL(INDEX 0)
			String solutionIRI = qsl.get(0).get("?" + argOVName).toString();
			// IF THE INDIVIDUAL EXISTS AND IT HAS THE EXPECTED IRI
			if (solutionIRI.equals(ontNamespace + args[0])) {
				// STORE IT ON ORDINARY VARIABLES MAPS
				resolvedArgs.put(mfrag.getOrdinaryVariableByName(argOVName), solutionIRI);
				this.globalResolvedOVs.put(mfrag.getOrdinaryVariableByName(argOVName), solutionIRI);
				// ADD THE ENTITY INSTANCE AS A FINDING ON THE KB AS, FOR
				// EXAMPLE:"(ASSERT
				// (AUTONOMOUSROBOT_LABEL AUTROB1))"
				ObjectEntity a = mebn.getObjectEntityContainer().getObjectEntityByName(args[1]);
				ObjectEntityInstance b = a.addInstance(args[0].toUpperCase());
				this.kb.insertEntityInstance(b);
				// CALL RECURSIVE FUNCTION ON THE DESIRED MFRAG
				LOG.println("\nCAQUERY: **************************EXECUTING RECURSIVE FUNCTION ON MFRAG: :" + mfrag);
				this.recursiveFunction(mfrag, resolvedArgs);
			} else {
				LOG.println("CAQUERY: THE ARGUMENT INDIVIDUAL WASNT FOUND.");
			}
			sol = null;
		}
		return sol;
	}

	/**
	 * Save the current state of things on the knowledge base a s a file with a
	 * set of findings.
	 */
	public void saveFindings() {
		this.mebnQuery.saveFindings(this.kb, this.mebn);
	}

	/**
	 * 
	 * @param mfrag
	 * @param args
	 * @throws CategoricalStateDoesNotExistException
	 * @throws TypeException
	 */
	private void recursiveFunction(MFrag mfrag, Map<OrdinaryVariable, String> args)
			throws CategoricalStateDoesNotExistException, TypeException {
		// INITIALIZE VARIABLES
		/**
		 * Helps to build SPARQL queries
		 */
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		/**
		 * Used to store the IRIs of the SPARQL variables representing each OV.
		 */
		Map<OrdinaryVariable, String> ovSPARQLvars = new HashMap<>();
		// GET MFRAG OVs, CNs, AND RNs ARGUMENTS
		List<OrdinaryVariable> ovList = mfrag.getOrdinaryVariableList();
		List<ContextNode> cnList = mfrag.getContextNodeList();
		List<OrdinaryVariable> rnArgOVList = new ArrayList<>();
		// FOR EACH ARGUMENT OF THE MFRAG RESIDENT NODE
		// TODO ONLY SUPPORT 1 RESIDENT NODE PER MFRAG (WITH 1 ARGUMENT)
		for (Argument a : mfrag.getResidentNodeList().get(0).getArgumentList()) {
			rnArgOVList.add(a.getOVariable());
		}
		LOG.println("CAQUERY: **********PROCESSING OV AND CONTEXT NODES FOR MFRAG " + mfrag);
		// BUILD OV AND CN QUERY
		// FOR EACH ORDINARY VARIABLE ON THE MFRAG
		for (OrdinaryVariable ov : ovList) {
			// IF IT IS NOT PRESENT AS RESIDENT´S NODE ARGUMENT
			if (!rnArgOVList.contains(ov)) {
				// ADD TO SPARQLQUERY-SELECT: OV_NAME
				sqb.addToSelect("?" + ov.getName());
				// ADD TO SPARQLQUERY-WHERE: OV_TYPE
				sqb.addTripleToWhere("?" + ov.getName(), RDFTYPE, "ns:" + ov.getValueType());
				// ADD THE ORDINARY VARIABLEV TO local resolved OVs
				ovSPARQLvars.put(ov, "?" + ov.getName());
			}
		}
		// FOR EACH CONTEXT NODE ON THE MFRAG
		// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA
		// "CN_NAME(CNARGOV0)=CNARGOV1"
		for (ContextNode cn : cnList) {
			// GET CN CORRESPONDING OWL PROPERTY NAME
			String cnName = labelToName(cn.getLabel());
			// GET CN FORMULA ARGUMENTS ORDINARY VARIABLES
			List<OrdinaryVariable> cnargOVList = new ArrayList<>();
			Iterator<OrdinaryVariable> it = cn.getFormulaTree().getChildren().get(0).getVariableList().iterator();
			cnargOVList.add(it.next());
			it = cn.getFormulaTree().getChildren().get(1).getVariableList().iterator();
			cnargOVList.add(it.next());
			// TODO ONLY SUPPORTED 1 RESIDENT NODE PER MFRAG
			// DIFFERENT POSSIBILITIES:
			// IF BOTH OF THE CONTEXT NODE ARGUMENTS ARE ARGUMENTS FOR THE
			// RESDIENT NODE OF THE MFRAG
			String s0;
			String s1;
			if (rnArgOVList.contains(cnargOVList.get(0)) && rnArgOVList.contains(cnargOVList.get(1))) {
				// ADD TO SPARQLQUERY WHERE: "CN_OVARG0_RESULT_IRI CN_NAME
				// CN_OVARG1_RESULT_IRI"
				s0 = "ns:" + iriToName(args.get(cnargOVList.get(0)));
				s1 = "ns:" + iriToName(args.get(cnargOVList.get(1)));
			}
			// ELSE IF ARG0 IS PRESENT AS ARGUMENT AND ARG1 AS ORDINARY
			// VARIABLE OF THE MFRAG
			else if (rnArgOVList.contains(cnargOVList.get(0)) && ovList.contains(cnargOVList.get(1))) {
				// ADD TO SPARQLQUERY WHERE: "CN_OVARG0_RESULT_IRI CN:NAME
				// OV_NAME"
				s0 = "ns:" + iriToName(args.get(cnargOVList.get(0)));
				s1 = ovSPARQLvars.get(cnargOVList.get(1));
			}
			// ELSE IF ARG1 IS PRESENT AS ARGUMENT AND ARG0 AS ORDINARY
			// VARIABLE OF THE MFRAG
			else if (rnArgOVList.contains(cnargOVList.get(1)) && ovList.contains(cnargOVList.get(0))) {
				// ADD TO SPARQLQUERY WHERE: "OV_NAME CN_NAME
				// CN_OVARG1_RESULT_IRI"
				s0 = ovSPARQLvars.get(cnargOVList.get(0));
				s1 = "ns:" + iriToName(args.get(cnargOVList.get(1)));
			}
			// ELSE IF BOTH ARGUMENTS ARE PRESENTS AS ORDINARY VARIABLES
			else if (ovList.contains(cnargOVList.get(0)) && ovList.contains(cnargOVList.get(1))) {
				s0 = ovSPARQLvars.get(cnargOVList.get(0));
				s1 = ovSPARQLvars.get(cnargOVList.get(1));
			} else {
				LOG.println("CAQUERY: *****FOUND A CONTEXT NODE ARGUMENT THAT IS NOT PRESENT AS ORDINARY VARIABLE");
				break;
			}
			// TODO TEST IF THE CONTEXT NODE PROPERTY CAN BE APPLIED IN
			// THIS WAY (DOMAIN, RANGE, FUNCTIONAL, SYMMETRIC, RELFEXIVE ETC...)
			sqb.addTripleToWhere(s0, "ns:" + cnName, s1);
		}
		// AT THIS POINT SQB INSTANCE HAVE A QUERY SUCH THAT SELECTS THE
		// ONTOLOGY INDIVIDUALS OR VALUES
		// THAT REPRESENT THE ENTITY INSTANCES OR OVs THAT CURRENTLY APPLY WITH
		// THE
		// MFRAG CONTEXT NODES CONSTRAINTS

		// EXECUTE OV AND CN QUERY AND PROCESS RESULTS
		// CREATE A NEW MAP FOR LOCAL RESOLVED OVs
		HashMap<OrdinaryVariable, String> localResolvedOV = new HashMap<>();
		// IF QUERY IS NOT EMPTY
		if (sqb != null && sqb.getQuery() != null) {
			// EXECUTE QUERY WITH ALL OV AND CN ON THE MFRAG
			LOG.println("CAQUERY: *****EXECUTING QUERY WITH ALL OV AND CN ON MFRAG " + mfrag);
			List<QuerySolution> qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// FOR EACH QUERY SOLUTION -->(THERE WILL BE AN INSTANCE OF THE
			// MFRAG)<--
			for (QuerySolution qs : qsl) {
				LOG.println("CAQUERY: *****QUERY SOLUTION: " + qs);
				LOG.println("CAQUERY: *****ADDING ORDINARY VARIABLES RESULTS TO FINDINGS FILE.");
				// UPDATE GLOBAL AND LOCAL RESOLVED OVS FOR EACH ONE IN ovList
				for (OrdinaryVariable ov : ovSPARQLvars.keySet()) {
					// GET RESULT FOR THAT OV
					String s = qs.get(ov.getName()).toString();
					// IF globalResolvedOV DOESNT ALREADY CONTAINS THE ENTRY
					// (OV -> RESULT ENTITY IRI)
					if (!this.globalResolvedOVs.contains(ov, s)) {
						// ADD THE ENTRY AS RESOLVED
						this.globalResolvedOVs.put(ov, s);
						// GET ENTITY NAME FROM IRI
						String eName = iriToName(s);
						// ADD THE ENTITY INSTANCE DECLARATION AS A FINDING ON
						// THE KB AS, FOR EXAMPLE:"(ASSERT
						// (AUTONOMOUSROBOT_LABEL AUTROB1))"
						LOG.println("CAQUERY: ADDED TO KB: " + "ASSERT (" + ov.getValueType().toString() + LABEL + eName
								+ ")");
						ObjectEntityInstance oei = mebn.getObjectEntityContainer()
								.getObjectEntityByName(ov.getValueType().toString()).addInstance(eName);
						if (oei != null) {
							this.kb.insertEntityInstance(oei);
						}
					}
					// PUT RESOLVED URIs ON LOCAL RESOLVED OVs MAP
					if (ovList.contains(ov)) {
						localResolvedOV.put(ov, s);
					}
				}
				// ADD args TO LOCAL RESOLVED OVs
				localResolvedOV.putAll(args);
				LOG.println("CAQUERY: *****ADDING CONTEXT NODES RESULTS TO FINDINGS FILE FOR MFRAG " + mfrag);
				// FOR EACH CN IN cnList (LOCAL TO FUNCTION)
				for (ContextNode cn : cnList) { // (IF WE GOT THE ENTITIES IN
												// THE RESULT, THAT ENTITIES
												// COMPLY WITH THE CONTEXT NODES
												// (WHERE CLAUSES))
					// GET CN CORRESPONDING OWL PROPERTY NAME
					String cnName = labelToName(cn.getLabel());
					// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA
					// "CN_NAME(CNARGOV0)=CNARGOV1"
					// GET CN FORMULA ARGUMENTS ORDINARY VARIABLES
					List<OrdinaryVariable> cnargOVList = new ArrayList<>();
					Iterator<OrdinaryVariable> it = cn.getFormulaTree().getChildren().get(0).getVariableList()
							.iterator();
					cnargOVList.add(it.next());
					it = cn.getFormulaTree().getChildren().get(1).getVariableList().iterator();
					cnargOVList.add(it.next());
					// STORE RV FINDINGS ON KB (ONLY IF THE RESIDENT NODE AND
					// ENTITY INSTANCES EXISTS ON THE MEBN AND THE KB
					// RESPECTIVELY)
					String s0 = localResolvedOV.get(cnargOVList.get(0));
					String s1 = localResolvedOV.get(cnargOVList.get(1));
					ObjectEntityInstance[] aux = new ObjectEntityInstance[1];
					aux[0] = mebn.getObjectEntityContainer().getEntityInstanceByName(iriToName(s0));
					ResidentNode rn = mebn.getDomainResidentNode(cnName);
					Entity e = mebn.getObjectEntityContainer().getEntityInstanceByName(iriToName(s1));
					if (e != null && rn != null && aux[0] != null) {
						this.kb.insertRandomVariableFinding(new RandomVariableFinding(rn, aux, e, mebn));
						LOG.println("CAQUERY: STORED ON THE KB: " + "ASSERT (= (" + cnName + " " + iriToName(s0) + ") "
								+ iriToName(s1) + ")");
					}
				}
				// PROCESS INPUT NODES
				LOG.println("CAQUERY: **********PROCESSING INPUT NODES FOR MFRAG " + mfrag);
				processInputNodes(mfrag, localResolvedOV);
			}
		} else {
			// IF QUERY IS EMPTY, PROCESS INPUT NODES WITHOUT DOING ANYTHING
			// ELSE
			LOG.println("CAQUERY: **********PROCESSING INPUT NODES FOR MFRAG " + mfrag);
			processInputNodes(mfrag, localResolvedOV);
		}
	}

	/**
	 * 
	 * @param mfrag
	 * @param localResolvedOV
	 * @throws CategoricalStateDoesNotExistException
	 * @throws TypeException
	 */
	private void processInputNodes(MFrag mfrag, Map<OrdinaryVariable, String> localResolvedOV)
			throws CategoricalStateDoesNotExistException, TypeException {
		List<InputNode> branchNodesList = new ArrayList<>();
		List<InputNode> leafNodesList = new ArrayList<>();
		// FOR EACH INPUT NODE ON THE MFRAG
		for (InputNode in : mfrag.getInputNodeList()) {
			// IF THE RESIDENT MFRAG FOR THIS INPUT NODE HAVE ANY INPUT NODES
			if (!in.getResidentNodePointer().getResidentNode().getMFrag().getInputNodeList().isEmpty()) {
				// STORE INPUT NODE IN branchNodesList
				branchNodesList.add(in);
			} else {
				// ELSE STORE IT IN leafNodesList
				leafNodesList.add(in);
			}
		}
		// PROCESS LEAF NODES
		processLeafNodes(leafNodesList, mfrag);
		// PROCESS BRANCH NODES
		processBranchNodes(branchNodesList, mfrag, localResolvedOV);
		// CLEAR branchNodesList
		branchNodesList.clear();
	}

	/**
	 * 
	 * @param branchNodesList
	 * @param mfrag
	 * @param localResolvedOVs
	 * @throws CategoricalStateDoesNotExistException
	 * @throws TypeException
	 */
	private void processBranchNodes(List<InputNode> branchNodesList, MFrag mfrag,
			Map<OrdinaryVariable, String> localResolvedOVs)
			throws CategoricalStateDoesNotExistException, TypeException {
		// FOR EACH BRANCH NODE
		for (InputNode in : branchNodesList) {
			// IF BRANCH NODE NAME IS NOT THE SAME AS THE CURRENT MFRAG RESIDENT
			// NODE
			// (PREVENT ENDLESS LOOPS)
			if (in.getResidentNodePointer().getResidentNode().getName() != mfrag.getResidentNodeList().get(0)
					.getName()) {
				// GET BRANCH NODE MFRAG
				MFrag bnMFrag = in.getResidentNodePointer().getResidentNode().getMFrag();
				// GET BRANCH NODE ARGS
				Map<OrdinaryVariable, String> bnArgs = new HashMap<>();
				boolean error = false;
				for (int i = 0; i < in.getResidentNodePointer().getNumberArguments(); i++) {
					// (MUST BE PRESENT ON localResolvedOV MAP)
					if (localResolvedOVs.containsKey(in.getResidentNodePointer().getArgument(i))) {
						bnArgs.put(in.getArgumentList().get(i).getOVariable(),
								localResolvedOVs.get(in.getResidentNodePointer().getArgument(i)));
					} else {
						error = true;
						// ERROR BRANCH NODE RESIDENT NODE ARGUMENT NOT
						// PRESENT ON
						// localResolvedOV, BRANCH NODE WILL NOT BE PROCESSED
					}
				}
				if (!error) {
					LOG.println("\nCAQUERY: *************************EXECUTING RECURSIVE FUNCTION ON " + bnMFrag
							+ ", FROM NOT LEAF INPUT NODES PROCESSING IN " + mfrag + "");
					// CALL RECURSIVE FUNCTION ON THE INPUT NODE MFRAG AND WITH
					// THE
					// INPUT NODE ARGS
					recursiveFunction(bnMFrag, bnArgs);
				}
			}
		}
	}

	/**
	 * 
	 * @param leafNodesList
	 * @param mfrag
	 * @throws CategoricalStateDoesNotExistException
	 */
	private void processLeafNodes(List<InputNode> leafNodesList, MFrag mfrag)
			throws CategoricalStateDoesNotExistException {
		/**
		 * Helps to build SPARQL queries
		 */
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		for (InputNode in : leafNodesList) {
			ResidentNode irn = in.getResidentNodePointer().getResidentNode();
			String irnName = irn.getName();
			List<Argument> irnArgumentList = irn.getArgumentList();
			// TODO ONLY SUPPORTS LEAF RESIDENT NODES WITH JUST 1 ARGUMENT (same
			// as the only one OV)
			OrdinaryVariable arg0OV = irnArgumentList.get(0).getOVariable();
			// IF INPUT NODE NAME IS NOT THE SAME AS THE CURRENT RESIDENT NODE
			// (PREVENT ENDLESS LOOPS)
			// TODO ONLY TESTS THE NAME OF THE FIRST POSSIBLE VALUE. NEEDS
			// SUPPORT MFRAGS WITH MORE RN
			if (!irnName.equals(mfrag.getResidentNodeList().get(0).getName())) {
				int i = 0;
				// BUILD QUERY
				// FOR EACH MAPPING INTO RESOLVED_OV (EACH INDIVIDUAL FOUND ON
				// THE
				// ONTOLOGY FOR THAT ORDINARY VARIABLE)
				for (String s : this.globalResolvedOVs.get(arg0OV)) {
					i++;
					String irnQueryVar = "?" + irnName + i;
					sqb.addToSelect(irnQueryVar);
					// IF RN POSSIBLE VALUE TYPE IS NOT CATEGORICAL
					if (!irn.getPossibleValueLinkList().get(0).getState().getType().getName().equals(CATLABEL)) {
						// ADD TRIPLE SPECIFYING THE EXPECTED RESULT INDIVIDUAL
						// CLASS
						sqb.addTripleToWhere(irnQueryVar, RDFTYPE,
								irn.getPossibleValueLinkList().get(0).getState().getType().getName());
					}
					sqb.addTripleToWhere("ns:" + iriToName(s), "ns:" + irnName, irnQueryVar);
				}
				// EXECUTE QUERY
				LOG.println("CAQUERY: *****EXECUTING QUERY FOR LEAF INPUT NODE " + in + " IN MFRAG " + mfrag);
				List<QuerySolution> lqs2 = this.sparqlQuery.executeArgQuery(sqb.getQuery());
				QuerySolution qs2;
				// PROCESS QUERY SOLUTION AND FILL THE FINDINGS FILE
				if (!lqs2.isEmpty()) {
					// TODO LEAF NODES ONLY ADMIT ONE RESULT (FUNCTIONAL OBJECT
					// OR DATA PROPERTIES)
					qs2 = lqs2.get(0);
					int j = 0;
					for (String s : this.globalResolvedOVs.get(irn.getArgumentList().get(0).getOVariable())) {
						j++;
						String irnQueryVar = "?" + irnName + j;
						String arg = iriToName(s);
						String res = stringValueIRItoLabel(qs2.get(irnQueryVar).toString());
						// STORE RV FINDINGS ON KB (ONLY IF THE RESIDENT NODE
						// AND
						// ENTITY INSTANCES EXISTS ON THE MEBN AND THE KB
						// RESPECTIVELY)
						ObjectEntityInstance[] aux = new ObjectEntityInstance[1];
						aux[0] = mebn.getObjectEntityContainer().getEntityInstanceByName(arg);
						ResidentNode rn = mebn.getDomainResidentNode(irnName);
						CategoricalStateEntity cse = null;
						cse = mebn.getCategoricalStatesEntityContainer().getCategoricalState(res);
						if (rn != null && aux != null && cse != null) {
							this.kb.insertRandomVariableFinding(new RandomVariableFinding(rn, aux, cse, mebn));
							LOG.println("CAQUERY: STORED ON THE KB: (ASSERT (= (" + irnName + " " + arg + ") " + res
									+ "))");
						}
					}
				} else {
					LOG.println("CAQUERY: *****LEAF NODE " + irnName + " DIDNT PRODUCE ANY RESULT.");
				}
			}
		}
	}

	// AUX FUNCTIONS
	/**
	 * 
	 * @param iri
	 * @return
	 */
	public static String stringValueIRItoLabel(String iri) {
		return (iri.indexOf("^^") >= 0) ? iri.substring(0, iri.indexOf("^^")) : iriToName(iri);
	}

	// TODO ONLY SUPPORTS CONTEXT NODES WITH FORMULA
	// "CN_NAME(CNARGOV0)=CNARGOV1"
	/**
	 * 
	 * @param label
	 * @return
	 */
	public static String labelToName(String label) {
		String name = label.substring(2, label.length() - 2);
		return name.substring(0, name.indexOf("("));
	}

	/**
	 * 
	 * @param name
	 * @return
	 */
	public String nameToIRI(String name) {
		return this.ontNamespace + name;
	}

	/**
	 * 
	 * @param iri
	 * @return
	 */
	public static String iriToName(String iri) {
		return (iri.lastIndexOf("/") >= 0) ? iri.substring(iri.lastIndexOf("/") + 1) : iri;
	}

	/**
	 * 
	 * @param residentNodesList
	 */
	public static void printAll(List<ResidentNode> residentNodesList) {
		List<OrdinaryVariable> ovList = new ArrayList<>();
		List<ContextNode> cnList = new ArrayList<>();
		Map<String, String> argMap = new TreeMap<>();
		Map<String, String> pvMap = new TreeMap<>();

		Iterator<ResidentNode> it = residentNodesList.iterator();
		// FOR EACH RESIDENT NODE
		while (it.hasNext()) {
			ResidentNode x = it.next();
			// PRINT NAME
			LOG.println("NAME: " + x.getName());
			// PRINT LABEL
			LOG.println("LABEL: " + x.getLabel());
			// PRINT MFRAG INPUT NODES
			LOG.println("INPUT NODES: " + x.getMFrag().getInputNodeList().toString());
			// GET MFRAG´s ORDINARY VARIABLES
			List<OrdinaryVariable> auxOVList = x.getMFrag().getOrdinaryVariableList();
			// ADD NEW ORDINARY VARIABLES TO GLOBAL LIST
			if (!ovList.containsAll(auxOVList)) {
				for (OrdinaryVariable ov : auxOVList) {
					if (!ovList.contains(ov)) {
						ovList.add(ov);
					}
				}
			}
			Iterator<OrdinaryVariable> it2 = auxOVList.iterator();
			int i = 1;
			while (it2.hasNext()) {
				OrdinaryVariable ov = it2.next();
				LOG.println("MFRAG OV " + i + TYPE + ov.getValueType());
				LOG.println("MFRAG OV " + i + " NAME: " + ov.getName());
				i++;
			}
			// GET MFRAG´S CONTEXT NODES
			List<ContextNode> auxCNList = x.getMFrag().getContextNodeList();
			// ADD THE NEW CONTEXT NODES TO THE GLOBAL LIST
			if (!cnList.containsAll(auxCNList)) {
				for (ContextNode cn : auxCNList) {
					if (!cnList.contains(cn)) {
						cnList.addAll(auxCNList);
					}
				}
			}
			Iterator<ContextNode> it21 = auxCNList.iterator();
			int i1 = 1;
			while (it21.hasNext()) {
				ContextNode cn = it21.next();
				String label = cn.getLabel();
				String name = label.substring(2, label.length() - 2);
				name = name.substring(0, name.indexOf("("));
				LOG.println("MFRAG CN " + i1 + " LABEL: " + label);
				List<OrdinaryVariable> list = new ArrayList<>();
				for (Argument a : cn.getArgumentList()) {
					list.add(a.getOVariable());
				}
				// List<OrdinaryVariable> list =
				// cn.getOrdinaryVariablesInArgument();
				LOG.println("MFRAG CN " + i1 + " OV 0 IN ARGUMENT: " + list.get(0));
				LOG.println("MFRAG CN " + i1 + " NAME: " + name);
				LOG.println("MFRAG CN " + i1 + " OV 1 IN ARGUMENT: " + list.get(1));
				i1++;
			}
			// PRINT ARGUMENTS NAMES AND TYPES
			Iterator<Argument> it3 = x.getArgumentList().iterator();
			int i2 = 1;
			while (it3.hasNext()) {
				Argument z = it3.next();
				String name = z.getOVariable().getName();
				LOG.println("ARG " + i2 + ": " + name);
				String type = z.getOVariable().getValueType().getName();
				LOG.println("ARG " + i2 + type + type);
				if (!CATLABEL.equals(type) && !argMap.containsKey(name)) {
					argMap.put(name, type);
				}
				i2++;
			}
			// PRINT POSSIBLE VALUES
			Iterator<StateLink> it4 = x.getPossibleValueLinkList().iterator();
			int i3 = 1;
			while (it4.hasNext()) {
				StateLink y = it4.next();
				String name = y.getState().getName();
				LOG.println("POSSIBLE VALUE " + i3 + ": " + name);
				String type = y.getState().getType().getName();
				LOG.println("POSSIBLE VALUE " + i3 + type + type);
				if (!CATLABEL.equals(type) && !pvMap.containsKey(name)) {
					pvMap.put(name, type);
				}
				// LOG.println("POSSIBLE VALUE "+i3+": "+name);
				i3++;
			}
			LOG.println("\n\n");
		}
	}

}
