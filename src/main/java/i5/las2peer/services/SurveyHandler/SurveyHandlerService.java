package i5.las2peer.services.SurveyHandler;

import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.ServiceException;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.logging.L2pLogger;
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
import org.glassfish.jersey.server.JSONP;
import org.junit.Assert;

import java.util.Properties;
import java.util.logging.Level;

import java.util.concurrent.locks.ReentrantLock;
/**
 * SurveyHandlerService
 *
 * A service to conduct surveys with a chatbot created with the SBF.
 *
 */





@Api
@SwaggerDefinition(
		info = @Info(
				title = "las2peer Survey Handler Service",
				version = "1.0.0",
				description = "A las2peer Survey Service, that handles the content of surveys.",
				termsOfService = "",
				contact = @Contact(
						name = "Theresa Täuber",
						url = "",
						email = "theresa.taeuber@rwth-aachen.de"),
				license = @License(
						name = "",
						url = "")))
@ServicePath("/SurveyHandler")
@ManualDeployment

public class SurveyHandlerService extends RESTService {

	private static ArrayList<Survey> allSurveys = new ArrayList<>();
	private static boolean firstStartUp = true;
	private String databaseUser = "";
	private String databasePassword = "";
	private String databaseName = "";
	private String databaseHost = "";
	private String url = "";
	private int databaseTypeInt;
	private int databasePort;

	// for logging
	private Context l2pcontext = null;
	public void setL2pcontext(Context l2pcontext) {
		this.l2pcontext = l2pcontext;
	}


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

	public static String setAdminlanguage(String surveyID, String language){
		for (Survey s : allSurveys){
			if (s.getSid().equals(surveyID)){
				// all surveys with same id have the same admin, so same adminlanguage
				s.setAdminLanguage(language);
			}
		}
		return null;
	}

