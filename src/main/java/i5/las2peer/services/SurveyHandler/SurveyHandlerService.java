package i5.las2peer.services.SurveyHandler;

import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.connectors.webConnector.WebConnector;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.Consumes;

import i5.las2peer.api.Context;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import org.json.XML;
import org.junit.Assert;
import org.junit.Test;

// TODO Describe your own service


/**
 * SurveyHandlerService
 *
 * A service to conduct surveys with a chatbot created with the SBF.
 *
 */




// TODO Adjust the following configuration
@Api
@SwaggerDefinition(
		info = @Info(
				title = "las2peer Template Service",
				version = "1.0.0",
				description = "A las2peer Survey Service.",
				termsOfService = "http://your-terms-of-service-url.com",
				contact = @Contact(
						name = "John Doe",
						url = "provider.com",
						email = "john.doe@provider.com"),
				license = @License(
						name = "your software license name",
						url = "http://your-software-license-url.com")))
@ServicePath("/SurveyHandler")
// TODO Your own service class
public class SurveyHandlerService extends RESTService {
	//to store if a participant has been contacted to start the survey
	private static HashMap<String, Boolean> participantContacted = new HashMap<String, Boolean>();
	//
	private static HashMap<String, Boolean> surveySetUp = new HashMap<String, Boolean>();
	//url from limesurvey
	String LSURL = "https://limesurvey.tech4comp.dbis.rwth-aachen.de/";




	/**
	 * Template of a get function.
	 *
	 * @return Returns an HTTP response with the username as string content.
	 */
	@GET
	@Path("/get")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE") })
	public Response getTemplate() {
		UserAgent userAgent = (UserAgent) Context.getCurrent().getMainAgent();
		String name = userAgent.getLoginName();
		return Response.ok().entity(name).build();
	}

	/**
	 * Template of a post function.
	 *
	 * @param myInput The post input the user will provide.
	 * @return Returns an HTTP response with plain text string content derived from the path input param.
	 */
	@POST
	@Path("/post/{input}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE") })
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "Example method that returns a phrase containing the received input.")
	public Response postTemplate(@PathParam("input") String myInput) {
		String returnString = "";
		returnString += "Input " + myInput;
		return Response.ok().entity(returnString).build();
	}

	// TODO your own service methods, e. g. for RMI
	@POST
	@Path("/survey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE") })
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "Example method that returns a phrase containing the received input.")
	public Response survey(String body) {
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
		HashMap<String, String> surveyInfos = new HashMap<>();
		try{
			JSONObject tbody = (JSONObject) p.parse(body);
			String username = tbody.getAsString("username");
			String password = tbody.getAsString("password");
			String surveyIDString = tbody.getAsString("surveyID");
			int surveyID = (int) tbody.getAsNumber("surveyID");
			String uri = tbody.getAsString("uri");
			JSONArray questions = new JSONArray();

			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \""+username+"\", \"" +password+"\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			if(!(surveySetUp.containsKey(surveyIDString))){
				ClientResponse mini2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \""+sessionKeyString+"\", \"" +surveyID+"\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire2 = (JSONObject) p.parse(mini2.getResponse());
				JSONArray ql = (JSONArray) minire2.get("result");

				for(int i=1; i<ql.size(); i++){
					ClientResponse mini3 = mini.sendRequest("POST", uri, ("{\"method\": \"get_question_properties\", \"params\": [ \""+sessionKeyString+"\", \"" +i+"\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
					JSONObject minire3 = (JSONObject) p.parse(mini3.getResponse());

					JSONObject z = (JSONObject) minire3.get("result");
					questions.add(i-1,z);

					if(z.getAsString("parent_qid").equals("0")){
						surveyInfos.put("Question" + i, z.getAsString("question"));
					}
				}
				ClientResponse mini4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \""+sessionKeyString+"\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire4 = (JSONObject) p.parse(mini4.getResponse());
				JSONArray sl = (JSONArray) minire4.get("result");
				for(Object i : sl){
					if(((JSONObject) i).getAsString("sid").equals(surveyIDString)){
						surveyInfos.put("surveyTitle", ((JSONObject) i).getAsString("surveyls_title"));
					}
				}

				surveySetUp.put(surveyIDString, true);
			}

			/*
			if(this.participantContacted.get("") == null){
				participantContacted.put("", true);
				response.put("text", "Would you like to start the survey " + surveyInfos.get("surveyTitle") + " ?");
				return Response.ok().entity(response).build();
			}
			*/
			String intent = "";
			if(questions != null){
				return Response.ok().entity(continueQuestioning(questions, surveyInfos, tbody, intent)).build();
			}
			else{
				response.put("text", "There are no questions in this survey");
				return Response.ok().entity(response).build();
			}

		}
		catch(Exception e){
			e.printStackTrace();
		}

	response.put("text", "try block broken");
	return Response.ok().entity(response).build();

	}


	private JSONObject continueQuestioning(JSONArray questions, HashMap<String,String> surveyInfos, JSONObject tbody, String intent){
		JSONObject response = new JSONObject();
		String answer ="";
		if(intent.equals("skip")){

		}
		answer = "Thank you for answering";
		response.put("text", answer);
		return response;
	}




}
