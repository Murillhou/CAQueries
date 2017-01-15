package main.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.semanticweb.owlapi.util.MultiMap;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.jena.SPARQLQuery;
import main.java.jena.SPARQLQueryBuilder;
import unbbayes.TextModeRunner;
import unbbayes.TextModeRunner.QueryNodeNameAndArguments;
import unbbayes.io.mebn.UbfIO;
import unbbayes.prs.bn.ProbabilisticNetwork;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.prs.mebn.Argument;
import unbbayes.prs.mebn.ContextNode;
import unbbayes.prs.mebn.InputNode;
import unbbayes.prs.mebn.MFrag;
import unbbayes.prs.mebn.MultiEntityBayesianNetwork;
import unbbayes.prs.mebn.OrdinaryVariable;
import unbbayes.prs.mebn.RandomVariableFinding;
import unbbayes.prs.mebn.ResidentNode;
import unbbayes.prs.mebn.entity.Entity;
import unbbayes.prs.mebn.entity.ObjectEntity;
import unbbayes.prs.mebn.entity.ObjectEntityInstance;
import unbbayes.prs.mebn.entity.StateLink;
import unbbayes.prs.mebn.entity.exception.TypeException;
import unbbayes.prs.mebn.kb.KnowledgeBase;
import unbbayes.prs.mebn.kb.powerloom.PowerLoomKB;

public class _CAQuery {

	private static TextModeRunner tmr;
	private static ProbabilisticNetwork net;
	private static KnowledgeBase kb;
	private static PrintStream so = System.out;
	public _CAQuery() {
		// TODO Auto-generated constructor stub
	}	
	
