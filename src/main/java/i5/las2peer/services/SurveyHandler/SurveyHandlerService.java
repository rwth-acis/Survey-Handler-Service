package i5.las2peer.services.SurveyHandler;

import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;

import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
	private static Survey surveyGlobal;
	private static boolean firstStartUp = true;


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
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response getTemplate() {
		UserAgent userAgent = (UserAgent) Context.getCurrent().getMainAgent();
		String name = userAgent.getLoginName();
		return Response.ok().entity(name).build();
	}

	@POST
	@Path("/post/{input}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
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
	@Path("/takingSurvey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response takingSurvey(String input) {
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try{
			LocalDateTime now = LocalDateTime.now();
			System.out.println(now);

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String intent = bodyInput.getAsString("intent");

			// Check if survey is set up already
			if (Objects.isNull(surveyGlobal)){
				response.put("text", "Please wait for the survey to be initialized.");
				return Response.ok().entity(response).build();
			}


			// Check if message was sent by someone known
			String senderEmail = bodyInput.getAsString("email");
			if (Objects.isNull(surveyGlobal.findParticipant(senderEmail))){
				// participant does not exist, create a new one
				Participant newParticipant = new Participant(senderEmail);
				surveyGlobal.addParticipant(newParticipant);
			}

			// Get the existing participant
			Participant currParticipant = surveyGlobal.findParticipant(senderEmail);
			String message = bodyInput.getAsString("msg");

			//Set the time the participant answered to check later if needed to be reminded to finish survey
			currParticipant.setLastTimeActive(LocalDateTime.now());

			// Get the next action
			return currParticipant.calculateNextAction(intent, message);




		} catch (ParseException e) {
			e.printStackTrace();
		}
		response.put("text", "survey taking block broken");
		return Response.ok().entity(response).build();
	}




	private void setUpSurvey(String input){
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
		try {
			JSONObject bodyInput = (JSONObject) p.parse(input);

			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String surveyIDString = bodyInput.getAsString("surveyIDString");
			int surveyID = Integer.parseInt(surveyIDString);
			String uri = bodyInput.getAsString("uri");

			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			// Get questions from limesurvey
			ClientResponse mini2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire2 = (JSONObject) p.parse(mini2.getResponse());
			JSONArray ql = (JSONArray) minire2.get("result");

			// Create a new survey object
			Survey newSurvey = new Survey(surveyIDString, ql);

			// Get survey title and add to survey
			ClientResponse mini4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \"" + sessionKeyString + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire4 = (JSONObject) p.parse(mini4.getResponse());
			JSONArray sl = (JSONArray) minire4.get("result");
			for (Object i : sl) {
				if (((JSONObject) i).getAsString("sid").equals(surveyIDString)) {
					newSurvey.addTitle( ((JSONObject) i).getAsString("surveyls_title"));
					break;
				}
			}

			// Check if adding title worked
			if (Objects.isNull(newSurvey.getTitle())){
				System.out.println("Failed to add title. Aborting survey creation...");
			} else {
				System.out.println(newSurvey.getTitle());
				surveyGlobal = newSurvey;
				System.out.println("Survey successfully initialized.");
			}




		} catch(Exception e){
			e.printStackTrace();
		}
	}




	@POST
	@Path("/adminSurvey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response adminSurvey(String input) {

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {
			JSONObject bodyInput = (JSONObject) p.parse(input);
			//set up survey, if not yet done

			if(firstStartUp){
				firstStartUp = false;
				setUpSurvey(input);
				if (surveyGlobal.getSortedQuestionIds().size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}


			//TODO: check if rocket chat passes on email of user
			if (!(bodyInput.getAsString("adminmail").equals(bodyInput.getAsString("email")))) {
				response.put("text", "only admin is allowed to do that");
				return Response.ok().entity(response).build();
			}
			String intent = bodyInput.getAsString("intent");
			if (intent.equals("add_participant")) {
				Participant newParticipant = new Participant(bodyInput.getAsString("msg"));
				boolean added = surveyGlobal.addParticipant(newParticipant);
				response.put("text", "Adding participant " + newParticipant.getEmail() + ", got result: " + added);
				//response.put("text", "Adding participant " +surveyGlobal.getParticipantsEmails() + ", got result: " + added);
				System.out.println(surveyGlobal.getParticipants().toString());
				System.out.println(surveyGlobal.getParticipants().size());
				System.out.println(surveyGlobal.findParticipant(newParticipant.getEmail()));
				return Response.ok().entity(response).build();
			}
			/*
			else if(intent.equals("add_participants")){
				ArrayList<String> paEmails = new ArrayList<>();
				if(bodyInput.getAsString("msg").contains(",")){
					for(String s : bodyInput.getAsString("msg").split(",")){
						paEmails.add(s);
					}
				}
				response.put("text", "");
				return Response.ok().entity(response).build();
			}
			*/
			else if (intent.equals("get_participants")) {
				//System.out.println(surveyGlobal.getParticipants().toString());
				//System.out.println(surveyGlobal.getParticipants());
				//System.out.println(surveyGlobal.getParticipants().size());
				response.put("text", surveyGlobal.getParticipantsEmails() + ". Currently there are " + surveyGlobal.getParticipants().size() + "participants in this survey");
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_answers")) {
				System.out.println(surveyGlobal.getAnswersStringFromAllParticipants());
				response.put("text", surveyGlobal.getAnswersStringFromAllParticipants());
				return Response.ok().entity(response).build();
			}
			else if(intent.equals("start_survey")){
				String emails = "";
				for (Participant pa : surveyGlobal.getParticipants()) {
					if(!(pa.getEmail().equals("null"))){ //&& !(pa.getParticipantContacted())) {
						emails += pa.getEmail() + ",";
						pa.setParticipantContacted();
					}
				}
				if(emails.length() > 0){
					emails.substring(0, emails.length() -1); //remove last separator, only if there are participants

					System.out.println(emails);
					response.put("text", "Would you like to start the survey \"" + surveyGlobal.getTitle() + "\"?");
					response.put("contactList", emails);
					return Response.ok().entity(response).build();
				}
				else{
					response.put("text", "Please add participants to start the survey.");
					return Response.ok().entity(response).build();
				}

			}
			else {
				response.put("text", "intent not recognized");
				return Response.ok().entity(response).build();
			}

		} catch (Exception e) {
			e.printStackTrace();
			response.put("text", "admin block broken");
			return Response.ok().entity(response).build();
		}
	}


	@POST
	@Path("/sendResultsToLimesurvey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response sendResultsToLimesurvey(String input){

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {

			if(firstStartUp){
				response.put("text", "Survey is not yet initialized.");
				return Response.ok().entity(response).build();
			}


			JSONObject bodyInput = (JSONObject) p.parse(input);

			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String surveyIDString = bodyInput.getAsString("surveyIDString");
			int surveyID = Integer.parseInt(surveyIDString);
			String uri = bodyInput.getAsString("uri");

			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			/*
			ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"export_responses\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyIDString + "\", \"" + "pdf" + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
			String response3 = minire3.getAsString("result");
			 */


			for(Participant pa : surveyGlobal.getParticipants()) {
				String surveyResponseID;

				String content = pa.getAnswersString();
				System.out.println(content);

				// If part of response already at LimeSurvey, update response
				if(pa.getSurveyResponseID() != null){
					surveyResponseID = pa.getSurveyResponseID();
					System.out.println(surveyResponseID);
					String contentFilled = "{" + content + ",\"token\":\"" + pa.getEmail() + "\",\"id\":\"" + surveyResponseID + "\"}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"update_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyIDString + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					String response2 = minire2.getAsString("result");
					System.out.println(response2);
				} else{ // If new response, add new response and return ID
					String contentFilled = "{" + content + ",\"token\":\"" + pa.getEmail() + "\"}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyIDString + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					surveyResponseID = minire2.getAsString("result");
					pa.setSurveyResponseID(surveyResponseID);
					System.out.println(surveyResponseID);
				}


			}


			response.put("text", "Passed back results to LimeSurvey.");
			return Response.ok().entity(response).build();


		}
		catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in sendResultsBackToLimesurvey try block.");
		return Response.ok().entity(response).build();


	}



	@POST
	@Path("/reminderRoutine")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response reminderRoutine(String input) {
		JSONObject response = new JSONObject();
		response.put("contactList", "");
		response.put("contactText", "");

		String separator = "";

		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);


		// Get all participants that have not answered for 3 days
		for(Participant pa : surveyGlobal.getParticipants()){

			// Only if participant has not already completed the survey
			if(!pa.getCompletedSurvey()){

				// Participant has not started survey
				System.out.println(pa.getUnaskedQuestions().size());
				Integer unSize = pa.getUnaskedQuestions().size();
				Integer unansweredQuetsions = unSize + 1;
				Integer allSize = surveyGlobal.getSortedQuestionIds().size();
				System.out.println(surveyGlobal.getSortedQuestionIds().size());
				if(unSize.equals(allSize)){
					response.put("contactList", response.get("contactList") + separator + pa.getEmail());
					response.put("contactText", response.get("contactText") + separator + "Hello again, it would be nice if you would start the survey. :)");
					separator = ",";
				} else {
					LocalDateTime lastDT = pa.getLastTimeActive();
					long hoursDifference = ChronoUnit.HOURS.between(lastDT, LocalDateTime.now());

					//seconds for testing, TODO delete later
					long secsDifference = ChronoUnit.SECONDS.between(lastDT, LocalDateTime.now());
					if(hoursDifference > 72 || secsDifference > 15){
						response.put("contactList", response.get("contactList") + separator + pa.getEmail());
						response.put("contactText", response.get("contactText") + separator + "Hello again, please continue with your survey. There are only " + unansweredQuetsions + " questions left! :)");
						separator = ",";
					}
				}

			}

		}

		System.out.println(response.toString());

		response.put("text", "Participants have been reminded.");
		return Response.ok().entity(response).build();

	}






















}