	public static void deleteSurvey(String surveyID, String language){
		for (Survey s : allSurveys){
			if (s.getSid().equals(surveyID)){
				SurveyHandlerServiceQueries.deleteSurveyFromDB(getSurveyBySurveyID(surveyID), database);
			}
		}
		allSurveys.remove(getSurveyBySurveyID(surveyID));
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
				//System.out.println(allQForSurvey);
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

	@POST
	@Path("/takingSurvey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Return the next question of the survey.",
			notes = "")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "survey taking request handled")})
	public Response takingSurvey(String input) {
		System.out.println("url: " + url);
		SurveyHandlerService surveyHandlerService = (SurveyHandlerService) Context.get().getService();
		Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, input);
		System.out.println("log: " + Context.get());

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try{
			LocalDate dateNow = LocalDate.now();
			LocalTime timeNow = LocalTime.now();

			JSONObject bodyInput = (JSONObject) p.parse(input);
			System.out.println("received message: " + bodyInput);
			String intent = bodyInput.getAsString("intent");
			String channel = bodyInput.getAsString("channel");
			String surveyID = bodyInput.getAsString("surveyID");
			String beginningText = "";
			if(bodyInput.containsKey("beginningText")){
				System.out.println("has beginningText");
				beginningText = bodyInput.getAsString("beginningText");
			}
			String senderEmail = "";

			String token = ""; // for rocket chat none in this service is needed, so length 0
			if(bodyInput.containsKey("slackToken")){
				token = bodyInput.getAsString("slackToken");
			}
			String messageTs = bodyInput.getAsString("time");
			boolean ls = bodyInput.containsKey("NameOfUser");

			// This intent is needed to check if the message received was send by clicking on a button as an answer
			String buttonIntent = bodyInput.getAsString("buttonIntent");
			System.out.println("buttonIntent: " + buttonIntent);

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			String followupSurveyID;
			Survey followUpSurvey = new Survey("");

			if(bodyInput.containsKey("followupSurveyID")){
				followupSurveyID = bodyInput.getAsString("followupSurveyID");
				followUpSurvey = getSurveyBySurveyID(followupSurveyID);
			}


			try{
				senderEmail = bodyInput.getAsString("email");
				System.out.println("senderEMail: " + senderEmail);

				// check if senderEmail is actual email or userid
				if(!senderEmail.contains("@")){
					System.out.println("sender email is user id");
					senderEmail = getSlackEmailBySlackId(senderEmail, token);
					System.out.println("senderEMail: " + senderEmail);
				}
			} catch(Exception e){
				senderEmail = currSurvey.findParticipantByChannel(channel).getEmail();
				System.out.println("senderEMail: " + senderEmail);
			}

			System.out.println("survey: " + currSurvey);
			System.out.println("followup: " + followUpSurvey);


			// Check if survey is set up already
			if (Objects.isNull(currSurvey)){
				response.put("text", "Please wait for the survey to be initialized.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}

			// Check if survey has expiration date and if survey has expired
			if(currSurvey.getExpires() != null){
				if(ls){
					// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
					String expireDate = currSurvey.getExpires().split("\\s")[0];
					String expireTime = currSurvey.getExpires().split("\\s")[1];
					System.out.println(expireDate + " and expires at " + dateNow);
					System.out.println(expireTime + " and expires at " + timeNow);
					if(dateNow.isAfter(LocalDate.parse(expireDate))){
						if(timeNow.isAfter(LocalTime.parse(expireTime)))
							System.out.println("survey not active anymore");
							response.put("text", "The survey is no longer active.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
				} else{
					// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
					String expireDate = currSurvey.getExpires().split("T")[0];
					String expireTime = currSurvey.getExpires().split("T")[1];
					System.out.println(expireDate + " and expires at " + dateNow);
					System.out.println(expireTime + " and expires at " + timeNow);
					if(dateNow.isAfter(LocalDate.parse(expireDate))){
						if(timeNow.isAfter(LocalTime.parse(expireTime)))
							System.out.println("survey not active anymore");
							response.put("text", "The survey is no longer active.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
				}


			}

			// Check if survey has expiration date and if survey has expired
			if(currSurvey.getStartDT() != null){
				if(ls){
					// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
					String startDate = currSurvey.getStartDT().split("\\s")[0];
					String startTime = currSurvey.getStartDT().split("\\s")[1];
					System.out.println(startDate + " and starts at " + dateNow);
					System.out.println(startTime + " and starts at " + timeNow);
					if(dateNow.isBefore(LocalDate.parse(startDate))){
						if(timeNow.isBefore(LocalTime.parse(startTime)))
							System.out.println("survey not yet active");
							response.put("text", "The survey is not yet active.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
				} else{
					// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
					String startDate = currSurvey.getStartDT().split("T")[0];
					String startTime = currSurvey.getStartDT().split("T")[1];
					System.out.println(startDate + " and starts at " + dateNow);
					System.out.println(startTime + " and starts at " + timeNow);
					if(dateNow.isBefore(LocalDate.parse(startDate))){
						if(timeNow.isBefore(LocalTime.parse(startTime)))
							System.out.println("survey not yet active");
							response.put("text", "The survey is not yet active.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
				}

			}

			// Check if message was sent by someone we only knew the channel of, but now also the email
			if(Objects.nonNull(currSurvey.findParticipant(channel))){
				// after setting the channel last time now we can set email, since the email gets send the second time a participants sents something
				Participant tempP = currSurvey.findParticipant(channel);
				tempP.setEmail(senderEmail);
				SurveyHandlerServiceQueries.updateParticipantInDB(tempP, database);
				tempP.setPid(senderEmail);
				SurveyHandlerServiceQueries.updateParticipantsPidInDB(tempP, database);
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
			System.out.println("ts: " + messageTs);
			JSONObject currMessage = new JSONObject();
			JSONObject prevMessage = new JSONObject();

			if(bodyInput.containsKey("currMessage") && bodyInput.containsKey("previousMessage")){
				currMessage = (JSONObject) p.parse(bodyInput.getAsString("currMessage"));
				prevMessage = (JSONObject) p.parse(bodyInput.getAsString("previousMessage"));
			}

			//
			boolean secondSurvey = false;
			System.out.println("token: " + token + " size: " + token.length());

			// check which messenger is used
			boolean slack = false;
			if(token.length() > 0){
				// a slack token is set
				slack = true;
			}

			System.out.println("using slack: " + slack);

			// check if there is a followup survey, if not sid is ""
			if(followUpSurvey.getSid().length() > 0){
				// check if the participant is done with the first survey
				if(currParticipant.isCompletedsurvey()){
					currParticipant = followUpSurvey.findParticipant(senderEmail);
					if(Objects.isNull(currParticipant)){
						currParticipant = currSurvey.findParticipant(senderEmail);
						if(currParticipant.participantChangedAnswer(messageTs, currMessage, prevMessage)){
							String changedAnswer = "Your answer has been changed sucessfully.";
							String answerNotFittingQuestion = "";
							return currParticipant.updateAnswer(intent, message, messageTs, currMessage, prevMessage, changedAnswer, token, slack);
						}
					}

					// if the participant is done with first survey, check if already participant in second survey
					currParticipant = followUpSurvey.findParticipant(senderEmail);
					secondSurvey = true;
					if(Objects.isNull(currParticipant)){
						System.out.println("creating new participant for follow up");
						// participant does not exist for new survey, create a new one
						currParticipant = new Participant(senderEmail);
						followUpSurvey.addParticipant(currParticipant);
						SurveyHandlerServiceQueries.addParticipantToDB(currParticipant, database);
					}
					if(currParticipant.getChannel() == null){
						currParticipant.setChannel(channel);
						SurveyHandlerServiceQueries.updateParticipantInDB(currParticipant, database);
					}

					boolean active = true;

					// Check if survey has expiration date and if survey has expired
					if(currSurvey.getExpires() != null){
						// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
						String expireDate = currSurvey.getExpires().split("\\s")[0];
						String expireTime = currSurvey.getExpires().split("\\s")[1];
						if(dateNow.isAfter(LocalDate.parse(expireDate))){
							if(timeNow.isAfter(LocalTime.parse(expireTime)))
								active = false;
						}
					}

					// Check if survey has expiration date and if survey has expired
					if(currSurvey.getStartDT() != null){
						// getting the date in format yyyy-mm-dd and time in format hh:mm:ss
						String startDate = currSurvey.getStartDT().split("\\s")[0];
						String startTime = currSurvey.getStartDT().split("\\s")[1];
						if(dateNow.isBefore(LocalDate.parse(startDate))){
							if(timeNow.isBefore(LocalTime.parse(startTime)))
								active = false;
						}
					}

					// check if survey is currently active
					if(!active){
						// survey is not active yet, so get participant infos from first survey again
						currSurvey = getSurveyBySurveyID(surveyID);
						currParticipant = currSurvey.findParticipant(senderEmail);
					}
				}
			}


			//Set the time the participant answered to check later if needed to be reminded to finish survey
			currParticipant.setLasttimeactive(LocalDateTime.now().toString());

			// Get the next action
			return currParticipant.calculateNextAction(intent, message, buttonIntent, messageTs, currMessage, prevMessage, token, secondSurvey, beginningText);


		} catch (ParseException e) {
			e.printStackTrace();
		}
		response.put("text", "Something went wrong in takingSurvey try block.");
		return Response.ok().entity(response).build();
	}

	private void setUpLimeSurvey(String username, String password, String surveyID, String uri, String adminmail){
		try{
			JSONParser p = new JSONParser();
			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			// Get survey language properties
			ClientResponse minires5 = mini.sendRequest("POST", uri, ("{\"method\": \"get_survey_properties\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire5 = (JSONObject) p.parse(minires5.getResponse());
			JSONObject sp = (JSONObject) minire5.get("result");

			String mainLanguage = sp.getAsString("language");
			System.out.println("main: " + mainLanguage);
			String additionalLanguage = sp.getAsString("additional_languages");
			System.out.println("add: " + additionalLanguage);

			// Get questions from limesurvey for main language
			ClientResponse minires2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
			JSONArray ql = (JSONArray) minire2.get("result");


			// Get question properties (includes answeroptions, logics and questiontype)
			JSONArray qlProperties = new JSONArray();
			JSONArray gList = new JSONArray();
			for(Object jo : ql){
				String qid = ((JSONObject) jo).getAsString("qid");
				ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"get_question_properties\", \"params\": [ \"" + sessionKeyString + "\", \"" + qid + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
				JSONObject qProperties = (JSONObject) minire3.get("result");
				qProperties.put("language", mainLanguage);
				qlProperties.add(qProperties);
				if(!gList.contains(qProperties.getAsString("gid"))){
					gList.add(qProperties.getAsString("gid"));
				}
			}

			System.out.println("props1: " + qlProperties.size());

			// Get question properties for additional language (includes answeroptions, logics and questiontype)
			if(additionalLanguage.length() > 0){
				for(Object jo : ql){
					String qid = ((JSONObject) jo).getAsString("qid");
					ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"get_question_properties\", \"params\": [ \"" + sessionKeyString + "\", \"" + qid + "\", null, \"" + additionalLanguage + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
					JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
					JSONObject qProperties = (JSONObject) minire3.get("result");
					qProperties.put("language", additionalLanguage);
					qlProperties.add(qProperties);
				}
			}

			System.out.println("props2: " + qlProperties.size());

			JSONArray glProperties = new JSONArray();
			for(Object jo : gList){
				// Get question group properties from limesurvey
				ClientResponse minires3 = mini.sendRequest("POST", uri, ("{\"method\": \"get_group_properties\", \"params\": [ \"" + sessionKeyString + "\", \"" + jo.toString() + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire3 = (JSONObject) p.parse(minires3.getResponse());
				JSONObject gl = (JSONObject) minire3.get("result");
				glProperties.add(gl);
			}

			// sort the questiongroups
			HashMap<String, String> t = new HashMap<>();
			for(Object glo : glProperties){
				JSONObject currQLO = (JSONObject) glo;
				String currQOString = currQLO.getAsString("group_order");
				int currQOInt = Integer.parseInt(currQOString);
				t.put(currQLO.getAsString("gid"), String.valueOf(currQOInt-1));
			}

			for(Object q : qlProperties){
				JSONObject currQ = (JSONObject) q;
				currQ.put("group_order",t.get(currQ.getAsString("gid")));
			}


			// Create a new survey object
			Survey newSurvey = new Survey(surveyID);
			newSurvey.setAdminmail(adminmail);
			newSurvey.initLimeSurveyData(qlProperties);

			// Get survey title and add to survey
			ClientResponse minires4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \"" + sessionKeyString + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire4 = (JSONObject) p.parse(minires4.getResponse());
			JSONArray sl = (JSONArray) minire4.get("result");
			for (Object i : sl) {
				if (((JSONObject) i).getAsString("sid").equals(surveyID)) {
					newSurvey.addTitle( ((JSONObject) i).getAsString("surveyls_title"));
					newSurvey.setExpires( ((JSONObject) i).getAsString("expires"));
					newSurvey.setStartDT(((JSONObject) i).getAsString("startdate"));
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
			System.out.println("api calls to limesurvey failed.");
		}
	}

	private void setUpMobsosSurvey(String surveyID, String uri, String adminmail){
		try{
			JSONParser p = new JSONParser();
			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();


			// Get questions from mobsos
			String questionpath = "surveys/" + surveyID + "/questions";
			ClientResponse minires = mini.sendRequest("GET", questionpath, "", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, head);
			System.out.println("minires: " + minires.getResponse());
			JSONObject qlO = (JSONObject) p.parse(minires.getResponse());

			JSONArray ql = new JSONArray();

			Set<String> keysSet = qlO.keySet();
			ArrayList<String> keys = new ArrayList<>();
			for(String s : keysSet){
				keys.add(s);
			}

			int index = 0;
			for(Object co : qlO.values()){
				JSONObject cjo = (JSONObject) co;
				cjo.put("qid", keys.get(index));
				cjo.put("sid", surveyID);
				ql.add(cjo);
				index++;
			}
			JSONArray qlProperties = new JSONArray();
			System.out.println("ql: " + ql.toString());
			qlProperties = ql;

			// Get survey title
			String surveypath = "surveys/" + surveyID;
			ClientResponse minicr = mini.sendRequest("GET", surveypath, "", MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, head);
			JSONObject minicrJO = (JSONObject) p.parse(minicr.getResponse());

			// Create a new survey object
			Survey newSurvey = new Survey(surveyID);
			newSurvey.setAdminmail(adminmail);

			newSurvey.addTitle(minicrJO.getAsString("name"));
			newSurvey.setExpires(minicrJO.getAsString("end"));
			newSurvey.setStartDT(minicrJO.getAsString("start"));




			newSurvey.initMobsosData(qlProperties);


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
			System.out.println("calls to mobsos failed.");
		}
	}

	private void setUpSurvey(String input){
		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
		try {
			JSONObject bodyInput = (JSONObject) p.parse(input);

			String username = "";
			String password = "";
			if(bodyInput.containsKey("NameOfUser")){
				username = bodyInput.getAsString("NameOfUser");
				password = bodyInput.getAsString("Password");
			}
			String surveyID = bodyInput.getAsString("surveyID");
			String uri = url;
			if(bodyInput.getAsString("uri") != null){
				uri = bodyInput.getAsString("uri");
			}
			String adminmail = bodyInput.getAsString("adminmail");

			if(bodyInput.containsKey("followupSurveyID")){
				String followupSurveyID = bodyInput.getAsString("followupSurveyID");
				System.out.println("fsid: " + followupSurveyID);
				if(username.length() > 0){
					// if a username was entered, it is a survey from limesurvey
					setUpLimeSurvey(username, password, followupSurveyID, uri, adminmail);
				} else{
					// if not it is from mobsos surveys
					setUpMobsosSurvey(followupSurveyID, uri, adminmail);
				}

			}

			if(username.length() > 0){
				// if a username was entered, it is a survey from limesurvey
				setUpLimeSurvey(username, password, surveyID, uri, adminmail);
			} else{
				// if not it is from mobsos surveys
				setUpMobsosSurvey(surveyID, uri, adminmail);
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
			value = "Returns the requested survey info or starts the survey.",
			notes = "")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "admin request handled")})
	public Response adminSurvey(String input) {
		//SurveyHandlerService surveyHandlerService = (SurveyHandlerService) Context.get().getService();
		Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, input);

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {
			JSONObject bodyInput = (JSONObject) p.parse(input);
			String intent = bodyInput.getAsString("intent");
			String surveyID = bodyInput.getAsString("surveyID");
			String token = bodyInput.getAsString("slackToken");
			String msg = bodyInput.getAsString("msg");

			String senderEmail = "";
			try{
				senderEmail = bodyInput.getAsString("email");

				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					Response res = takingSurvey(input);
					return res;
				}

			} catch(Exception e){
				Response res = takingSurvey(input);
				return res;
			}


			System.out.println("token is: " + token);

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			if (intent.equals("set_up_survey")) {
				//set up survey
				if(Objects.isNull(currSurvey)){
					System.out.println("No survey exists for id "+ surveyID + ". Creating...");
					setUpSurvey(input);
					// See if survey is set up now
					currSurvey = getSurveyBySurveyID(surveyID);
					if (Objects.isNull(currSurvey)){
						System.out.println("ERROR: Could not set up survey, still null.");
						response.put("text", "ERROR: Could not set up survey. Reason unknown.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}

					if (currSurvey.numberOfQuestions() == 0) {
						response.put("text", "There are no questions in this survey.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
					System.out.println("Survey is set-up.");
					response.put("text", "The survey has been successfully set up.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
				else {
					response.put("text", "The survey is already set up, there are no further actions necessary. Users can send the chatbot a message and it will start to conduct the survey with them.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
			}
			else if (intent.equals("set_up_followup_survey")) {
				//set up survey
				if(Objects.isNull(currSurvey)){
					System.out.println("No survey exists for id "+ surveyID + ". Creating...");
					setUpSurvey(input);
					// See if survey is set up now
					currSurvey = getSurveyBySurveyID(surveyID);
					if (Objects.isNull(currSurvey)){
						System.out.println("ERROR: Could not set up follow up survey, still null.");
						response.put("text", "ERROR: Could not set up follow up survey. Reason unknown.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}

					if (currSurvey.numberOfQuestions() == 0) {
						response.put("text", "There are no questions in this survey.");
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}
					System.out.println("Survey is set-up.");
					response.put("text", "The followup survey has been successfully set up.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
				else {
					response.put("text", "The follow up survey is already set up, there are no further actions necessary. Users can send the chatbot a message and it will start to conduct the survey with them.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
			}
			else if(Objects.isNull(currSurvey)){
				response.put("text", "Please initiate the setup of the survey first.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
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
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_participants")) {
				response.put("text", currSurvey.getParticipantsEmails() + ". Currently there are " + currSurvey.getParticipants().size() + " participants in this survey");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_answers")) {
				System.out.println(currSurvey.getAnswersStringFromAllParticipants());
				String firstSurveyAnswers = "The current answers for survey " + currSurvey.getTitle() + " are: \n" + currSurvey.getAnswersStringFromAllParticipants();
				String secondSurveyAnswers = "";
				// get the followup survey
				if(bodyInput.containsKey("followupSurveyID")){
					surveyID = bodyInput.getAsString("followupSurveyID");
					currSurvey = getSurveyBySurveyID(surveyID);
					secondSurveyAnswers = "\n\nThe current answers for survey " + currSurvey.getTitle() + " are: \n" + currSurvey.getAnswersStringFromAllParticipants();
				}

				response.put("text",firstSurveyAnswers + secondSurveyAnswers);
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}
			else if(intent.equals("delete_participant")){
				// expected form deleteuser: email or userid
				try{
					// not the best way to ensure no one gets deleted by accident
					if(msg.contains("deleteuser: ")){
						System.out.println("deleting participant...");
						String userIdentificator = msg.split(" ")[1];
						String email;
						if(String.valueOf(userIdentificator.charAt(0)).equals("<")){
							if(userIdentificator.contains("<mailto:")){
								//slack adds this mailto part when messaging an email
								email = userIdentificator.split("\\|")[1];
								email = email.split("\\>")[0];
							}
							// if its a userID
							email = getSlackEmailBySlackId(userIdentificator, token);
						} else{
							// email
							email = userIdentificator;
						}
						Participant participant = currSurvey.findParticipant(email);
						if(participant != null){
							// not possible with limesurvey api to delete response (TODO)

							for(Answer answer : participant.getGivenAnswersAl()){
								// delete all answers given by the participant
								SurveyHandlerServiceQueries.deleteAnswerFromDB(answer, database);
							}
							// remove participant from database
							SurveyHandlerServiceQueries.deleteParticipantFromDB(participant, database);
							currSurvey.deleteParticipant(participant);
						}

					}
					response.put("text", "Participant successfully deleted.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				} catch (Exception exception){
					exception.printStackTrace();
					response.put("text", "Deleting participant failed.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
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
					int questionsInSurvey = currSurvey.numberOfQuestions();
					String welcomeString = "Hello :slightly_smiling_face: \n Just send me a message and I will conduct the survey \"" + currSurvey.getTitle() + "\" with you. There are " + questionsInSurvey + " questions for you to answer. \n\n Here are some hints:\n";
					String skipExplanation = " To skip a question just send \"skip\", you will be able to answer them later if you want.";
					String changeAnswerExplanation = "\nTo change your given answer edit your message, by clicking on the 3 points next to your text message and then choosing \"Edit Message\", or click on a button again. For multiple choice questions it is not neccessary to submit the answers again.";
					String resultsGetSaved = "\nYour responses will be saved continuously.";
					response.put("text", welcomeString + skipExplanation + changeAnswerExplanation + resultsGetSaved);
					response.put("contactList", emails);
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
				else{
					response.put("text", "Please add participants to start the survey. No participants found that have not been contacted yet.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
			}
			else if(intent.equals("start_followup")){
				// start the followup survey

				// add participants that completed first survey
				ArrayList<Participant> participantsToAdd = new ArrayList<>();
				for(Participant pa : currSurvey.getParticipants()){
					if(pa.isCompletedsurvey()){
						participantsToAdd.add(pa);
					}
				}

				// get the followup survey
				surveyID = bodyInput.getAsString("followupSurveyID");
				currSurvey = getSurveyBySurveyID(surveyID);

				for(Participant pa : participantsToAdd){
					currSurvey.addParticipant(pa);
				}

				//set up survey, if not yet done
				if(Objects.isNull(currSurvey)){
					response.put("text", "Please initiate the setup of the survey first.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}

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
					int questionsInSurvey = currSurvey.numberOfQuestions();
					String welcomeString = "Hello :slightly_smiling_face: \nJust send me a message and I will conduct the survey \"" + currSurvey.getTitle() + "\" with you. There are " + questionsInSurvey + " questions for you to answer.\n \n Here are some hints:\n";
					String skipExplanation = " To skip a question just send \"skip\", you will be able to answer them later if you want.";
					String changeAnswerExplanation = " To change your answer, either click on the 3 points next to your text message, and then choose \"Edit Message\", or click on a button again. For multiple choice questions it is not neccessary to submit the answers again.";
					response.put("text", welcomeString + skipExplanation + changeAnswerExplanation);
					response.put("contactList", emails);
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
				else{
					response.put("text", "Please add participants to start the survey. No participants found that have not been contacted yet.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}
			}
			else {
				response.put("text", "intent not recognized");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}

		} catch (Exception e) {
			e.printStackTrace();
			response.put("text", "Something went wrong in adminSurvey try block.");
			Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
			return Response.ok().entity(response).build();
		}
	}


	@POST
	@Path("/sendResultsToMobsosSurveys")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Sends the saved answers to Mobsos.",
			notes = "")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "results sent to Mobsos surveys")})
	public Response sendResultsToMobsosSurveys(String input) {
		SurveyHandlerService surveyHandlerService = (SurveyHandlerService) Context.get().getService();
		Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, input);

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String surveyID = bodyInput.getAsString("surveyID");
			String uri = url;
			if(bodyInput.getAsString("uri") != null){
				uri = bodyInput.getAsString("uri");
			}


			String senderEmail = "";
			try{
				senderEmail = bodyInput.getAsString("email");

				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					Response res = takingSurvey(input);
					return res;
				}

			} catch(Exception e){
				Response res = takingSurvey(input);
				return res;
			}

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			if (Objects.isNull(currSurvey)) {
				response.put("text", "Please initiate the setup of the survey first.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}


			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();


			for (Participant pa : currSurvey.getParticipants()) {
				if(pa.isCompletedsurvey() && !(pa.getSurveyResponseID() != null)){
					// dont send inbetween, since there is no possibility to update response
					String surveyResponseID;

					String content = pa.getMSAnswersString();
					System.out.println(content);

					String contentFilled = "{" + content + "}";
					String resultsURL = "surveys/" + surveyID + "/responses";
					ClientResponse minires = mini.sendRequest("POST", resultsURL, contentFilled, MediaType.APPLICATION_JSON, "", head);
					System.out.println("minires: " + minires.getResponse());
					pa.setSurveyResponseID("1");
					SurveyHandlerServiceQueries.updateParticipantInDB(pa, currSurvey.database);
				}

			}

			response.put("text", "Passed back results to MobSOS surveys.");
			Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
			return Response.ok().entity(response).build();

		} catch (Exception e) {
			System.out.println("exception after mobsos results");
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in sendResultsBackToMobsos try block.");
		Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
		return Response.ok().entity(response).build();
	}

	@POST
	@Path("/sendResultsToLimesurvey")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Sends the saved answers to LimeSurvey.",
			notes = "")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "results sent to LimeSurvey")})
	public Response sendResultsToLimesurvey(String input){
		SurveyHandlerService surveyHandlerService = (SurveyHandlerService) Context.get().getService();
		Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, input);

		JSONObject response = new JSONObject();
		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try {

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String surveyID = bodyInput.getAsString("surveyID");
			String uri = url;
			if(bodyInput.getAsString("uri") != null){
				uri = bodyInput.getAsString("uri");
			}

			String senderEmail = "";
			try{
				senderEmail = bodyInput.getAsString("email");

				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					Response res = takingSurvey(input);
					return res;
				}

			} catch(Exception e){
				Response res = takingSurvey(input);
				return res;
			}

			// find correct survey
			Survey currSurvey = getSurveyBySurveyID(surveyID);

			if(Objects.isNull(currSurvey)){
				response.put("text", "Please initiate the setup of the survey first.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
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

				String content = pa.getLSAnswersString();
				System.out.println(content);

				if(pa.getSurveyResponseID() != null){
					// Part of the response is already at LimeSurvey, update response
					surveyResponseID = pa.getSurveyResponseID();
					System.out.println(surveyResponseID);
					String contentFilled = "{" + content + ",\"id\":\"" + surveyResponseID + "\"}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"update_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					String response2 = minire2.getAsString("result");
					System.out.println("aaaaaaaaaaaaaaresult: " + response2);
				} else{
					// New response, add new response and save id at participant
					String contentFilled = "{" + content + "}";
					System.out.println(contentFilled);
					String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
					ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
					JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
					surveyResponseID = minire2.getAsString("result");
					try{
						Integer.parseInt(surveyResponseID);
						pa.setSurveyResponseID(surveyResponseID);
						SurveyHandlerServiceQueries.updateParticipantInDB(pa, currSurvey.database);
						System.out.println("response id: " + pa.getSurveyResponseID());
					} catch (Exception e){
						System.out.println("ERROR in sending results to LimeSurvey");
						response.put("text", surveyResponseID);
						Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
						return Response.ok().entity(response).build();
					}

				}
			}


		}
		catch(Exception e){
			System.out.println("exception after firstsurvey lime");
			e.printStackTrace();
		}


		// again for the follow up survey
		try {

			JSONObject bodyInput = (JSONObject) p.parse(input);
			String username = bodyInput.getAsString("NameOfUser");
			String password = bodyInput.getAsString("Password");
			String senderEmail = bodyInput.getAsString("email");
			if(senderEmail != null){
				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					Response res = takingSurvey(input);
					return res;
				}
			} else{
				Response res = takingSurvey(input);
				return res;
			}
			if(bodyInput.containsKey("followupSurveyID")) {

				System.out.println("has followup survey. sending results back...");
				String surveyID = bodyInput.getAsString("followupSurveyID");
				String uri = url;
				if(bodyInput.getAsString("uri") != null){
					uri = bodyInput.getAsString("uri");
				}

				// find correct survey
				Survey currSurvey = getSurveyBySurveyID(surveyID);

				if(Objects.isNull(currSurvey)){
					response.put("text", "Please initiate the setup of the survey first.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}


				MiniClient mini = new MiniClient();
				mini.setConnectorEndpoint(uri);
				HashMap<String, String> head = new HashMap<String, String>();

				ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + username + "\", \"" + password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire = (JSONObject) p.parse(minires.getResponse());
				String sessionKeyString = minire.getAsString("result");

				for(Participant pa : currSurvey.getParticipants()) {
					String surveyResponseID;

					String content = pa.getLSAnswersString();
					System.out.println(content);

					if(pa.getSurveyResponseID() != null){
						// Part of the response is already at LimeSurvey, update response
						surveyResponseID = pa.getSurveyResponseID();
						System.out.println(surveyResponseID);
						String contentFilled = "{" + content + ",\"id\":\"" + surveyResponseID + "\"}";
						System.out.println(contentFilled);
						String responseData = "{\"method\": \"update_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
						ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
						JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
						String response2 = minire2.getAsString("result");
						System.out.println(response2);
					} else{
						// New response, add new response and save id at participant
						String contentFilled = "{" + content + "}";
						System.out.println(contentFilled);
						String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyID + "\"," + contentFilled + "], \"id\": 1}";
						ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
						JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
						surveyResponseID = minire2.getAsString("result");
						try{
							Integer.parseInt(surveyResponseID);
							pa.setSurveyResponseID(surveyResponseID);
							SurveyHandlerServiceQueries.updateParticipantInDB(pa, currSurvey.database);
							System.out.println("response id: " + pa.getSurveyResponseID());
						} catch (Exception e){
							System.out.println("ERROR in sending results to LimeSurvey");
							response.put("text", surveyResponseID);
							Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
							return Response.ok().entity(response).build();
						}
					}
				}

				response.put("text", "Passed back results to LimeSurvey.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}

			response.put("text", "Passed back results to LimeSurvey.");
			Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
			return Response.ok().entity(response).build();

		}
		catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in sendResultsBackToLimesurvey try block.");
		Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
		return Response.ok().entity(response).build();


	}



	@POST
	@Path("/reminderRoutine")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(
			value = "Sends reminders to survey participants, that have not provided any new answers for a given time.",
			notes = "")
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Reminding successful")})
	public Response reminderRoutine(String input) {
		SurveyHandlerService surveyHandlerService = (SurveyHandlerService) Context.get().getService();
		Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, input);

		JSONObject response = new JSONObject();

		JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

		try{
			JSONObject bodyInput = (JSONObject) p.parse(input);
			String surveyID = bodyInput.getAsString("surveyID");
			String token = bodyInput.getAsString("slackToken");
			String sbfmUrl = bodyInput.getAsString("sbfmUrl");
			Survey currSurvey = getSurveyBySurveyID(surveyID);
			boolean slack = false;
			if(token.startsWith("xoxb-")){
				slack = true;
			}

			String senderEmail = "";
			try{
				senderEmail = bodyInput.getAsString("email");

				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					Response res = takingSurvey(input);
					return res;
				}

			} catch(Exception e){
				Response res = takingSurvey(input);
				return res;
			}

			if(Objects.isNull(currSurvey)){
				response.put("text", "Please initiate the setup of the survey first.");
				Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
				return Response.ok().entity(response).build();
			}

			System.out.println(bodyInput);
			if(bodyInput.containsKey("reminderAfterHours")){
				System.out.println("contains Key : " + bodyInput);
				Integer timeToRemind = Integer.parseInt(bodyInput.getAsString("reminderAfterHours"));
				System.out.println(timeToRemind);

				for(Participant pa : currSurvey.getParticipants()){
					// Get all participants and check if they have not answered for the amount of time
					if(!pa.isCompletedsurvey()){
						// participant has not already completed the survey

						Integer unSize = pa.getUnaskedQuestions().size();
						// The last question asked, but not answered is not on the list anymore
						Integer unansweredQuestions = unSize + 1;
						Integer allSize = currSurvey.numberOfQuestions();

						LocalDateTime lastDT = LocalDateTime.parse(pa.getLasttimeactive());
						long hoursDifference = ChronoUnit.HOURS.between(lastDT, LocalDateTime.now());

						System.out.println("time gone : " + hoursDifference + "how long to wait: " + timeToRemind);
						//System.out.println("time gone times 2: " + timeToRemind*2);
						if(hoursDifference > (timeToRemind*2)){
							// do not remind if already reminded 2 times
						} else if(hoursDifference >= timeToRemind){
							// Participant has not started survey
							String msg = "";
							String mail = "";

							if(unSize.equals(allSize)){
								mail = pa.getEmail();
								msg = "Hello again! It would be nice if you would start the survey. :slightly_smiling_face:";
							}
							// Participant has started, but not finished survey
							else {
								mail = pa.getEmail();
								msg = "Hello again! Please continue with your survey. There are only " + unansweredQuestions + " questions left. :slightly_smiling_face:";
							}

							if(slack){
								// post request to sbfmanager to send slack message
								String SBFManagerURL = "SBFManager";
								String uri = SBFManagerURL + "/sendMessageToSlack/" + token + "/" + mail;
								HashMap<String, String> head = new HashMap<>();

								MiniClient client = new MiniClient();
								client.setConnectorEndpoint(sbfmUrl);

								ClientResponse result = client.sendRequest("POST", uri, "{\"msg\":\"" + msg + "\"}", "application/json", "*/*", head);
								String resString = result.getResponse();
								System.out.println(resString);
							}
							else{
								// post request to sbfmanager to send rocketchat message
								String SBFManagerURL = "SBFManager";
								String uri = SBFManagerURL + "/sendMessageToRocketChat/" + token + "/" + mail;
								HashMap<String, String> head = new HashMap<>();

								MiniClient client = new MiniClient();
								client.setConnectorEndpoint(sbfmUrl);

								ClientResponse result = client.sendRequest("POST", uri, "{\"msg\":\"" + msg + "\"}", "application/json", "*/*", head);
								String resString = result.getResponse();
								System.out.println(resString);
							}

						}
					}
				}
			}

			System.out.println(response.toString());

		} catch(Exception e){
			e.printStackTrace();
		}

		// again for the followup survey
		try{
			JSONObject bodyInput = (JSONObject) p.parse(input);
			String surveyID = bodyInput.getAsString("surveyID");
			String token = bodyInput.getAsString("slackToken");
			String sbfmUrl = bodyInput.getAsString("sbfmUrl");
			String senderEmail = bodyInput.getAsString("email");

			if(senderEmail != null){
				if (!(bodyInput.getAsString("adminmail").equals(senderEmail))) {
					System.out.println("admin detected, email: " + senderEmail + " and " + bodyInput.getAsString("adminmail"));
					Response res = takingSurvey(input);
					return res;
				}
			} else{
				Response res = takingSurvey(input);
				return res;
			}
			if(bodyInput.containsKey("followupSurveyID")) {
				Survey currSurvey = getSurveyBySurveyID("followupSurveyID");

				if(Objects.isNull(currSurvey)){
					response.put("text", "Please initiate the setup of the survey first.");
					Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
					return Response.ok().entity(response).build();
				}

				System.out.println(bodyInput);
				if(bodyInput.containsKey("reminderAfterHours")){
					System.out.println("contains Key : " + bodyInput);
					Integer timeToRemind = Integer.parseInt(bodyInput.getAsString("reminderAfterHours"));
					System.out.println(timeToRemind);

					for(Participant pa : currSurvey.getParticipants()){
						// Get all participants and check if they have not answered for the amount of time
						if(!pa.isCompletedsurvey()){
							// participant has not already completed the survey

							Integer unSize = pa.getUnaskedQuestions().size();
							// The last question asked, but not answered is not on the list anymore
							Integer unansweredQuestions = unSize + 1;
							Integer allSize = currSurvey.numberOfQuestions();

							LocalDateTime lastDT = LocalDateTime.parse(pa.getLasttimeactive());
							long hoursDifference = ChronoUnit.HOURS.between(lastDT, LocalDateTime.now());

							System.out.println("time gone : " + hoursDifference + "how long to wait: " + timeToRemind);
							//System.out.println("time gone times 2: " + timeToRemind*2);
							if(hoursDifference > (timeToRemind*2)){
								// do not remind if already reminded 2 times
							} else if(hoursDifference >= timeToRemind){
								// Participant has not started survey
								String msg = "";
								String mail = "";

								if(unSize.equals(allSize)){
									mail = pa.getEmail();
									msg = "Hello again! It would be nice if you would start the survey. :)";
								}
								// Participant has started, but not finished survey
								else {
									mail = pa.getEmail();
									msg = "Hello again! Please continue with your survey. There are only " + unansweredQuestions + " questions left. :)";
								}

								// post request to sbfmanager to send message
								String SBFManagerURL = "SBFManager";
								String uri = SBFManagerURL + "/token/" + token + "/" + mail;
								HashMap<String, String> head = new HashMap<>();

								MiniClient client = new MiniClient();
								client.setConnectorEndpoint(sbfmUrl);

								ClientResponse result = client.sendRequest("POST", uri, "{\"msg\":\"" + msg + "\"}", "application/json", "*/*", head);
								String resString = result.getResponse();
								System.out.println(resString);
							}
						}
					}
				}
			}


			System.out.println(response.toString());

			response.put("text", "Participants reminded.");
			Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
			return Response.ok().entity(response).build();

		} catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "Something went wrong in reminderRoutine try block.");
		Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
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

	/*
	private String getRocketChatEmailByUsername(String user, String token){
		System.out.println("inside getSlackEMailbyuserid...");
		JSONParser p = new JSONParser();
		System.out.println(user);
		//remove <@ and > at the beginning and end
		if(user.contains("@")){
			user = user.substring(1, user.length());
		}

		try{
			// slack api call to get email for user id
			String urlParameters = "token=" + token + "&user=" + user;
			byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int postDataLength = postData.length;
			String request = "https://chat.tech4comp.dbis.rwth-aachen.de";
			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint("");
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", request + "/api/v1/users.info", "{\"X-Auth-Token\":" + token + ", \"X-User_Id\":" + user + "}", MediaType.APPLICATION_FORM_URLENCODED, "", head);
			System.out.println("minires: " + minires.getResponse());
			String cResult = java.net.URLDecoder.decode(minires.getResponse(), StandardCharsets.UTF_8.name());
			System.out.println(cResult);
			//System.out.println(result);


			// getting email from json
			JSONObject resultJ = (JSONObject) p.parse(cResult);
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

	 */





}
