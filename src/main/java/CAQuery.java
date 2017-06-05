package main.java;

import java.io.File;
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

import main.java.helpers.PrintWriterHelper;
import main.java.jenafacade.SPARQLQuery;
import main.java.jenafacade.SPARQLQueryBuilder;
import unbbayes.io.mebn.UbfIO;
import unbbayes.prs.mebn.Argument;
import unbbayes.prs.mebn.ContextNode;
import unbbayes.prs.mebn.InputNode;
import unbbayes.prs.mebn.MFrag;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.OrdinaryVariable;
import unbbayes.prs.mebn.ResidentNode;
import unbbayes.prs.mebn.entity.StateLink;

public class CAQuery {

	private static PrintStream so = System.out;
	private static String catLabel = "CategoryLabel";
	private static String label = "_LABEL ";
	private static String type = " TYPE: ";
	private static String rdfType = "rdf:type";
	private String ontNamespace;
	private SPARQLQuery sparqlQuery;
	private MultiMap<OrdinaryVariable, String> globalResolvedOVs;

	public CAQuery(String ontNamespace, String ontologyFile) {
		this.ontNamespace = ontNamespace;
		this.sparqlQuery = new SPARQLQuery(ontNamespace, ontologyFile);
	}

	/*
	 * type: 0 if it is an environment CA query, 1 if it refers to an indivifual
	 * arg1: type 0: [gpsLatitude, gpsLongitude, gpsAltitude], type1:
	 * [individualName, individualClass] mfragName ovName
	 */
	public String fillFindings(String findingsFile, String mebnFile, int type, String[] arg1, String mfragName,
			String ovName) throws IOException {
		// initialize variables
		this.globalResolvedOVs = new MultiMap<>();
		Map<OrdinaryVariable, String> args = new HashMap<>();
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		List<QuerySolution> qsl;
		String sol = null;
		PrintWriterHelper pw = new PrintWriterHelper(findingsFile, "UTF-8");
		// load ubf/owl
		UbfIO ubf = UbfIO.getInstance();
		// load MeBN and MFrag
		MultiEntityBayesianNetwork mebn = ubf.loadMebn(new File(mebnFile));
		MFrag mfrag = mebn.getMFragByName(mfragName + "_MFrag");
		// switch type
		if (type == 0) { // ENVIRONMENTAL
			// EXECUTE QUERY IN ORDER TO KNOW IF THERE IS A GPSPoasition ENTITY
			// IN THE ONTOLOGY
			// WITH THE SAME COORDINATES SPECIFIED AS ARGUMENTS
			sqb.addToSelect("?" + ovName);
			String xsdFloat = "\"^^xsd:float";
			sqb.addTripleToWhere("?" + ovName, "ns:gpsLatitude", "\"" + arg1[0] + xsdFloat);
			sqb.addTripleToWhere("?" + ovName, "ns:gpsLongitude", "\"" + arg1[1] + xsdFloat);
			sqb.addTripleToWhere("?" + ovName, "ns:gpsAltitude", "\"" + arg1[2] + xsdFloat);
			qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// IF THE POSITION EXISTS (THEN gpsPos OV IN MTHEORY REFERS TO THAT
			// ONTOLOGY INDIVIDUAL)
			if (!qsl.isEmpty()) {
				// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY
				// WITH
				String solutionIRI = qsl.get(0).get("?" + ovName).toString();
				// GET INDIVIDUAL NAME
				sol = CAQuery.iriToName(solutionIRI);
				// STORE ON LOCAL ARGS AND GLOBAL ORDINARY VARIABLES MAPS
				args.put(mfrag.getOrdinaryVariableByName(ovName), solutionIRI);
				this.globalResolvedOVs.put(mfrag.getOrdinaryVariableByName(ovName), solutionIRI);
				// ADD ENTITY INSTANCE TO THE FINDINGS FILE:"(ASSERT
				// (GPSPosition_LABEL gpsPos))"
				pw.println("(ASSERT (GPSPosition_LABEL " + sol + "))");
				// CALL RECURSIVE FUNCTION ON SOUNDVELOCITY_MFRAG
				so.println("\nCAQUERY: **************************EXECUTING RECURSIVE FUNCTION ON MFRAG: " + mfrag);
				this.recursiveFunction(mfrag, pw, args);
			} else {
				so.println(
						"\nCAQUERY: THE GPS POSITION [" + arg1[0] + ", " + arg1[1] + ", " + arg1[2] + "] WASNT FOUND.");
			}
			pw.close();
		} else if (type == 1) { // INDIVIDUAL
			// EXECUTE QUERY IN ORDER TO KNOW IF THE INDIVIDUAL ON THE ARGUMENT
			// EXISTS IN THE ONTOLOGY
			sqb.addToSelect("?" + ovName);
			sqb.addTripleToWhere("?" + ovName, rdfType, "ns:" + arg1[1]);
			qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY WITH
			// THE DESIRED INDIVIDUAL(INDEX 0)
			String solutionIRI = qsl.get(0).get("?" + ovName).toString();
			// IF THE INDIVIDUAL EXISTS AND IT HAS THE EXPECTED IRI
			if (solutionIRI.equals(ontNamespace + arg1[0])) {
				// STORE IT ON ORDINARY VARIABLES MAPS
				args.put(mfrag.getOrdinaryVariableByName(ovName), solutionIRI);
				this.globalResolvedOVs.put(mfrag.getOrdinaryVariableByName(ovName), solutionIRI);
				// ADD IT TO THE FINDINGS FILE AS, FOR EXAMPLE:"(ASSERT
				// (AUTONOMOUSROBOT_LABEL AUTROB1))"
				pw.println("(ASSERT (" + arg1[1].toUpperCase() + label + arg1[0] + "))");
				// CALL RECURSIVE FUNCTION ON THE DESIRED MFRAG
				so.println("\nCAQUERY: **************************EXECUTING RECURSIVE FUNCTION ON MFRAG: :" + mfrag);
				this.recursiveFunction(mfrag, pw, args);
			} else {
				so.println("CAQUERY: THE ARGUMENT INDIVIDUAL WASNT FOUND.");
			}
			pw.close();
			sol = null;
		}
		return sol;
	}

