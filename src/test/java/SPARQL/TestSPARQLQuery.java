package test.java.SPARQL;

import java.util.List;

import com.hp.hpl.jena.query.QuerySolution;

import main.java.jenafacade.SPARQLQuery;

public class TestSPARQLQuery {

	public static void main(String[] args) {
		SPARQLQuery sparqlQuery = new SPARQLQuery();
		List<QuerySolution> lqs = sparqlQuery.executeArgQuery("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
			"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
			"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
			"PREFIX ns: <http://www.semanticweb.org/SWARMs/ontology/>\n"+
			"SELECT ?autRob ?CA_vehicleBatteryLevel ?step ?path ?gpsPos ?prevStep ?gpsPos2 ?waterCol ?waterCurr ?CA_waterCurrentDirection ?CA_waterCurrentVelocity ?CA_stepDirection\n"+
			"WHERE {\n"+
			"?autRob rdf:type ns:AutonomousRobot . \n"+
			"?autRob ns:CA_vehicleBatteryLevel ?CA_vehicleBatteryLevel . \n"+
			"?step rdf:type ns:Step . \n"+
			"?path rdf:type ns:Path . \n"+
			"?path ns:hasStep ?step . \n"+
			"?autRob ns:CA_waybackPath ?path . \n"+
			"?gpsPos rdf:type ns:GPSPosition . \n"+
			"?prevStep rdf:type ns:Step . \n"+
			"?gpsPos2 rdf:type ns:GPSPosition . \n"+
			"?waterCol rdf:type ns:WaterColumn . \n"+
			"?waterCurr rdf:type ns:WaterCurrent . \n"+
			"?step ns:startPostion ?gpsPos . \n"+
			"?path ns:hasStep ?step . \n"+
			"?prevStep ns:endPosition ?gpsPos . \n"+
			"?step ns:endPosition ?gpsPos2 . \n"+
			"?gpsPos2 ns:gpsPositionWaterCol ?waterCol . \n"+
			"?waterCol ns:hasWaterCurrent ?waterCurr . \n"+
			"?waterCurr ns:CA_waterCurrentDirection ?CA_waterCurrentDirection . \n"+
			"?waterCurr ns:CA_waterCurrentVelocity ?CA_waterCurrentVelocity . \n"+
			"?step ns:CA_stepDirection ?CA_stepDirection . \n"+
			"?path ns:hasStep ?step . \n"+
			"?path ns:hasStep ?prevStep\n"+
			"}");
		for(QuerySolution qs : lqs){
			System.out.println("autRob: "+qs.get("?autRob")+"\n"+
					"CA_vehicleBaterryLevel: "+qs.get("?CA_vehicleBatteryLevel")+"\n"+
					"step: "+qs.get("?step")+"\n"+
					"path: "+qs.get("?path")+"\n"+
					"gpsPos: "+qs.get("?gpsPos")+"\n"+
					"prevStep: "+qs.get("?prevStep")+"\n"+
					"gpsPos2: "+qs.get("?waterCol")+"\n"+
					"waterCurrentDirection: "+qs.get("?CA_waterCurrentDirection")+"\n"+
					"waterCurrentVelocity: "+qs.get("CA_waterCurrentVelocity")+"\n"+
					"stepDirection: "+qs.get("CA_stepDirection"));
		}
	}

}
