package main.java;

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
import main.java.jena.SPARQLQuery;
import main.java.jena.SPARQLQueryBuilder;
import unbbayes.prs.mebn.Argument;
import unbbayes.prs.mebn.ContextNode;
import unbbayes.prs.mebn.InputNode;
import unbbayes.prs.mebn.MFrag;
import unbbayes.prs.mebn.OrdinaryVariable;
import unbbayes.prs.mebn.ResidentNode;
import unbbayes.prs.mebn.entity.StateLink;

public class CAQuery {
	
	private static PrintStream so = System.out;
	
	private CAQuery(){}	
	
	public static void recursiveFunction(MFrag mfrag, PrintWriterHelper pw, Map<OrdinaryVariable, String> args, MultiMap<OrdinaryVariable, String> globalResolvedOV ) {		
	// INITIALIZE VARIABLES
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		//TODO ONLY SUPPORT 1 RESIDENT NODE PER MFRAG (WITH 1 ARGUMENT)
		List<OrdinaryVariable> rnArgOVList = new ArrayList<>();
		for(Argument a : mfrag.getResidentNodeList().get(0).getArgumentList()){
			rnArgOVList.add(a.getOVariable());
		}
		//List<OrdinaryVariable> rnArgOVList = mfrag.getResidentNodeList().get(0).getArgumentList().getOrdinaryVariablesInArgument();
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
		// FOR EACH CONTEXT NODE ON THE MFRAG
		// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA "CN_NAME(CNARGOV0)=CNARGOV1"
		for(ContextNode cn : cnList){
			// GET CN CORRESPONDING OWL PROPERTY NAME
			String name = labelToName(cn.getLabel());
			// GET CN FORMULA ARGUMENTS ORDINARY VARIABLES 
			List<OrdinaryVariable> cnargOVList = new ArrayList<>();
			Iterator<OrdinaryVariable> it = cn.getFormulaTree().getChildren().get(0).getVariableList().iterator();
			cnargOVList.add(it.next());
			it = cn.getFormulaTree().getChildren().get(1).getVariableList().iterator();
			cnargOVList.add(it.next());
			
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
				s1 = "ns:"+iriToName(args.get(cnargOVList.get(1)));
			} // ELSE IF BOTH ARGUMENTS ARE PRESENTS AS ORDINARY VARIABLES
			else if(localOV.containsKey(cnargOVList.get(0)) && localOV.containsKey(cnargOVList.get(1))){
				s0 = localOV.get(cnargOVList.get(0));
				s1 = localOV.get(cnargOVList.get(1));
			}else{
				// TODO BREAK RECURSIVE FUNCTION
				so.println("*****FOUND A CONTEXT NODE ARGUMENT THAT IS NOT PRESENT AS ORDINARY VARIABLE");
				break;
			}
			// TODO TEST IF THE CONTEXT NODE PROPERTY CAN BE APPLIED LIKE IN THIS WAY (DOMAIN, RANGE, FUNCTIONAL, SYMMETRIC, RELFEXIVE ETC...)			
			sqb.addTripleToWhere(s0, "ns:"+name, s1);
		}			
	// EXECUTE OV AND CN QUERY AND PROCESS RESULTS
		// CREATE A NEW MAP FOR LOCAL RESOLVED OVs
		HashMap<OrdinaryVariable,String> localResolvedOV = new HashMap<OrdinaryVariable,String>();
		// IF QUERY IS NOT EMPTY
		if(sqb!=null && sqb.getQuery()!=null){
			// EXECUTE QUERY WITH ALL OV AND CN ON THE MFRAG
			so.println("*****EXECUTING QUERY WITH ALL OV AND CN ON MFRAG "+mfrag);
			List<QuerySolution> l = SPARQLQuery.executeArgQuery(sqb.getQuery());				
			// FOR EACH QUERY SOLUTION -->(THERE WILL BE AN INSTANCE OF THE MFRAG)<--
			for(QuerySolution qs : l){					
				so.println("*****MFRAG "+mfrag+" OV AND CN QUERY SOLUTION "+qs);
				so.println("*****ADDING ORDINARY VARIABLES RESULTS TO FINDINGS FILE FOR MFRAG "+mfrag);
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
						so.println("ADDED TO FINDINGS FILE: "+"ASSERT ("+ov.getValueType().toString()+"_LABEL "+name+")");
						pw.println("(ASSERT ("+ov.getValueType().toString()+"_LABEL "+name+"))");
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
					// TODO ONLY SUPPORTED CONTEXT NODES WITH FORMULA "CN_NAME(CNARGOV0)=CNARGOV1"
					// GET CN FORMULA ARGUMENTS ORDINARY VARIABLES 
					List<OrdinaryVariable> cnargOVList = new ArrayList<>();
					Iterator<OrdinaryVariable> it = cn.getFormulaTree().getChildren().get(0).getVariableList().iterator();
					cnargOVList.add(it.next());
					it = cn.getFormulaTree().getChildren().get(1).getVariableList().iterator();
					cnargOVList.add(it.next());
					// ADD TO FINDINGS FILE
					String s0 = localResolvedOV.get(cnargOVList.get(0));
					String s1 = localResolvedOV.get(cnargOVList.get(1));
					so.println("ADDED TO FINDINGS FILE: "+"ASSERT (= ("+name+" "+iriToName(s0)+") "+iriToName(s1)+")");
					pw.println("(ASSERT (= ("+name+" "+iriToName(s0)+") "+iriToName(s1)+"))");
				}
			// PROCESS INPUT NODES
				so.println("**********PROCESSING INPUT NODES FOR MFRAG "+mfrag);
				processInputNodes(mfrag,sqb,pw,localResolvedOV,globalResolvedOV);
			}
		}else{
		// IF QUERY IS EMPTY, PROCESS INPUT NODES WITHOUT DOING ANYTHING ELSE
			so.println("**********PROCESSING INPUT NODES FOR MFRAG "+mfrag);
			processInputNodes(mfrag,sqb,pw,localResolvedOV,globalResolvedOV);
		}
	}
		
	private static MultiMap<OrdinaryVariable, String> processInputNodes(MFrag mfrag, SPARQLQueryBuilder sqb, PrintWriterHelper pw, Map<OrdinaryVariable, String> localResolvedOV, MultiMap<OrdinaryVariable, String> globalResolvedOV) {
		List<InputNode> notEdgeNodesList = new ArrayList<>();
		List<InputNode> edgeNodesList = new ArrayList<>();
		// FOR EACH INPUT NODE ON THE MFRAG
		for(InputNode in : mfrag.getInputNodeList()){
			// IF THE ORIGIN MFRAG FOR THIS INPUT NODE HAVE ANY INPUT NODES
			if(!in.getResidentNodePointer().getResidentNode().getMFrag().getInputNodeList().isEmpty()){
				// STORE INPUT NODE IN notEdgeNodesList
				notEdgeNodesList.add(in);
			}else{
				// ELSE STORE IT IN edgeNodesList
				edgeNodesList.add(in);
			}
		}
		// PROCESS EDGE edgeNodesList INPUT NODES
		processEdgeInputNodes(edgeNodesList, mfrag, pw, sqb, localResolvedOV, globalResolvedOV);
		// PROCESS notEdgeNodesList INPUT NODES
		processNotEdgeInputNodes(notEdgeNodesList, mfrag, pw, localResolvedOV, globalResolvedOV);
		// CLEAR notEdgeNodesList
		notEdgeNodesList.clear();
		return globalResolvedOV;
	}
	
	private static void processNotEdgeInputNodes(List<InputNode> notEdgeNodesList, MFrag mfrag, PrintWriterHelper pw, Map<OrdinaryVariable, String> localResolvedOV, MultiMap<OrdinaryVariable, String> globalResolvedOV){
		// FOR EACH NOT EDGE INPUT NODE
		for(InputNode in : notEdgeNodesList){
			// IF INPUT NODE NAME IS NOT THE SAME AS THE CURRENT RESIDENT NODE (PREVENT ENDLESS LOOPS)
			if(in.getResidentNodePointer().getResidentNode().getName() != mfrag.getResidentNodeList().get(0).getName()){ 
				// GET INPUT NODES MFRAG
				MFrag mf = in.getResidentNodePointer().getResidentNode().getMFrag();
				// GET INPUT NODE ARGS 
				Map<OrdinaryVariable, String> inArgs = new HashMap<>();
				for(int i = 0; i < in.getResidentNodePointer().getNumberArguments();i++){
					// (MUST BE PRESENT ON localResolvedOV MAP)
					if(localResolvedOV.containsKey(in.getResidentNodePointer().getArgument(i))){
						inArgs.put(in.getArgumentList().get(i).getOVariable(), localResolvedOV.get(in.getResidentNodePointer().getArgument(i)));
					}else{
						//TODO ERROR INPUT NODE RESIDENT NODE NOT PRESENT ON localResolvedOV
					}
				}
				so.println("*************************EXECUTING RECURSIVE FUNCTION ON "+mf+", FROM NOT EDGE INPUT NODES PROCESSING IN "+mfrag+"");
				// CALL RECURSIVE FUNCTION  ON THE INPUT NODE MFRAG AND WITH THE INPUT NODE ARGS
				recursiveFunction(mf, pw, inArgs, globalResolvedOV);
			}
		}
	}
	
	private static void processEdgeInputNodes(List<InputNode> edgeNodesList, MFrag mfrag, PrintWriterHelper pw, SPARQLQueryBuilder sqb, Map<OrdinaryVariable, String> localResolvedOV, MultiMap<OrdinaryVariable, String> globalResolvedOV){
		for(InputNode in : edgeNodesList){
			ResidentNode irn = in.getResidentNodePointer().getResidentNode();
			String irnName = irn.getName();
			List<Argument> irnArgumentList = irn.getArgumentList();
			//TODO ONLY SUPPORTS EDGE RESIDENT NODES WITH JUST 1 ARGUMENT (same as the only one OV)
			OrdinaryVariable arg0OV = irnArgumentList.get(0).getOVariable();	
			
			// IF INPUT NODE NAME IS NOT THE SAME AS THE CURRENT RESIDENT NODE (PREVENT ENDLESS LOOPS)
			//TODO ONLY TESTS THE TYPE OF THE FIRST POSSIBLE VALUE. NEEDS SUPPORT FOR RESIDENT NODES WITH MANY POSSIBLE VALUE TYPES
			if(!irnName.equals(mfrag.getResidentNodeList().get(0).getName())){
				int i = 0;
		// BUILD QUERY
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
		// PROCESS QUERY SOLUTION AND FILL THE FINDINGS FILE
				if(!lqs2.isEmpty()){
					// TODO EDGE NODES ONLY ADMIT ONE RESULT (FUNCTIONAL OBJECT OR DATA PROPERTIES)
					qs2 = lqs2.get(0);
					int j = 0;
					for(String s : globalResolvedOV.get(irn.getArgumentList().get(0).getOVariable())){
						j++;
						String irnQueryVar = "?"+irnName+j;
						String arg = iriToName(s);
						String res = stringValueIRItoLabel(qs2.get(irnQueryVar).toString());
						// ADD TO THE FINDINGS FILE: (ASSERT (= ("RN_NAME" "RESOLVED_OV_ARG") "QUERYRESULT")
						so.println("ADDED TO THE FINDINGS FILE: (ASSERT (= ("+irnName+" "+arg+") "+res+"))");
						pw.println("(ASSERT (= ("+irnName+" "+arg+") "+res+"))");
					}
				}else{
					so.println("***ERROR*** FOUND AN EDGE NODE THAT DIDNT PRODUCE ANY RESULT. MFRAG: "+mfrag+". RN: "+irnName);
				}
			}
		}
	}
	
	
	private static String stringValueIRItoLabel(String iri) {
		return (iri.indexOf("^^") > 0)? iri.substring(0,iri.indexOf("^^")) : iriToName(iri);
	}
	// TODO ONLY SUPPORTS CONTEXT NODES WITH FORMULA "CN_NAME(CNARGOV0)=CNARGOV1"
	private static String labelToName(String label) {
		String name = label.substring(2, label.length()-2);
		return name.substring(0, name.indexOf("("));
	}
 	@SuppressWarnings("unused")
	private static String nameToIRI(String name) {
		return SPARQLQuery.ontNamespace+name;
	}
	public static String iriToName(String iri) {
		return (iri.lastIndexOf("/") > 0)? iri.substring(iri.lastIndexOf("/")+1) : iri;
	}
	public static void printAll(List<ResidentNode> residentNodesList) {
		List<OrdinaryVariable> ovList = new ArrayList<>();
		List<ContextNode> cnList = new ArrayList<>();
		Map<String,String> argMap = new TreeMap<>();
		Map<String,String> pvMap = new TreeMap<>();
		
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
					if(!ovList.contains(ov)){
						ovList.add(ov);
					}
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
					if(!cnList.contains(cn)){
						cnList.addAll(auxCNList);
					}
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
				List<OrdinaryVariable> list = new ArrayList<>(); 
				for(Argument a : cn.getArgumentList()){
					list.add(a.getOVariable());
				}
				//List<OrdinaryVariable> list = cn.getOrdinaryVariablesInArgument();
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
				if(!"CategoryLabel".equals(type) && !argMap.containsKey(name)){
					argMap.put(name, type);
				}
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
				if(!"CategoryLabel".equals(type) && !pvMap.containsKey(name)){
					pvMap.put(name,type);
				}
//				so.println("POSSIBLE VALUE "+i3+": "+name);
				i3++;
			}
			so.println("\n\n");
		}
	}
	
}
