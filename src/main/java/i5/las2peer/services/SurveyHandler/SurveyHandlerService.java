package i5.las2peer.services.SurveyHandler;

import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.ServiceException;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;

import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
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

	private static SQLDatabase database; // The database instance to write to.

	// Look through global survey list for botname, which is unique for each survey
	public static Survey getSurveyByBotname(String botname){
		for (Survey s : allSurveys){
			if (s.getBotname().equals(botname)){
				return s;
			}
		}
		return null;
	}

	@Override
	public void onStart() throws ServiceException {
		// read and set properties values
		// IF THE SERVICE CLASS NAME IS CHANGED, THE PROPERTIES FILE NAME NEED TO BE CHANGED TOO!
		setFieldValues();

		// instantiate a database manager to handle database connection pooling and credentials
		getLogger().log(Level.INFO, "Service started");

		try{
			// Read properties file
			Properties tempProp = new Properties();
			FileInputStream propsFile = new FileInputStream("");
			tempProp.load(propsFile);
			String databaseUser = tempProp.getProperty("databaseUser");
			String databasePassword = tempProp.getProperty("databasePassword");
			String databaseName = tempProp.getProperty("databaseName");
			String databaseHost = tempProp.getProperty("databaseHost");
			int databaseTypeInt = Integer.parseInt(tempProp.getProperty("databaseTypeInt")); // See SQLDatabaseType for more information
			int databasePort = Integer.parseInt(tempProp.getProperty("databasePort"));

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
		super();
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
			LocalDateTime now = LocalDateTime.now();
			System.out.println(now);

			// Check if survey has expired
			/*
			String expireDate = surveyGlobal.getExpires();
			String expireTime = surveyGlobal.getExpires();
			if(){
				response.put("text", "The survey is no longer active.");
				return Response.ok().entity(response).build();
			}
			 */

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String intent = bodyInput.getAsString("intent");
			String channel = bodyInput.getAsString("channel");

			String botname = bodyInput.getAsString("botName");

			// find correct survey
			Survey currSurvey = getSurveyByBotname(botname);

			System.out.println(currSurvey);

			// Check if survey is set up already
			if (Objects.isNull(currSurvey)){
				response.put("text", "Please wait for the survey to be initialized.");
				return Response.ok().entity(response).build();
			}


			// Check if message was sent by someone known
			String senderEmail = bodyInput.getAsString("email");
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
			return currParticipant.calculateNextAction(intent, message);


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
			String surveyIDString = bodyInput.getAsString("surveyIDString");
			int surveyID = Integer.parseInt(surveyIDString);
			String uri = bodyInput.getAsString("uri");
			String adminmail = bodyInput.getAsString("adminmail");
			String botname = bodyInput.getAsString("botName");

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
			Survey newSurvey = new Survey(surveyIDString);
			newSurvey.setBotname(botname);
			newSurvey.setAdminmail(adminmail);
			newSurvey.initData(qlProperties);

			// Get survey title and add to survey
			ClientResponse minires4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \"" + sessionKeyString + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire4 = (JSONObject) p.parse(minires4.getResponse());
			JSONArray sl = (JSONArray) minire4.get("result");
			for (Object i : sl) {
				if (((JSONObject) i).getAsString("sid").equals(surveyIDString)) {
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
	@Path("/slackAttachment")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	public Response attachment(String input) {
		System.out.println(input);
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {
			String result = java.net.URLDecoder.decode(input, StandardCharsets.UTF_8.name());
			System.out.println(result);

			//slack adds payload= in front of the result, so deleting that to parse it to json
			result = result.substring(8);
			System.out.println(result);

			JSONObject bodyInput = (JSONObject) p.parse(result);
			System.out.println(bodyInput);

			String channelString = bodyInput.getAsString("channel");
			JSONObject channelJSON = (JSONObject) p.parse(channelString);
			String channel = channelJSON.getAsString("id");
			System.out.println(channel);

			String actionString = bodyInput.getAsString("actions");
			System.out.println(actionString);
			JSONArray actionJSON = (JSONArray) p.parse(actionString);

			// The actions array has only one entry when it is only possible to click one button
			JSONObject first = (JSONObject) actionJSON.get(0);
			String message = "";
			if(first.getAsString("type").equals("button")){
				String textString = first.getAsString("text");
				JSONObject textJson = (JSONObject) p.parse(textString);
				message = textJson.getAsString("text");
				System.out.println(message);
			}

			else if (first.getAsString("type").equals("checkboxes")){
				String selectedOptions = first.getAsString("selected_options");
				JSONArray textJsonArray = (JSONArray) p.parse(selectedOptions);
				for(Object o : textJsonArray){
					JSONObject jo = (JSONObject) o;
					String textString = jo.getAsString("text");
					JSONObject textJson = (JSONObject) p.parse(textString);
					message += textJson.getAsString("text") + ",";
					System.out.println(message);
				}

				// Remove last ,
				message.substring(0, message.length() - 1);
				System.out.println(message);
			}

			// TODO submit button

			String intent = "";


			// Get the existing participant
			Participant currParticipant = surveyGlobal.findParticipantByChannel(channel);
			System.out.println(currParticipant.getChannel());

			//Set the time the participant answered to check later if needed to be reminded to finish survey
			currParticipant.setLasttimeactive(LocalDateTime.now().toString());

			// Get the next action
			return currParticipant.calculateNextAction(intent, message);

		} catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in slackAttachments try block.");
		return Response.ok().entity(response).build();

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
			String botname = bodyInput.getAsString("botName");
			Survey currSurvey = getSurveyByBotname(botname);
			//set up survey, if not yet done

			if(Objects.isNull(currSurvey)){
				System.out.println("No survey exists for bot "+ botname + ". Creating...");
				setUpSurvey(input);
				// See if survey is set up now
				currSurvey = getSurveyByBotname(botname);
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
				response.put("text", "only admin is allowed to do that");
				return Response.ok().entity(response).build();
			}

			if (intent.equals("add_participant")) {
				boolean added = true;
				// Check if it is a list of emails, then add all of them
				if(bodyInput.getAsString("msg").contains(",")){
					for(String s : bodyInput.getAsString("msg").split(",")){
						Participant newParticipant = new Participant(s);
						boolean thisAdded = currSurvey.addParticipant(newParticipant);
						SurveyHandlerServiceQueries.addParticipantToDB(newParticipant, database);
						if(!thisAdded){
							added = false;
						}
					}
				} else{
					// Only one participant is added
					Participant newParticipant = new Participant(bodyInput.getAsString("msg"));
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
			else if(intent.equals("start_survey")){
				String emails = "";
				for (Participant pa : currSurvey.getParticipants()) {
					if(!(pa.getEmail().equals("null"))){ //&& !(pa.getParticipantContacted())) {
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
			// Export the responses
			ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"export_responses\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyIDString + "\", \"" + "pdf" + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
			String response3 = minire3.getAsString("result");
			 */
			//TODO: search survey by surveyid or some other unique identifier
			// Survey currSurvey = getSurveyByBotname()

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
		String cList = "";
		String cText = "";
		String separator = "";

		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try{
			JSONObject bodyInput = (JSONObject) p.parse(input);
			System.out.println(bodyInput);
			if(bodyInput.containsKey("reminderAfterHours")){
				System.out.println("contains Key : " + bodyInput);
				Integer timeToRemind = Integer.parseInt(bodyInput.getAsString("reminderAfterHours"));
				System.out.println(timeToRemind);
				// Get all participants that have not answered for the amount of time
				for(Participant pa : surveyGlobal.getParticipants()){

					// Only if participant has not already completed the survey
					if(!pa.isCompletedsurvey()){

						System.out.println(pa.getUnaskedQuestions().size());
						Integer unSize = pa.getUnaskedQuestions().size();
						// The last question asked, but not answered is not on the list anymore
						Integer unansweredQuestions = unSize + 1;
						Integer allSize = surveyGlobal.getSortedQuestionIds().size();
						System.out.println(surveyGlobal.getSortedQuestionIds().size());

						LocalDateTime lastDT = LocalDateTime.parse(pa.getLasttimeactive());
						long hoursDifference = ChronoUnit.HOURS.between(lastDT, LocalDateTime.now());

						//seconds for testing, TODO delete later
						long secsDifference = ChronoUnit.SECONDS.between(lastDT, LocalDateTime.now());
						System.out.println("time gone : " + secsDifference + "how long to wait: " + timeToRemind);
						if(hoursDifference > timeToRemind || secsDifference > timeToRemind){
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






















}
