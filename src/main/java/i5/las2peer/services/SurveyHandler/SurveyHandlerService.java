package i5.las2peer.services.SurveyHandler;

import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;

import java.net.HttpURLConnection;
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



	private static ArrayList<String> surveySetUp = new ArrayList<>();

	private static HashMap<String, String> surveyInfos = new HashMap<>();
	private static HashMap<String, String> questions = new HashMap<String, String>();
	private static ArrayList<String> questionIDs = new ArrayList<>();
	private static ArrayList<String> questionIDsOrdered = new ArrayList<>();
	private static HashMap<String, String> questionOrder = new HashMap<>();
	private static HashMap<String, ArrayList<String>> questionGroupID = new HashMap<String, ArrayList<String>>();
	private static ArrayList<String> questionsWithSub = new ArrayList<>();
	private static ArrayList<String> questionsWithSubOrdered = new ArrayList<>();
	private static HashMap<String, ArrayList<String>> subquestions = new HashMap<String, ArrayList<String>>();
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
			JSONObject bodyInput = (JSONObject) p.parse(input);
			String intent = bodyInput.getAsString("intent");

			// Check if survey is set up already
			if (Objects.isNull(this.surveyGlobal)){
				response.put("text", "Please wait for the survey to be initialized.");
				return Response.ok().entity(response).build();
			}
			/*
			if (!(surveySetUp.contains(bodyInput.getAsString("surveyIDString")))) {
				setUpSurvey(input);
				if (questions.size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}
			*/

			// Check if message was sent by someone known
			String senderEmail = bodyInput.getAsString("email");
			if (Objects.isNull(this.surveyGlobal.findParticipant(senderEmail))){
				// participant does not exist, create a new one
				Participant newParticipant = new Participant(senderEmail);
				this.surveyGlobal.addParticipant(newParticipant);
			}

			// Get the existing participant
			Participant currParticipant = this.surveyGlobal.findParticipant(senderEmail);
			String message = bodyInput.getAsString("msg");
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
					//surveyInfos.put("surveyTitle", ((JSONObject) i).getAsString("surveyls_title"));
					newSurvey.addTitle( ((JSONObject) i).getAsString("surveyls_title"));
					break;
				}
			}

			// Check if adding title worked
			if (Objects.isNull(newSurvey.getTitle())){
				System.out.println("Failed to add title. Aborting survey creation...");
			} else {
				System.out.println(newSurvey.getTitle());
				this.surveyGlobal = newSurvey;
				System.out.println("Survey successfully initialized.");
			}




		} catch(Exception e){
			e.printStackTrace();
		}

			/*
			if (!(surveySetUp.contains(surveyIDString))) {
				ClientResponse mini2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire2 = (JSONObject) p.parse(mini2.getResponse());
				JSONArray ql = (JSONArray) minire2.get("result");

				for (Object jo : ql) {
					JSONObject j = (JSONObject) jo;

					if (j.getAsString("parent_qid").equals("0")) {
						questions.put(j.getAsString("qid"), j.getAsString("question"));
						questionIDs.add(j.getAsString("qid"));

						questionOrder.put(j.getAsString("qid"), (j.getAsString("gid")) + "#" + j.getAsString("question_order"));
						if (questionGroupID.containsKey(j.getAsString("gid"))) {
							questionGroupID.get(j.getAsString("gid")).add(j.getAsString("qid"));
						} else {
							questionGroupID.computeIfAbsent(j.getAsString("gid"),
									k -> {
										ArrayList<String> h = new ArrayList<String>();
										h.add(j.getAsString("qid"));
										return h;
									});
						}
					} else {

						if (!(questionsWithSub.contains(j.getAsString("parent_qid")))) {
							questionsWithSub.add(j.getAsString("parent_qid"));
						}

						if (subquestions.containsKey(j.getAsString("parent_qid"))) {
							subquestions.get(j.getAsString("parent_qid")).add(j.getAsString("question"));
						} else {
							subquestions.computeIfAbsent(j.getAsString("parent_qid"),
									k -> {
										ArrayList<String> h = new ArrayList<String>();
										h.add(j.getAsString("question"));
										return h;
									});
						}

					}

				}

				//order the questionIDs and questionsWithSUbIDs


				for(String entry : questionGroupID.keySet()){
					for(String id : questionOrder.keySet()){
						qO = questionOrder.get(id);
						groupid = qO.split("#")[0];
						order = qO.split("#")[1];
						if(questionGroupID.get(entry).equals(groupid)){

						}
						for(int i = 0; i < questionGroupID.get(entry).size(); i++){
					}
				}


				surveyInfos.put("numberOfQuestions", String.valueOf(questions.size()));

				ClientResponse mini4 = mini.sendRequest("POST", uri, ("{\"method\": \"list_surveys\", \"params\": [ \"" + sessionKeyString + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire4 = (JSONObject) p.parse(mini4.getResponse());
				JSONArray sl = (JSONArray) minire4.get("result");
				for (Object i : sl) {
					if (((JSONObject) i).getAsString("sid").equals(surveyIDString)) {
						surveyInfos.put("surveyTitle", ((JSONObject) i).getAsString("surveyls_title"));
					}
				}

				surveySetUp.add(surveyIDString);

			}

		} catch (Exception e) {
			e.printStackTrace();
		}*/
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
			//if (Objects.isNull(surveyGlobal)) {
			if(this.firstStartUp){
				this.firstStartUp = false;
				setUpSurvey(input);
				if (this.surveyGlobal.getSortedQuestionIds().size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}

			//}

			//TODO: check if rocket chat passes on email of user
			if (!(bodyInput.getAsString("adminmail").equals(bodyInput.getAsString("email")))) {
				response.put("text", "only admin is allowed to do that");
				return Response.ok().entity(response).build();
			}
			String intent = bodyInput.getAsString("intent");
			if (intent.equals("add_participant")) {
				Participant newParticipant = new Participant(bodyInput.getAsString("msg"));
				boolean added = surveyGlobal.addParticipant(newParticipant);
				response.put("text", "Adding participant " +surveyGlobal.getParticipantsEmails() + ", got result: " + added);
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
				System.out.println(surveyGlobal.getParticipants().toString());
				System.out.println(surveyGlobal.getParticipants());
				System.out.println(surveyGlobal.getParticipants().size());
				response.put("text", surveyGlobal.getParticipantsEmails());
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_answers")) {
				response.put("text", surveyGlobal.getAnswers().toString());
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
				}
				else{
					response.put("start", "Please add participants to start the survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println(emails);
				response.put("text", "Would you like to start the survey \"" + surveyInfos.get("surveyTitle") + "\"?");
				response.put("start", emails);
				return Response.ok().entity(response).build();
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
			setUpSurvey(input);
			surveyGlobal.addParticipant(par);
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


			String content = "";//"{\"133653X1X1\":\"t\",\"133653X1X2SQ001\":\"tes2t\", \"133653X1X2SQ002\":\"test\"}";
			String separator = "X";//
			String base = surveyIDString + separator;

			/////////////////////////////

			/*
			ArrayList<String> emptyList1 = new ArrayList<>();
			ArrayList<String> emptyList2 = new ArrayList<>();
			HashMap<String, String> emptyHash = new HashMap<>();
			Participant newParticipant = new Participant("testmail", false, false, emptyList1, emptyList2, emptyHash);
			participants.add(0, newParticipant);
			for(String id : questionIDs){
				newParticipant.addUnaskedQuestion(id);
			}
			newParticipant.addAnswer("1", "testanswer");

			 */

			//maybe with getAnswers
			for(Participant pa : surveyGlobal.getParticipants()){
				for(String qid : questionIDs){
					if(pa.hasAnswer(qid)) {
						for(String id : questionGroupID.keySet()) {
							if (questionGroupID.get(id).contains(qid)) {
								if(questionsWithSub.contains(qid)){
									for(int i = 0; i<subquestions.get(qid).size(); i++){
										String subcode = "";
										String s = id + separator + qid + subcode;
										String a = pa.getAnswer(qid);
										content += "\"" + base + s + "\":\"" + a + "\",";
									}
								}
								else{
									String s = id + separator + qid;
									String a = pa.getAnswer(qid);
									content += "\"" + base + s + "\":\"" + a + "\",";
								}

							}
						}

					}
				}

				String contentFilled = "{" + content + "\"token\":\"" + pa.getEmail() + "\"}";
				String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + surveyIDString + "\"," + contentFilled + "], \"id\": 1}"; // \"" + sessionKeyString + "\", \"" + surveyID + "\", \"" + a + "\"], \"id\": 1}";
				ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
				JSONObject minire2 = (JSONObject) p.parse(minires.getResponse());
				String result = minire.getAsString("result");
				System.out.println(result);
			}

		}
		catch(Exception e){
			e.printStackTrace();
		}

		response.put("text", "passed back results");
		return Response.ok().entity(response).build();


	}
}
