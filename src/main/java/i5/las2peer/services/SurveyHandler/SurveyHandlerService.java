package i5.las2peer.services.SurveyHandler;

import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.ServiceException;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import i5.las2peer.restMapper.annotations.ServicePath;

import i5.las2peer.services.SurveyHandler.database.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

import java.util.Properties;
import java.util.logging.Level;
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
@ManualDeployment
// TODO Your own service class
public class SurveyHandlerService extends RESTService {

	// TODO change the remaining surveyGlobal
	private static Survey surveyGlobal;
	private static ArrayList<Survey> allSurveys = new ArrayList<>();
	private static boolean firstStartUp = true;
	private String databaseUser = "";
	private String databasePassword = "";
	private String databaseName = "";
	private String databaseHost = "";
	private int databaseTypeInt;
	private int databasePort;

	private static SQLDatabase database; // The database instance to write to.

	// Look through global survey list for surveyID, which is unique for each survey
	public static Survey getSurveyBySurveyID(String surveyID){
		for (Survey s : allSurveys){
			if (s.getSid().equals(surveyID)){
				return s;
			}
		}
		return null;
	}

	public static ArrayList<Survey> getAllSurveys(){
		return allSurveys;
	}

	@Override
	public void onStart() throws ServiceException {
		// instantiate a database manager to handle database connection pooling and credentials
		getLogger().log(Level.INFO, "Service started");

		try{
			// Set up database
			SQLDatabaseType databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);
			System.out.println(databaseType + " " + databaseUser + " " + databasePassword + " "
					+ databaseName + " " + databaseHost + " " + databasePort);

			database = new SQLDatabase(databaseType, databaseUser, databasePassword, databaseName,
					databaseHost, databasePort);

			// Test database connection
			try {
				Connection con = database.getDataSource().getConnection();
				con.close();
			} catch (SQLException e) {
				System.out.println("Failed to Connect: " + e.getMessage());
			}

			// drop all for clean testing
			//System.out.println("Dropped all got result: " +SurveyHandlerServiceQueries.dropAll(database));

			// TODO better handling, if tables are missing.
			// Check if required table exists in our database
			for (String tableName : SurveyHandlerServiceQueries.requiredTables){
				if(!SurveyHandlerServiceQueries.tablesExist(tableName, database)){
					System.out.println("Table " + tableName + " not found. Creating table...");
					// Create table
					boolean created = SurveyHandlerServiceQueries.createTable(tableName, database);
					System.out.println("Created table had result: " + created);
				}else{
					System.out.println("Table " + tableName + " found.");
				}
			}

			// Load internal data structures with values from database
			ArrayList<Survey> allSurveysFromDB = SurveyHandlerServiceQueries.getSurveysFromDB(database);
			for (Survey sur : allSurveysFromDB){
				sur.setDatabase(database);
				// init questions
				ArrayList<Question> allQForSurvey = SurveyHandlerServiceQueries.getSurveyQuestionsFromDB(sur.getSid(), database);
				System.out.println(allQForSurvey);
				sur.initQuestionsFromDB(allQForSurvey);
				// init participants
				ArrayList<Participant> allPForSurvey = SurveyHandlerServiceQueries.getSurveyParticipantsFromDB(sur.getSid(), database);
				sur.initParticipantsFromDB(allPForSurvey);
				// init answers for every participant
				for (Participant tempP : sur.getParticipants()){
					ArrayList<Answer> allAforP = SurveyHandlerServiceQueries.getAnswersForParticipantFromDB(sur.getSid(), tempP.getPid(), database);
					System.out.println(allAforP);
					sur.initAnswersForParticipantFromDB(tempP, allAforP);

				}
				// add new survey to global list
				allSurveys.add(sur);
			}
		} catch (Exception e){
			e.printStackTrace();
			return;
		}

	}

	public SurveyHandlerService(){
		// Read and set properties values
		setFieldValues();
	}
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
			LocalDate dateNow = LocalDate.now();
			LocalTime timeNow = LocalTime.now();

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String intent = bodyInput.getAsString("intent");
			String channel = bodyInput.getAsString("channel");
			String surveyID = bodyInput.getAsString("surveyID");
			String senderEmail = bodyInput.getAsString("email");
			String token = bodyInput.getAsString("slackToken");
			// This intent is needed to check if the message received was send by clicking on a button as an answer
			String buttonIntent = bodyInput.getAsString("buttonIntent");
			System.out.println("buttonIntent: " + buttonIntent);

			// check if senderEmail is actual email or userid
			System.out.println("senderEMail: " + senderEmail);
			if(!senderEmail.contains("@")){
				System.out.println("sender email is user id");
				senderEmail = getSlackEmailBySlackId(senderEmail, token);
			}

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			System.out.println(currSurvey);

			// Check if survey is set up already
			if (Objects.isNull(currSurvey)){
				response.put("text", "Please wait for the survey to be initialized.");
				return Response.ok().entity(response).build();
			}

			// Check if survey has expiration date and if survey has expired
			if(currSurvey.getExpires() != null){
				// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
				String expireDate = currSurvey.getExpires().split("\\s")[0];
				String expireTime = currSurvey.getExpires().split("\\s")[1];
				System.out.println(expireDate + " and expires at " + dateNow);
				System.out.println(expireTime + " and expires at " + timeNow);
				if(dateNow.isAfter(LocalDate.parse(expireDate))){
					if(timeNow.isAfter(LocalTime.parse(expireTime)))
						response.put("text", "The survey is no longer active.");
					return Response.ok().entity(response).build();
				}
			}

			// Check if message was sent by someone known
			if (Objects.isNull(currSurvey.findParticipant(senderEmail))){
				// participant does not exist, create a new one
				Participant newParticipant = new Participant(senderEmail);
				newParticipant.setLasttimeactive(LocalDateTime.now().toString());
				currSurvey.addParticipant(newParticipant);
				SurveyHandlerServiceQueries.addParticipantToDB(newParticipant, database);
			}

			// Get the existing participant
			Participant currParticipant = currSurvey.findParticipant(senderEmail);
			System.out.println(currParticipant.getChannel());
			if(currParticipant.getChannel() == null){
				currParticipant.setChannel(channel);
				SurveyHandlerServiceQueries.updateParticipantInDB(currParticipant, database);
			}
			System.out.println(currParticipant.getChannel());
			String message = bodyInput.getAsString("msg");

			//Set the time the participant answered to check later if needed to be reminded to finish survey
			currParticipant.setLasttimeactive(LocalDateTime.now().toString());

			// Get the next action
			return currParticipant.calculateNextAction(intent, message, buttonIntent);


		} catch (ParseException e) {
			e.printStackTrace();
		}
		response.put("text", "Something went wrong in takingSurvey try block.");
		return Response.ok().entity(response).build();
	}




	private void setUpSurvey(String input){
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
		try {
			JSONObject bodyInput = (JSONObject) p.parse(input);

			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String surveyID = bodyInput.getAsString("surveyID");
			String uri = bodyInput.getAsString("uri");
			String adminmail = bodyInput.getAsString("adminmail");

			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			// Get questions from limesurvey
			ClientResponse minires2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
			JSONArray ql = (JSONArray) minire2.get("result");


			// Get question properties (includes answeroptions, logics and questiontype)
			JSONArray qlProperties = new JSONArray();
			for(Object jo : ql){
				String qid = ((JSONObject) jo).getAsString("qid");
				ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"get_question_properties\", \"params\": [ \"" + sessionKeyString + "\", \"" + qid + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
				JSONObject qProperties = (JSONObject) minire3.get("result");
				qlProperties.add(qProperties);
			}


			// Create a new survey object
			Survey newSurvey = new Survey(surveyID);
			newSurvey.setAdminmail(adminmail);
			newSurvey.initData(qlProperties);

			// Get survey title and add to survey
			ClientResponse minires4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \"" + sessionKeyString + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire4 = (JSONObject) p.parse(minires4.getResponse());
			JSONArray sl = (JSONArray) minire4.get("result");
			for (Object i : sl) {
				if (((JSONObject) i).getAsString("sid").equals(surveyID)) {
					newSurvey.addTitle( ((JSONObject) i).getAsString("surveyls_title"));
					newSurvey.setExpires( ((JSONObject) i).getAsString("expires"));
					break;
				}
			}

			// Check if adding title worked
			if (Objects.isNull(newSurvey.getTitle())){
				System.out.println("Failed to add title. Aborting survey creation...");
			} else {
				System.out.println(newSurvey.getTitle());
				//surveyGlobal = newSurvey;
				allSurveys.add(newSurvey);
				SurveyHandlerServiceQueries.addSurveyToDB(newSurvey, database);
				newSurvey.safeQuestionsToDB(database);
				newSurvey.setDatabase(database);
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
			String intent = bodyInput.getAsString("intent");
			String surveyID = bodyInput.getAsString("surveyID");
			String token = bodyInput.getAsString("slackToken");
			String msg = bodyInput.getAsString("msg");
			System.out.println("token is: " + token);

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);


			//set up survey, if not yet done
			if(Objects.isNull(currSurvey)){
				System.out.println("No survey exists for id "+ surveyID + ". Creating...");
				setUpSurvey(input);
				// See if survey is set up now
				currSurvey = getSurveyBySurveyID(surveyID);
				if (Objects.isNull(currSurvey)){
					System.out.println("ERROR: Could not set up survey, still null.");
					response.put("text", "ERROR: Could not set up survey. Reason unknown.");
					return Response.ok().entity(response).build();
				}

				if (currSurvey.getSortedQuestionIds().size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}


			//TODO: check if rocket chat passes on email of user
			if (!(bodyInput.getAsString("adminmail").equals(bodyInput.getAsString("email")))) {
				Response res = takingSurvey(input);
				return res;
			}

			if (intent.equals("add_participant")) {
				boolean added = true;

				if(msg.contains(" ")){
					// remove all spaces
					msg.replaceAll(" ","");
				}

				// Check if it is a list of userids or emails, then add all of them
				if(msg.contains(",")){
					for(String email : msg.split(",")){
						// Check if it is a slack userid
						if(email.contains("<")){
							email = getSlackEmailBySlackId(msg, token);
						}
						System.out.println("email thats going to be added: " + email);
						Participant newParticipant = new Participant(email);
						boolean thisAdded = currSurvey.addParticipant(newParticipant);
						SurveyHandlerServiceQueries.addParticipantToDB(newParticipant, database);
						if(!thisAdded){
							added = false;
						}
					}
				} else{
					// Only one participant is added

					// Check if it is a slack userid
					if(msg.contains("<")){
						msg = getSlackEmailBySlackId(msg, token);
					}

					Participant newParticipant = new Participant(msg);
					added = currSurvey.addParticipant(newParticipant);
					SurveyHandlerServiceQueries.addParticipantToDB(newParticipant, database);
					System.out.println(currSurvey.findParticipant(newParticipant.getEmail()));
				}
				response.put("text", "Adding participant(s), got result: " + added);
				System.out.println(currSurvey.getParticipants().toString());
				System.out.println(currSurvey.getParticipants().size());
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_participants")) {
				response.put("text", currSurvey.getParticipantsEmails() + ". Currently there are " + currSurvey.getParticipants().size() + " participants in this survey");
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_answers")) {
				System.out.println(currSurvey.getAnswersStringFromAllParticipants());
				response.put("text", "The current answers are: " + currSurvey.getAnswersStringFromAllParticipants());
				return Response.ok().entity(response).build();
			}
			else if(intent.equals("delete_participant")){
				System.out.println("deleting participant...");
				// expected form deleteuser: email or userid
				try{
					// not the best way to ensure no one gets deleted by accident
					if(msg.contains("deleteuser: ")){
						String userIdentificator = msg.split(" ")[1];
						String email;
						if(String.valueOf(userIdentificator.charAt(0)).equals("<")){
							// if its a userID
							email = getSlackEmailBySlackId(userIdentificator, token);
						} else{
							// email
							email = userIdentificator;
						}
						Participant participant = currSurvey.findParticipant(email);
						if(participant != null){
							// TODO delete answers in LimeSurvey
							// TODO delete answers in database (will not be shown if participant is deleted)
							// remove from database
							SurveyHandlerServiceQueries.deleteParticipantFromDB(participant, database);
							currSurvey.deleteParticipant(participant);
						}

					}
					response.put("text", "Participant successfully deleted.");
					return Response.ok().entity(response).build();
				} catch (Exception exception){
					exception.printStackTrace();
					response.put("text", "Deleting participant failed.");
					return Response.ok().entity(response).build();
				}

			}
			else if(intent.equals("start_survey")){
				String emails = "";
				for (Participant pa : currSurvey.getParticipants()) {
					// contact all participants that did not yet get contacted or contacted themselves
					if(!(pa.isParticipantcontacted())) { //!(pa.getEmail().equals("null") &&
						System.out.println("inside has email and ");
						emails += pa.getEmail() + ",";
						pa.setParticipantcontacted(true);
					}
					pa.setLasttimeactive(LocalDateTime.now().toString());
				}
				if(emails.length() > 0){
					emails.substring(0, emails.length() -1); //remove last separator, only if there are several participants

					System.out.println(emails);
					int questionsInSUrvey = currSurvey.getSortedQuestions().size();
					String welcomeString = "Would you like to start the survey \"" + currSurvey.getTitle() + "\"? There are " + questionsInSUrvey + " questions in this survey.";
					String explanation = " To skip a question just send \"skip\", you will be able to answer them later if you want.";
					response.put("text", welcomeString + explanation);
					response.put("contactList", emails);
					return Response.ok().entity(response).build();
				}
				else{
					response.put("text", "Please add participants to start the survey. No participants found that have not been contacted yet.");
					return Response.ok().entity(response).build();
				}

			}
			else {
				response.put("text", "intent not recognized");
				return Response.ok().entity(response).build();
			}

		} catch (Exception e) {
			e.printStackTrace();
			response.put("text", "Something went wrong in adminSurvey try block.");
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

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String surveyID = bodyInput.getAsString("surveyID");
			String uri = bodyInput.getAsString("uri");

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			if(Objects.isNull(currSurvey)){
				System.out.println("No survey exists for id "+ surveyID + ". Creating...");
				setUpSurvey(input);
				// See if survey is set up now
				currSurvey = getSurveyBySurveyID(surveyID);
				if (Objects.isNull(currSurvey)){
					System.out.println("ERROR: Could not set up survey, still null.");
					response.put("text", "ERROR: Could not set up survey. Reason unknown.");
					return Response.ok().entity(response).build();
				}

				if (currSurvey.getSortedQuestionIds().size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}


			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			/*
			// Export the responses
			ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"export_responses\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\", \"" + "pdf" + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
			String response3 = minire3.getAsString("result");
			 */

			for(Participant pa : currSurvey.getParticipants()) {
				String surveyResponseID;

				String content = pa.getAnswersString();
				System.out.println(content);

				// If part of response already at LimeSurvey, update response
				if(pa.getSurveyResponseID() != null){
					surveyResponseID = pa.getSurveyResponseID();
					System.out.println(surveyResponseID);
					String contentFilled = "{" + content + ",\"token\":\"" + pa.getEmail() + "\",\"id\":\"" + surveyResponseID + "\"}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"update_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					String response2 = minire2.getAsString("result");
					System.out.println(response2);
				} else{ // If new response, add new response and return ID
					String contentFilled = "{" + content + ",\"token\":\"" + pa.getEmail() + "\"}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					surveyResponseID = minire2.getAsString("result");
					pa.setSurveyResponseID(surveyResponseID);
					SurveyHandlerServiceQueries.updateParticipantInDB(pa, currSurvey.database);
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
		String cList = "";
		String cText = "";
		String separator = "";

		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try{
			JSONObject bodyInput = (JSONObject) p.parse(input);
			String surveyID = bodyInput.getAsString("surveyID");
			Survey currSurvey = getSurveyBySurveyID(surveyID);
			System.out.println(bodyInput);
			if(bodyInput.containsKey("reminderAfterHours")){
				System.out.println("contains Key : " + bodyInput);
				Integer timeToRemind = Integer.parseInt(bodyInput.getAsString("reminderAfterHours"));
				System.out.println(timeToRemind);
				// Get all participants that have not answered for the amount of time
				for(Participant pa : currSurvey.getParticipants()){

					// Only if participant has not already completed the survey
					if(!pa.isCompletedsurvey()){
						System.out.println(pa.getUnaskedQuestions().size());
						Integer unSize = pa.getUnaskedQuestions().size();
						// The last question asked, but not answered is not on the list anymore
						Integer unansweredQuestions = unSize + 1;
						Integer allSize = currSurvey.getSortedQuestionIds().size();
						System.out.println(currSurvey.getSortedQuestionIds().size());

						LocalDateTime lastDT = LocalDateTime.parse(pa.getLasttimeactive());
						long hoursDifference = ChronoUnit.HOURS.between(lastDT, LocalDateTime.now());

						//seconds for testing, TODO delete later
						long secsDifference = ChronoUnit.SECONDS.between(lastDT, LocalDateTime.now());
						System.out.println("time gone : " + secsDifference + "how long to wait: " + timeToRemind);
						System.out.println("time gone times 2: " + timeToRemind*2);
						if(hoursDifference > (timeToRemind*2)){
							// do not remind if already reminded 2 times
						} else if(hoursDifference > timeToRemind){ // for testing: || secsDifference > timeToRemind
							// Participant has not started survey
							if(unSize.equals(allSize)){
								cList += separator + pa.getEmail();
								cText += separator + "Hello again! It would be nice if you would start the survey. :)";
								separator = ",";
							}
							// Participant has started, but not finished survey
							else {
								cList += separator + pa.getEmail();
								cText += separator + "Hello again! Please continue with your survey. There are only " + unansweredQuestions + " questions left. :)";
								separator = ",";
							}
						}
					}
				}
			}

			if(cList.length() > 0){
				response.put("contactList", cList);
				response.put("contactText", cText);
			}

			System.out.println(response.toString());

			response.put("text", "No participants to remind.");
			return Response.ok().entity(response).build();

		} catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in reminderRoutine try block.");
		return Response.ok().entity(response).build();

	}


	private String getSlackEmailBySlackId(String userId, String token){
		System.out.println("inside getSlackEMailbyuserid...");
		JSONParser p = new JSONParser();
		System.out.println(userId);
		//remove <@ and > at the beginning and end
		if(userId.contains("<")){
			userId = userId.substring(2, userId.length() - 1);
		}

		try{
			// slack api call to get email for user id
			String urlParameters = "token=" + token + "&user=" + userId;
			//String urlParameters  = "param1=data1&param2=data2&param3=data3";
			byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int postDataLength = postData.length;
			String request = "https://slack.com/api/users.info";
			URL url = new URL( request );
			HttpURLConnection conn= (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString(postDataLength ));
			conn.setUseCaches(false);
			try(DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
				wr.write( postData );
			}

			InputStream stream = conn.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 8);
			String result = reader.readLine();
			System.out.println(result);

			/*
			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint("https://slack.com/api/users.info");
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", "https://slack.com/api/users.info", "{\"token\":" + token + ", \"user\":" + userId + "}", MediaType.APPLICATION_FORM_URLENCODED, "", head);
			System.out.println("minires: " + minires.getResponse());
			String cResult = java.net.URLDecoder.decode(minires.getResponse(), StandardCharsets.UTF_8.name());
			System.out.println(cResult);
			 */

			// getting email from json
			JSONObject resultJ = (JSONObject) p.parse(result);
			String userS = resultJ.getAsString("user");
			JSONObject userJson = (JSONObject) p.parse(userS);
			String profileS = userJson.getAsString("profile");
			JSONObject profileJson = (JSONObject) p.parse(profileS);
			String email = profileJson.getAsString("email");
			System.out.println("email: " + email);

			return email;

		} catch(Exception e){
			e.printStackTrace();
			return "";
		}

	}




















}