	public void recursiveFunction(MFrag mfrag, PrintWriterHelper pw, Map<OrdinaryVariable, String> args) {
		// INITIALIZE VARIABLES
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		List<QuerySolution> qsl;
		Map<OrdinaryVariable, String> ovSPARQLvars = new HashMap<>();
		List<OrdinaryVariable> ovList = mfrag.getOrdinaryVariableList();
		List<ContextNode> cnList = mfrag.getContextNodeList();
		List<OrdinaryVariable> rnArgOVList = new ArrayList<>();
		// TODO ONLY SUPPORT 1 RESIDENT NODE PER MFRAG (WITH 1 ARGUMENT)
		for (Argument a : mfrag.getResidentNodeList().get(0).getArgumentList()) {
			rnArgOVList.add(a.getOVariable());
		}
		so.println("CAQUERY: **********PROCESSING OV AND CONTEXT NODES FOR MFRAG " + mfrag);
		// BUILD OV AND CN QUERY
		// FOR EACH ORDINARY VARIABLE ON THE MFRAG
		for (OrdinaryVariable ov : ovList) {
			// IF IT IS NOT PRESENT AS RESIDENT´S NODE ARGUMENT
			if (!rnArgOVList.contains(ov)) {
				// ADD TO SPARQLQUERY-SELECT: OV_NAME
				sqb.addToSelect("?" + ov.getName());
				// ADD TO SPARQLQUERY-WHERE: OV_TYPE
				sqb.addTripleToWhere("?" + ov.getName(), rdfType, "ns:" + ov.getValueType());
				// ADD THE ORDINARY VARIABLEV TO local resolved OVs
				ovSPARQLvars.put(ov, "?" + ov.getName());
			}
		}
		// FOR EACH CONTEXT NODE ON THE MFRAG
		// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA
		// "CN_NAME(CNARGOV0)=CNARGOV1"
		for (ContextNode cn : cnList) {
			// GET CN CORRESPONDING OWL PROPERTY NAME
			String name = labelToName(cn.getLabel());
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
				so.println("CAQUERY: *****FOUND A CONTEXT NODE ARGUMENT THAT IS NOT PRESENT AS ORDINARY VARIABLE");
				break;
			}
			// TODO TEST IF THE CONTEXT NODE PROPERTY CAN BE APPLIED IN
			// THIS WAY (DOMAIN, RANGE, FUNCTIONAL, SYMMETRIC, RELFEXIVE ETC...)
			sqb.addTripleToWhere(s0, "ns:" + name, s1);
		}
		// AT THIS POINT SQB INSTANCE HAVE A QUERY SUCH THAT SELECTS THE
		// ONTOLOGY INDIVIDUALS
		// THAT REPRESENT THE MFRAG OV´S INSTANCES THAT CURRENTLY APPLY WITH THE
		// MFRAG CONTEXT NODES CONSTRAINTS

