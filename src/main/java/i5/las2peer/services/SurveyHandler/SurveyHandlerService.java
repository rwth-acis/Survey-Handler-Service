package i5.las2peer.services.SurveyHandler;

import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.connectors.webConnector.WebConnector;

import java.awt.image.MemoryImageSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
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
	private static ArrayList<String> surveySetUp = new ArrayList<>();

	private static HashMap<String, String> surveyInfos = new HashMap<>();
	private static HashMap<String, String> questions = new HashMap<String, String>();
	private static ArrayList<String> questionIDs = new ArrayList<>();
	private static ArrayList<String> questionsWithSub = new ArrayList<>();
	private static HashMap<String, ArrayList<String>> subquestions = new HashMap<String, ArrayList<String>>();

	private static ArrayList<Participant> participants = new ArrayList<>();


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

	/**
	 * Template of a post function.
	 *
	 * @param myInput The post input the user will provide.
	 * @return Returns an HTTP response with plain text string content derived from the path input param.
	 */
	@POST
	@Path("/postt/{input}")
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "Example method that returns a phrase containing the received input.")
	public Response posttTemplate(@PathParam("input") String myInput) {
		JSONObject r = new JSONObject();
		r.put("text", myInput);
		return Response.ok().entity(r).build();
	}

	@POST
	@Path("/posti")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiResponses(
			value = {@ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "REPLACE THIS WITH YOUR OK MESSAGE")})
	@ApiOperation(
			value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
			notes = "Example method that returns a phrase containing the received input.")
	public Response posti(JSONObject myInput) {
		JSONObject r = new JSONObject();
		r.put("text", myInput);
		return Response.ok().entity(r).build();
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

			if (!(surveySetUp.contains(bodyInput.getAsString("surveyIDString")))) {
				setUpSurvey(input);
				if (questions.size() == 0) {
					response.put("text", "There are no questions in this survey.");
					return Response.ok().entity(response).build();
				}
				System.out.println("Survey is set-up.");
			}

			boolean newParticipant = true;

			ArrayList<String> emptyList1 = new ArrayList<>();
			ArrayList<String> emptyList2 = new ArrayList<>();
			HashMap<String, String> emptyHash = new HashMap<>();
			Participant currParticipant = new Participant("", false, false, emptyList1, emptyList2, emptyHash);

			for(Participant pa : participants){
				if(pa.getEmail().contains(bodyInput.getAsString("email"))){
					currParticipant = pa;
					newParticipant = false;
				}
			}
			if(newParticipant){
				currParticipant.addEmail(bodyInput.getAsString("email"));
				participants.add(0, currParticipant);
				for(String id : questionIDs){
					currParticipant.addUnaskedQuestion(id);
				}
				Collections.reverse(currParticipant.getUnaskedQuestions());
			}
			if(!(currParticipant.getParticipantContacted())){
				currParticipant.setParticipantContacted();
				response.put("text", "Would you like to start the survey \"" + surveyInfos.get("surveyTitle") + "\"?");
				return Response.ok().entity(response).build();
			}
			if(currParticipant.getCompletedSurvey()) {
				response.put("text", "You completed this survey already, to change answers, please ...");
				return Response.ok().entity(response).build();
			} else{
				if(intent.equals("skip")){
					currParticipant.addSkippedQuestion(currParticipant.getLastQuestion());
				} else{
					currParticipant.addAnswer(currParticipant.getLastQuestion(), bodyInput.getAsString("msg"));
				}
				if (currParticipant.getUnaskedQuestions().size() == 0 && currParticipant.getSkippedQuestions().size() == 0) {
					currParticipant.setCompletedSurvey();
					response.put("text", "Thank you for completing this survey."); //+ currParticipant.getEmail() + currParticipant.getUnaskedQuestions() + currParticipant.getSkippedQuestions()
					return Response.ok().entity(response).build();
				} else if (!(currParticipant.getUnaskedQuestions().size() == 0)) {
					if (!(questionsWithSub.contains(currParticipant.getUnaskedQuestions().get(0)))) {
						response.put("text", questions.get(currParticipant.getUnaskedQuestions().get(0)));
					} else {
						response.put("text", questions.get(currParticipant.getUnaskedQuestions().get(0)) + ". Please choose an answer option from the following optinos: \n" + subquestions.get(currParticipant.getUnaskedQuestions().get(0)));
					}
					currParticipant.addLastQuestion(currParticipant.getUnaskedQuestions().get(0));
					currParticipant.getUnaskedQuestions().remove(0);
					return Response.ok().entity(response).build();
				} else if (currParticipant.getUnaskedQuestions().size() == 0 && currParticipant.getSkippedQuestions().size() > 0) {
					response.put("text", "This question was skipped by you, you can answer now or skip again: " + questions.get(currParticipant.getSkippedQuestions().get(0)));
					currParticipant.addLastQuestion(currParticipant.getSkippedQuestions().get(0));
					currParticipant.getSkippedQuestions().remove(0);
					return Response.ok().entity(response).build();
				}
			}

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

			if (!(surveySetUp.contains(surveyIDString))) {
				ClientResponse mini2 = mini.sendRequest("POST", uri, ("{\"method\": \"list_questions\", \"params\": [ \"" + sessionKeyString + "\", \"" + surveyID + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
				JSONObject minire2 = (JSONObject) p.parse(mini2.getResponse());
				JSONArray ql = (JSONArray) minire2.get("result");

				for (Object jo : ql) {
					JSONObject j = (JSONObject) jo;

					if (j.getAsString("parent_qid").equals("0")) {
						questions.put(j.getAsString("qid"), j.getAsString("question"));
						questionIDs.add(j.getAsString("qid"));
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
			if (!(surveySetUp.contains(bodyInput.getAsString("surveyIDString")))) {
				setUpSurvey(input);
				if (questions.size() == 0) {
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
				for (Participant pa : participants) {
					if(pa.getEmail().equals(bodyInput.getAsString("msg"))){
						response.put("text", "participant " + bodyInput.getAsString("msg") + " is already in participants list.");
						return Response.ok().entity(response).build();
					}
				}
				ArrayList<String> emptyList1 = new ArrayList<>();
				ArrayList<String> emptyList2 = new ArrayList<>();
				HashMap<String, String> emptyHash = new HashMap<>();
				Participant newParticipant = new Participant(bodyInput.getAsString("msg"), false, false, emptyList1, emptyList2, emptyHash);
				for(String id : questionIDs){
					newParticipant.addUnaskedQuestion(id);
				}
				Collections.reverse(newParticipant.getUnaskedQuestions());
				participants.add(0, newParticipant);
				response.put("text", "participant " + bodyInput.getAsString("msg") + " successfully added");
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
				ArrayList<String> parti = new ArrayList<>();
				for (Participant pa : participants) {
					parti.add(pa.getEmail());
				}
				response.put("text", parti.toString());
				return Response.ok().entity(response).build();
			}
			else if (intent.equals("get_answers")) {
				ArrayList<String> answe = new ArrayList<>();
				for (Participant pa : participants) {
					answe.add(pa.getEmail() + " : " + pa.getAnswers());
				}
				response.put("text", answe.toString());
				return Response.ok().entity(response).build();
			}
			else if(intent.equals("start_survey")){
				String emails = "";
				for (Participant pa : participants) {
					if(!(pa.getEmail().equals("null"))){ //&& !(pa.getParticipantContacted())) {
						if(pa.getEmail().contains("<mailto:")){ //slack adds this mailto part when messaging an email
							pa.addEmail(pa.getEmail().split("\\|")[1]);
							pa.addEmail(pa.getEmail().split("\\>")[0]);
						}
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
	public Response passResultsBack(String input){
		JSONObject response = new JSONObject();
		response.put("text", "pased back results");
		return Response.ok().entity(response).build();
	}
}