	public static void main(String[] args) {
		String mebnFile = args[0];
		String arg = args[1];		
		tmr = new TextModeRunner();
	// load ubf/owl
		UbfIO ubf = UbfIO.getInstance();
		MultiEntityBayesianNetwork mebn = null;
		try {
			mebn = ubf.loadMebn(new File(mebnFile));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	// initialize kb
		kb = PowerLoomKB.getNewInstanceKB(); 
		kb = tmr.createKnowledgeBase(kb, mebn);
	// fill findings file from OWL ontology
		String name = mebnFile.substring(mebnFile.lastIndexOf('/')+1, mebnFile.lastIndexOf('.'));
		fillFindings(name, arg, mebn, kb);
	// execute MEBN query
		QueryNodeNameAndArguments qnnaa = tmr.new QueryNodeNameAndArguments("Recoverable",arg);
		executeMEBNQuery(mebn, qnnaa);
	}	
	
	private static void fillFindings(String name, String arg, MultiEntityBayesianNetwork mebn, KnowledgeBase kb) {
	// initialize variables	
		Map<OrdinaryVariable,String> argsMap = new HashMap<OrdinaryVariable,String>();
		MultiMap<OrdinaryVariable,String> globalMap = new MultiMap<OrdinaryVariable,String>();
		// get resident nodes list
		List<ResidentNode> residentNodesList = mebn.getDomainResidentNodes();
		MFrag mfrag = mebn.getMFragByName(name+"_MFrag");
		// print all
		printAll(residentNodesList);
		// EXECUTE TYPE SEARCH QUERY IN ORDER TO KNOW IF THE OBJECT ENTITY IN THE CA_QUERY ARG EXISTS IN THE ONTOLOGY
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		// TODO ONLY SUPPORT ONE ARGUMENT ON THE 'MAIN' RESIDENT NODE
		sqb.addToSelect("?"+mfrag.getResidentNodeList().get(0).getOrdinaryVariableList().get(0));
		sqb.addTripleToWhere("?"+mfrag.getResidentNodeList().get(0).getOrdinaryVariableList().get(0), "rdf:type", "ns:"+mfrag.getResidentNodeList().get(0).getOrdinaryVariableList().get(0).getValueType().toString());
		List<QuerySolution> l = SPARQLQuery.executeArgQuery(sqb.getQuery());
		// TODO CONSIDERS THAT THE SOLUTION LIST JUST CARRIES ONE ENTRY WITH THE DESIRED ENTITY(INDEX 0) 
		QuerySolution qs = l.get(0);
		String solutionIRI = qs.get("?"+mfrag.getResidentNodeList().get(0).getOrdinaryVariableList().get(0)).toString();
		// IF THE VEHICLE EXISTS AND IT IS AN AUTONOMOUS ROBOT (THEN autRob ENTITY INSTANCE IN MTHEORY REFERS TO THAT VEHICLE)
		if(solutionIRI.equals(SPARQLQuery.ontNamespace+arg)){
			// STORE "AUTROB->AUTONOMOUSROBOT ARG" ON ovMap
			argsMap.put(mfrag.getOrdinaryVariableByName("autRob"), solutionIRI);
			globalMap.put(mfrag.getOrdinaryVariableByName("autRob"), solutionIRI);
			// ADD TO THE FINDINGS FILE:"(ASSERT (AUTONOMOUSROBOT_LABEL AUTROB1))" 
			kb.insertEntityInstance(new ObjectEntityInstance(arg, mebn.getObjectEntityContainer().getObjectEntityByName("AutonomousRobot")));
			// CALL RECURSIVE FUNCTION ON AUTONOMY_MFRAG{
			so.println("**************************EXECUTING RECURSIVE FUNCTION ON MFRAG: :"+mfrag);
			recursiveFunction(mfrag, kb, argsMap, globalMap);
		}else {
			so.println("THE ARGUMENT VEHICLE WASNT FOUND.");
		}
	}
			
	
	private static void recursiveFunction(MFrag mfrag, KnowledgeBase kb, Map<OrdinaryVariable, String> args, MultiMap<OrdinaryVariable, String> globalResolvedOV ) {		
		// INITIALIZE VARIABLES
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		MultiEntityBayesianNetwork mebn = mfrag.getMultiEntityBayesianNetwork();
		//TODO ONLY SUPPORT 1 RESIDENT NODE PER MFRAG
		List<OrdinaryVariable> rnArgOVList = mfrag.getResidentNodeList().get(0).getOrdinaryVariablesInArgument();
		List<OrdinaryVariable> ovList = mfrag.getOrdinaryVariableList();
		List<ContextNode> cnList = mfrag.getContextNodeList();
		Map<OrdinaryVariable,String> localOV = new HashMap<OrdinaryVariable,String>();
				
	// BUILD OV AND CN QUERY		
		// FOR EACH ORDINARY VARIABLE ON THE MFRAG
		for(OrdinaryVariable ov : ovList){
			// IF IT IS NOT PRESENT AS RESIDENT´S NODE ARGUMENT 	
			if(!rnArgOVList.contains(ov)){
			// ADD TO SPARQLQUERY-SELECT: OV_NAME
				sqb.addToSelect("?"+ov.getName());
			// ADD TO SPARQLQUERY-WHERE: OV_TYPE
				sqb.addTripleToWhere("?"+ov.getName(), "rdf:type", "ns:"+ov.getValueType());
			// ADD THE ORDINARY VARIABLEV TO localOV
				localOV.put(ov, "?"+ov.getName());
			}
		}		
		// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA "CN_NAME(OVARG0)=0VARG1"
		// FOR EACH CONTEXT NODE ON THE MFRAG
		for(ContextNode cn : cnList){
			// GET CN CORRESPONDING OWL PROPERTY NAME
			String name = labelToName(cn.getLabel());
			// GET CN ORDINARY VARIABLES AS ARGUMENTS
			List<OrdinaryVariable> cnargOVList = cn.getOrdinaryVariablesInArgument();
			// DIFFERENT POSSIBILITIES:
			// TODO ONLY SUPPORTED 1 RESIDENT NODE PER MFRAG
			String s0;
			String s1;
			// IF BOTH OF THE CONTEXT NODE ARGUMENTS ARE ARGUMENTS FOR THE RESDIENT NODE OF THE MFRAG
			if(rnArgOVList.contains(cnargOVList.get(0)) && rnArgOVList.contains(cnargOVList.get(1))){
				// ADD TO SPARQLQUERY WHERE: "CN_OVARG0_RESULT_IRI CN_NAME CN_OVARG1_RESULT_IRI"
				s0 = "ns:"+iriToName(args.get(cnargOVList.get(0)));
				s1 = "ns:"+iriToName(args.get(cnargOVList.get(1)));
			} // ELSE IF ARG0 IS PRESENT AS ARGUMENT AND ARG1 AS ORDINARY VARIABLE OF THE MFRAG
			else if(rnArgOVList.contains(cnargOVList.get(0)) && localOV.containsKey(cnargOVList.get(1))){
				// ADD TO SPARQLQUERY WHERE: "CN_OVARG0_RESULT_IRI CN:NAME OV_NAME"
				s0 = "ns:"+iriToName(args.get(cnargOVList.get(0)));
				s1 = localOV.get(cnargOVList.get(1));
			} // ELSE IF ARG1 IS PRESENT AS ARGUMENT AND ARG0 AS ORDINARY VARIABLE OF THE MFRAG
			else if(rnArgOVList.contains(cnargOVList.get(1)) && localOV.containsKey(cnargOVList.get(0))){
				// ADD TO SPARQLQUERY WHERE: "OV_NAME CN_NAME CN_OVARG1_RESULT_IRI"
				s0 = localOV.get(cnargOVList.get(0));
				s1 = "ns:"+iriToName(args.get(cnargOVList.get(1).getName()));
			} // ELSE IF BOTH ARGUMENTS ARE PRESENTS AS ORDINARY VARIABLES
			else if(localOV.containsKey(cnargOVList.get(0)) && localOV.containsKey(cnargOVList.get(1))){
				s0 = localOV.get(cnargOVList.get(0));
				s1 = localOV.get(cnargOVList.get(1));
			}else{
				// TODO BREAK RECURSIVE FUNCTION
				so.println("*****FOUND A CONTEXT NODE ARGUMENT THAT IS NOT PRESENT AS ORDINARY VARIABLE");
				break;
			}
			// TODO TEST IF THE CONTEXT NODE PROPERTY CAN BE APPLIED LIKE THIS (DOMAIN, RANGE, FUNCTIONAL, SYMMETRIC, RELFEXIVE ETC...)			
			sqb.addTripleToWhere(s0, "ns:"+name, s1);
		}			
	// PROCESS OV AND CN QUERY RESULTS
		// CREATE A NEW MAP FOR LOCAL RESOLVED OVs
		HashMap<OrdinaryVariable,String> localResolvedOV = new HashMap<OrdinaryVariable,String>();
		// IF QUERY IS NOT EMPTY
		if(sqb!=null){
			if(sqb.getQuery()!=null){
				// EXECUTE QUERY WITH ALL OV AND CN ON THE MFRAG
				so.println("*****EXECUTING QUERY WITH ALL OV AND CN ON MFRAG "+mfrag);
				List<QuerySolution> l = SPARQLQuery.executeArgQuery(sqb.getQuery());				
				// FOR EACH QUERY SOLUTION -->(THERE WILL BE AN INSTANCE OF THE MFRAG)<--
				for(QuerySolution qs : l){					
					so.println("*****MFRAG "+mfrag+" OV AND CN QUERY SOLUTION "+qs);
					so.println("*****ADDING ORDINARY VARIABLES RESULTS AS FINDINGS FOR MFRAG "+mfrag);
					// FOR EACH OV IN localOV
					for(OrdinaryVariable ov :localOV.keySet()){
						// GET RESULT FOR THAT OV
						String s = qs.get(ov.getName()).toString();
						// IF resolvedOV (GLOBAL) DOESNT ALREADY CONTAINS THE ENTRY (OV -> RESULT ENTITY IRI)
						if(!globalResolvedOV.contains(ov, s)){
							// ADD THE ENTRY AS RESOLVED
							globalResolvedOV.put(ov, s);
							// GET ENTITY NAME FROM IRI
							String name = iriToName(s);
							// ADD LINE TO FINDINGS FILE
							so.println("ADDED TO FINDINGS: "+"ASSERT ("+ov.getValueType().toString()+"_LABEL "+name+")");
///////////////////INSERT FINDING							
							kb.insertEntityInstance(new ObjectEntityInstance(name, mebn.getObjectEntityContainer().getObjectEntityByName(ov.getValueType().toString())));
/////////////////////////////////							
						}
						// PUT RESOLVED URIs ON LOCAL RESOLVED OVs MAP
						if(localOV.containsKey(ov)){
							localResolvedOV.put(ov, s);
						}
					}
					// ADD args TO LOCAL RESOLVED OVs
					localResolvedOV.putAll(args);
					so.println("*****ADDING CONTEXT NODES RESULTS TO FINDINGS FILE FOR MFRAG "+mfrag);
					// FOR EACH CN IN cnList (LOCAL TO FUNCTION) 
					for(ContextNode cn : cnList){ // (IF WE GOT THE ENTITIES IN THE RESULT, THAT ENTITIES COMPLY WITH THE CONTEXT NODES (WHERE CLAUSES))
						// GET CN CORRESPONDING OWL PROPERTY NAME
						String name = labelToName(cn.getLabel());
						// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA "CN_NAME(OVARG0)=0VARG1"
						String s0 = localResolvedOV.get(cn.getOrdinaryVariablesInArgument().get(0));
						String s1 = localResolvedOV.get(cn.getOrdinaryVariablesInArgument().get(1));
						so.println("ADDED TO FINDINGS FILE: "+"ASSERT (= ("+name+" "+iriToName(s0)+") "+iriToName(s1)+")");
////////////////INSERT FINDING					
						ObjectEntityInstance[] arguments = new ObjectEntityInstance[1];
						Entity state = null;
						try {
							ObjectEntity oe0 = mebn.getObjectEntityContainer().getObjectEntityByName(mfrag.getOrdinaryVariableByName(cn.getOrdinaryVariablesInArgument().get(0).getName()).getValueType().toString());
							oe0.addInstance(iriToName(s0));
							arguments[0] = oe0.getInstanceByName(iriToName(s0));
							ObjectEntity oe1 = mebn.getObjectEntityContainer().getObjectEntityByName(mfrag.getOrdinaryVariableByName(cn.getOrdinaryVariablesInArgument().get(1).getName()).getValueType().toString());
							oe1.addInstance(iriToName(s1));
							state = oe1.getInstanceByName(iriToName(s1));
						} catch (TypeException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						RandomVariableFinding rvf = new RandomVariableFinding(mebn.getDomainResidentNode(name), arguments, state, mebn);
						kb.insertRandomVariableFinding(rvf);
///////////////////////////////					
					}
				// PROCESS INPUT NODES
					so.println("**********PROCESSING INPUT NODES FOR MFRAG "+mfrag);
					processInputNodes(mfrag,sqb,kb,localResolvedOV,globalResolvedOV);
				}
			}else{
				so.println("**********PROCESSING INPUT NODES FOR MFRAG "+mfrag);
				processInputNodes(mfrag,sqb,kb,localResolvedOV,globalResolvedOV);
			}
		}
	}
			
	
	private static MultiMap<OrdinaryVariable, String> processInputNodes(MFrag mfrag, SPARQLQueryBuilder sqb, KnowledgeBase kb, Map<OrdinaryVariable, String> localResolvedOV, MultiMap<OrdinaryVariable, String> globalResolvedOV) {
		List<InputNode> notEdgeNodesList = new ArrayList<InputNode>();
	// FOR EACH INPUT NODE ON THE MFRAG
		for(InputNode in : mfrag.getInputNodeList()){
			ResidentNode irn = in.getResidentNodePointer().getResidentNode();
			String irnName = irn.getName();
			List<Argument> irnArgumentList = irn.getArgumentList();
			//TODO ONLY SUPPORTS EDGE RESIDENT NODES WITH JUST 1 ARGUMENT (same as the only one OV)
			OrdinaryVariable arg0OV = irnArgumentList.get(0).getOVariable();			
			// IF THE ORIGIN MFRAG FOR THIS INPUT NODE HAVE ANY INPUT NODES
			if(irn.getMFrag().getInputNodeList().size()>0){
				// STORE INPUT NODE IN notEdgeNodesList
				notEdgeNodesList.add(in);
				// BREAK
			// ELSE IF INPUT NODE NAME IS NOT THE SAME AS THE CURRENT RESIDENT NODE (PREVENT ENDLESS LOOPS)
			//TODO ONLY TESTS THE TYPE OF THE FIRST POSSIBLE VALUE. NEEDS SUPPORT FOR RESIDENT NODES WITH MANY POSSIBLE VALUE TYPES
			}else if(irnName!=mfrag.getResidentNodeList().get(0).getName()){ 
				int i = 0;
				// FOR EACH MAPPING INTO RESOLVED_OV (EACH ENTITY FOUND ON THE ONTOLOGY FOR THAT ORDINARY VARIABLE)
				for(String s : globalResolvedOV.get(arg0OV)){
					i++;
					String irnQueryVar = "?"+irnName+i;
					sqb.addToSelect(irnQueryVar);
					// IF RN POSSIBLE VALUE TYPE IS NOT CATEGORICAL
					if(!irn.getPossibleValueLinkList().get(0).getState().getType().getName().equals("CategoryLabel")){
						// ADD TRIPLE SPECIFYING THE EXPECTED RESULT ENTITY TYPE
						sqb.addTripleToWhere(irnQueryVar, "rdf:type", irn.getPossibleValueLinkList().get(0).getState().getType().getName());
					}
					sqb.addTripleToWhere("ns:"+iriToName(s), "ns:"+irnName, irnQueryVar);
				}
				// EXECUTE QUERY
				so.println("*****EXECUTING QUERY FOR EDGE INPUT NODE "+in+" IN MFRAG "+mfrag);
				List<QuerySolution> lqs2 = SPARQLQuery.executeArgQuery(sqb.getQuery());
				QuerySolution qs2;
				if(lqs2.size()>0){
					// TODO EDGE NODES ONLY ADMIT ONE RESULT
					qs2 = lqs2.get(0);
					int j = 0;
					for(String s : globalResolvedOV.get(irn.getArgumentList().get(0).getOVariable())){
						j++;
						String irnQueryVar = "?"+irnName+j;
						String arg = iriToName(s);
						String res = stringValueIRItoLabel(qs2.get(irnQueryVar).toString());
						// ADD TO THE FINDINGS FILE: (ASSERT (= ("RN_NAME" "RESOLVED_OV_ARG") "QUERYRESULT")
						so.println("ADDED TO THE FINDINGS FILE: (ASSERT (= ("+irnName+" "+arg+") "+res+"))");
						//pw.println("(ASSERT (= ("+irnName+" "+arg+") "+res+"))");
					}
					
				}else{
					so.println("*****FOUND AN EDGE NODE THAT DIDNT PRODUCE RESULT. MFRAG: "+mfrag);
				}
			}
		}
	// FOR EACH INPUT NODE IN notEdgeNodesList
		for(InputNode in : notEdgeNodesList){
			if(in.getResidentNodePointer().getResidentNode().getName() != mfrag.getResidentNodeList().get(0).getName()){ 
				MFrag mf = in.getResidentNodePointer().getResidentNode().getMFrag();
				// GET INPUT NODE ARGS 
				Map<OrdinaryVariable, String> inArgs = new HashMap<OrdinaryVariable, String>();
					for(int i = 0; i < in.getResidentNodePointer().getNumberArguments();i++){
						// (MUST BE PRESENT ON localResolvedOV MAP)
						if(localResolvedOV.containsKey(in.getResidentNodePointer().getArgument(i))){
							inArgs.put(in.getArgumentList().get(i).getOVariable(), localResolvedOV.get(in.getResidentNodePointer().getArgument(i)));
						}
					}
				so.println("*************************EXECUTING RECURSIVE FUNCTION ON "+mf+", FROM NOT EDGE INPUT NODES PROCESSING IN "+mfrag+"");
				//recursiveFunction(mf, pw, inArgs, globalResolvedOV);
			}
		}
	// CLEAR notEdgeNodesList
		notEdgeNodesList.clear();
		return globalResolvedOV;
	}
			
	
	private static ProbabilisticNode executeMEBNQuery(MultiEntityBayesianNetwork mebn, QueryNodeNameAndArguments qnnaa) {
		ProbabilisticNode n = null;
	// fill findings
		kb = tmr.fillFindings(mebn, kb);
	// execute query
		Collection<QueryNodeNameAndArguments> qnnaaCollection = new ArrayList<QueryNodeNameAndArguments>();
		qnnaaCollection.add(qnnaa);
		try {
			net = tmr.callLaskeyAlgorithm(mebn, kb, qnnaaCollection);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//TODO: MORE THAN 1 ARG SUPPORT NEEDED
		n = (ProbabilisticNode) net.getNode(qnnaa.getNodeName()+"__"+qnnaa.getArguments()[0]);
		return n;
	}
			
	
	private static String stringValueIRItoLabel(String iri) {
		return iri.substring(0,iri.indexOf("^"));
	}
	private static String labelToName(String label) {
		String name = label.substring(2, label.length()-2);
		return name.substring(0, name.indexOf("("));
	}
 	@SuppressWarnings("unused")
	private static String nameToIRI(String name) {
		return SPARQLQuery.ontNamespace+name;
	}
	private static String iriToName(String iri) {
		return iri.substring(iri.lastIndexOf("/")+1);
	}
	private static void printAll(List<ResidentNode> residentNodesList) {
		List<OrdinaryVariable> ovList = new ArrayList<OrdinaryVariable>();
		List<ContextNode> cnList = new ArrayList<ContextNode>();
		Map<String,String> argMap = new TreeMap<String,String>();
		Map<String,String> pvMap = new TreeMap<String,String>();
		
		Iterator<ResidentNode> it = residentNodesList.iterator();
		// FOR EACH RESIDENT NODE
		while(it.hasNext()){
			ResidentNode x = it.next();
			// PRINT NAME
			so.println("NAME: "+x.getName());
			// PRINT LABEL
			so.println("LABEL: "+x.getLabel());
			// PRINT MFRAG INPUT NODES
			so.println("INPUT NODES: "+x.getMFrag().getInputNodeList().toString());
			// GET MFRAG´s ORDINARY VARIABLES
			List<OrdinaryVariable> auxOVList = x.getMFrag().getOrdinaryVariableList();
			// ADD NEW ORDINARY VARIABLES TO GLOBAL LIST
			if(!ovList.containsAll(auxOVList)){
				for(OrdinaryVariable ov : auxOVList){
					if(!ovList.contains(ov))ovList.add(ov);
				}
			}
			Iterator<OrdinaryVariable> it2 = auxOVList.iterator();
			int i = 1;
			while(it2.hasNext()){
				OrdinaryVariable ov = it2.next();
				so.println("MFRAG OV "+i+" TYPE: "+ov.getValueType());
				so.println("MFRAG OV "+i+" NAME: "+ov.getName());
				i++;
			}
			// GET MFRAG´S CONTEXT NODES
			List<ContextNode> auxCNList = x.getMFrag().getContextNodeList();
			// ADD THE NEW CONTEXT NODES TO THE GLOBAL LIST
			if(!cnList.containsAll(auxCNList)){
				for(ContextNode cn : auxCNList){
					if(!cnList.contains(cn))cnList.addAll(auxCNList);
				}
			}
			Iterator<ContextNode> it21 = auxCNList.iterator();
			int i1 = 1;
			while(it21.hasNext()){
				ContextNode cn = it21.next();
				String label = cn.getLabel();
				String name = label.substring(2, label.length()-2);
				name = name.substring(0, name.indexOf("("));
				so.println("MFRAG CN "+i1+" LABEL: "+label);
				List<OrdinaryVariable> list = cn.getOrdinaryVariablesInArgument();
				so.println("MFRAG CN "+i1+" OV 0 IN ARGUMENT: "+list.get(0));
				so.println("MFRAG CN "+i1+" NAME: "+name);
				so.println("MFRAG CN "+i1+" OV 1 IN ARGUMENT: "+list.get(1));
				i1++;
			}
			// PRINT ARGUMENTS NAMES AND TYPES
			Iterator<Argument> it3 = x.getArgumentList().iterator();
			int i2 = 1;
			while(it3.hasNext()){
				Argument z = it3.next();
				String name = z.getOVariable().getName();
				so.println("ARG "+i2+": "+name);
				String type = z.getOVariable().getValueType().getName();
				so.println("ARG "+i2+" TYPE: "+type);
				if(!type.equals("CategoryLabel") && !argMap.containsKey(name)) argMap.put(name, type);
				i2++;
			}
			// PRINT POSSIBLE VALUES
			Iterator<StateLink> it4 = x.getPossibleValueLinkList().iterator();
			int i3 = 1;
			while(it4.hasNext()){
				StateLink y = it4.next();
				String name = y.getState().getName();
				so.println("POSSIBLE VALUE "+i3+": "+name);
				String type = y.getState().getType().getName();
				so.println("POSSIBLE VALUE "+i3+" TYPE: "+type);
				if(!type.equals("CategoryLabel") && !pvMap.containsKey(name)) pvMap.put(name,type);
//				so.println("POSSIBLE VALUE "+i3+": "+name);
				i3++;
			}
			so.println("\n\n");
		}
	}
	
}