		// EXECUTE OV AND CN QUERY AND PROCESS RESULTS
		// CREATE A NEW MAP FOR LOCAL RESOLVED OVs
		HashMap<OrdinaryVariable, String> localResolvedOV = new HashMap<>();
		// IF QUERY IS NOT EMPTY
		if (sqb != null && sqb.getQuery() != null) {
			// EXECUTE QUERY WITH ALL OV AND CN ON THE MFRAG
			so.println("CAQUERY: *****EXECUTING QUERY WITH ALL OV AND CN ON MFRAG " + mfrag);
			qsl = this.sparqlQuery.executeArgQuery(sqb.getQuery());
			// FOR EACH QUERY SOLUTION -->(THERE WILL BE AN INSTANCE OF THE
			// MFRAG)<--
			for (QuerySolution qs : qsl) {
				so.println("CAQUERY: *****QUERY SOLUTION: " + qs);
				so.println("CAQUERY: *****ADDING ORDINARY VARIABLES RESULTS TO FINDINGS FILE.");
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
						String name = iriToName(s);
						// ADD LINE TO FINDINGS FILE
						so.println("CAQUERY: ADDED TO FINDINGS FILE: " + "ASSERT (" + ov.getValueType().toString()
								+ label + name + ")");
						pw.println("(ASSERT (" + ov.getValueType().toString() + label + name + "))");
					}
					// PUT RESOLVED URIs ON LOCAL RESOLVED OVs MAP
					if (ovList.contains(ov)) {
						localResolvedOV.put(ov, s);
					}
				}
				// ADD args TO LOCAL RESOLVED OVs
				localResolvedOV.putAll(args);
				so.println("CAQUERY: *****ADDING CONTEXT NODES RESULTS TO FINDINGS FILE FOR MFRAG " + mfrag);
				// FOR EACH CN IN cnList (LOCAL TO FUNCTION)
				for (ContextNode cn : cnList) { // (IF WE GOT THE ENTITIES IN
												// THE RESULT, THAT ENTITIES
												// COMPLY WITH THE CONTEXT NODES
												// (WHERE CLAUSES))
					// GET CN CORRESPONDING OWL PROPERTY NAME
					String name = labelToName(cn.getLabel());
					// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA
					// "CN_NAME(CNARGOV0)=CNARGOV1"
					// GET CN FORMULA ARGUMENTS ORDINARY VARIABLES
					List<OrdinaryVariable> cnargOVList = new ArrayList<>();
					Iterator<OrdinaryVariable> it = cn.getFormulaTree().getChildren().get(0).getVariableList()
							.iterator();
					cnargOVList.add(it.next());
					it = cn.getFormulaTree().getChildren().get(1).getVariableList().iterator();
					cnargOVList.add(it.next());
					// ADD TO FINDINGS FILE
					String s0 = localResolvedOV.get(cnargOVList.get(0));
					String s1 = localResolvedOV.get(cnargOVList.get(1));
					so.println("CAQUERY: ADDED TO FINDINGS FILE: " + "ASSERT (= (" + name + " " + iriToName(s0) + ") "
							+ iriToName(s1) + ")");
					pw.println("(ASSERT (= (" + name + " " + iriToName(s0) + ") " + iriToName(s1) + "))");
				}
				// PROCESS INPUT NODES
				so.println("CAQUERY: **********PROCESSING INPUT NODES FOR MFRAG " + mfrag);
				processInputNodes(mfrag, pw, localResolvedOV);
			}
		} else {
			// IF QUERY IS EMPTY, PROCESS INPUT NODES WITHOUT DOING ANYTHING
			// ELSE
			so.println("CAQUERY: **********PROCESSING INPUT NODES FOR MFRAG " + mfrag);
			processInputNodes(mfrag, pw, localResolvedOV);
		}
	}

	private void processInputNodes(MFrag mfrag, PrintWriterHelper pw, Map<OrdinaryVariable, String> localResolvedOV) {
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
		processLeafNodes(leafNodesList, mfrag, pw);
		// PROCESS BRANCH NODES
		processBranchNodes(branchNodesList, mfrag, pw, localResolvedOV);
		// CLEAR branchNodesList
		branchNodesList.clear();
	}

	private void processBranchNodes(List<InputNode> branchNodesList, MFrag mfrag, PrintWriterHelper pw,
			Map<OrdinaryVariable, String> localResolvedOVs) {
		// FOR EACH BRANCH NODE
		for (InputNode in : branchNodesList) {
			// IF BRANCH NODE NAME IS NOT THE SAME AS THE CURRENT MFRAG RESIDENT NODE
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
						// TODO ERROR BRANCH NODE RESIDENT NODE ARGUMENT NOT
						// PRESENT ON
						// localResolvedOV, BRANCH NODE WILL NOT BE PROCESSED
					}
				}
				if (!error) {
					so.println("\nCAQUERY: *************************EXECUTING RECURSIVE FUNCTION ON " + bnMFrag
							+ ", FROM NOT LEAF INPUT NODES PROCESSING IN " + mfrag + "");
					// CALL RECURSIVE FUNCTION ON THE INPUT NODE MFRAG AND WITH
					// THE
					// INPUT NODE ARGS
					recursiveFunction(bnMFrag, pw, bnArgs);
				}
			}
		}
	}

	private void processLeafNodes(List<InputNode> leafNodesList, MFrag mfrag, PrintWriterHelper pw) {
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
			// TODO ONLY TESTS THE TYPE OF THE FIRST POSSIBLE VALUE. NEEDS
			// SUPPORT FOR RESIDENT NODES WITH MANY POSSIBLE VALUE TYPES
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
					if (!irn.getPossibleValueLinkList().get(0).getState().getType().getName().equals(catLabel)) {
						// ADD TRIPLE SPECIFYING THE EXPECTED RESULT INDIVIDUAL
						// CLASS
						sqb.addTripleToWhere(irnQueryVar, rdfType,
								irn.getPossibleValueLinkList().get(0).getState().getType().getName());
					}
					sqb.addTripleToWhere("ns:" + iriToName(s), "ns:" + irnName, irnQueryVar);
				}
				// EXECUTE QUERY
				so.println("CAQUERY: *****EXECUTING QUERY FOR LEAF INPUT NODE " + in + " IN MFRAG " + mfrag);
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
						// ADD TO THE FINDINGS FILE: (ASSERT (= ("RN_NAME"
						// "RESOLVED_OV_ARG") "QUERYRESULT")
						so.println("CAQUERY: ADDED TO THE FINDINGS FILE: (ASSERT (= (" + irnName + " " + arg + ") "
								+ res + "))");
						pw.println("(ASSERT (= (" + irnName + " " + arg + ") " + res + "))");
					}
				} else {
					so.println("CAQUERY: *****LEAF NODE " + irnName + " DIDNT PRODUCE ANY RESULT.");
				}
			}
		}
	}

	// AUX FUNCTIONS
	private static String stringValueIRItoLabel(String iri) {
		return (iri.indexOf("^^") >= 0) ? iri.substring(0, iri.indexOf("^^")) : iriToName(iri);
	}

	// TODO ONLY SUPPORTS CONTEXT NODES WITH FORMULA
	// "CN_NAME(CNARGOV0)=CNARGOV1"
	private static String labelToName(String label) {
		String name = label.substring(2, label.length() - 2);
		return name.substring(0, name.indexOf("("));
	}

	@SuppressWarnings("unused")
	private String nameToIRI(String name) {
		return this.ontNamespace + name;
	}

	public static String iriToName(String iri) {
		return (iri.lastIndexOf("/") >= 0) ? iri.substring(iri.lastIndexOf("/") + 1) : iri;
	}

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
			so.println("NAME: " + x.getName());
			// PRINT LABEL
			so.println("LABEL: " + x.getLabel());
			// PRINT MFRAG INPUT NODES
			so.println("INPUT NODES: " + x.getMFrag().getInputNodeList().toString());
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
				so.println("MFRAG OV " + i + type + ov.getValueType());
				so.println("MFRAG OV " + i + " NAME: " + ov.getName());
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
				so.println("MFRAG CN " + i1 + " LABEL: " + label);
				List<OrdinaryVariable> list = new ArrayList<>();
				for (Argument a : cn.getArgumentList()) {
					list.add(a.getOVariable());
				}
				// List<OrdinaryVariable> list =
				// cn.getOrdinaryVariablesInArgument();
				so.println("MFRAG CN " + i1 + " OV 0 IN ARGUMENT: " + list.get(0));
				so.println("MFRAG CN " + i1 + " NAME: " + name);
				so.println("MFRAG CN " + i1 + " OV 1 IN ARGUMENT: " + list.get(1));
				i1++;
			}
			// PRINT ARGUMENTS NAMES AND TYPES
			Iterator<Argument> it3 = x.getArgumentList().iterator();
			int i2 = 1;
			while (it3.hasNext()) {
				Argument z = it3.next();
				String name = z.getOVariable().getName();
				so.println("ARG " + i2 + ": " + name);
				String type = z.getOVariable().getValueType().getName();
				so.println("ARG " + i2 + type + type);
				if (!catLabel.equals(type) && !argMap.containsKey(name)) {
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
				so.println("POSSIBLE VALUE " + i3 + ": " + name);
				String type = y.getState().getType().getName();
				so.println("POSSIBLE VALUE " + i3 + type + type);
				if (!catLabel.equals(type) && !pvMap.containsKey(name)) {
					pvMap.put(name, type);
				}
				// so.println("POSSIBLE VALUE "+i3+": "+name);
				i3++;
			}
			so.println("\n\n");
		}
	}

}
