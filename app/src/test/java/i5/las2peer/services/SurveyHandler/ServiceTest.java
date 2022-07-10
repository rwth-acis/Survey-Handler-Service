package i5.las2peer.services.SurveyHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.glassfish.jersey.server.JSONP;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class ServiceTest {


	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgentImpl testAgent;
	private static final String testPass = "adamspass";

	private static final String mainPath = "SurveyHandler/";

	String NameOfUser = "testUsername";
	String Password = "testPassword1";
	String uri = "https://testsurveyurl.limesurvey.net/admin/remotecontrol";
	String surveyID = "494668";
	String secondSurveyID = "647979";



	/**
	 * Called before a test starts.
	 * <p>
	 * Sets up the node, initializes connector and adds user agent that can be used throughout the test.
	 *
	 * @throws Exception
	 */
	@Before
	public void startServer() throws Exception {
		// start node
		node = new LocalNodeManager().newNode();
		node.launch();

		// add agent to node
		testAgent = MockAgentFactory.getAdam();
		testAgent.unlock(testPass); // agents must be unlocked in order to be stored
		node.storeAgent(testAgent);

		//my testingagent
		testAgent = MockAgentFactory.getAdam();
		testAgent.unlock(testPass); // agents must be unlocked in order to be stored
		node.storeAgent(testAgent);

		// start service
		// during testing, the specified service version does not matter
		node.startService(new ServiceNameVersion(SurveyHandlerService.class.getName(), "1.0.0"), "a pass");

		// start connector
		connector = new WebConnector(true, 0, false, 0); // port 0 means use system defined port

		logStream = new ByteArrayOutputStream();
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
	}

	/**
	 * Called after the test has finished. Shuts down the server and prints out the connector log file for reference.
	 *
	 * @throws Exception
	 */
	@After
	public void shutDownServer() throws Exception {
		if (connector != null) {
			connector.stop();
			connector = null;
		}
		if (node != null) {
			node.shutDown();
			node = null;
		}
		if (logStream != null) {
			System.out.println("Connector-Log:");
			System.out.println("--------------");
			System.out.println(logStream.toString());
			logStream = null;
		}
	}

	@Test
	public void testPost() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			// testInput is the pathParam
			ClientResponse result = client.sendRequest("POST", mainPath + "post/testInput", "");
			Assert.assertEquals(200, result.getHttpCode());
			// "testInput" name is part of response
			Assert.assertTrue(result.getResponse().trim().contains("testInput"));
			System.out.println("Result of 'testPost': " + result.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testGet() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			ClientResponse result = client.sendRequest("GET", mainPath + "get", "");
			Assert.assertEquals(200, result.getHttpCode());
			Assert.assertEquals("adam", result.getResponse().trim());// YOUR RESULT VALUE HERE
			System.out.println("Result of 'testGet': " + result.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testSurveySetUp() {
		// test to set up a dummy limesurvey survey and delete after success
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			// Survey defined in LimeSurvey with following values
			String surveyTitle = "Test Title";
			String question1 = "The first question.";
			String question1Type = Question.qType.LONGFREETEXT.toString();
			String question2 = "The second question.";
			String answerOption1 = "Answer Option 1";
			String answerOption2 = "Answer Option 2";
			String question2Type = Question.qType.LISTRADIO.toString();
			int numberOfQuestions = 2;
			String language = "en";

			JSONObject defs = setDefs();

			ClientResponse result = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			Survey testSurvey = SurveyHandlerService.getSurveyBySurveyID(defs.getAsString("surveyID"));

			// test if values set correctly
			Assert.assertEquals(testSurvey.getSid(), defs.getAsString("surveyID"));
			Assert.assertEquals(testSurvey.getAdminmail(), defs.getAsString("adminmail"));
			Assert.assertEquals(testSurvey.getTitle(), surveyTitle);
			System.out.println("language: " + testSurvey.getLanguages());
			ArrayList<String> l = testSurvey.getLanguages();
			HashMap<String, ArrayList<Question>> b = testSurvey.getQuestionALLanguage();

			Assert.assertEquals(testSurvey.getQuestionAL(language).size(), numberOfQuestions);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(0).getText(), question1);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(0).getType(), question1Type);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(1).getText(), question2);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(1).getAnswerOptionByIndex(1).getText(), answerOption1);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(1).getAnswerOptionByIndex(2).getText(), answerOption2);
			Assert.assertEquals(testSurvey.getQuestionAL(language).get(1).getType(), question2Type);

			System.out.println("Result of 'testGet': " + result.getResponse().trim());


			// now delete created survey from db
			SurveyHandlerService.deleteSurvey(defs.getAsString("surveyID"));


		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}



	private JSONObject setDefs(){

		// define test survey values that gets passed on
		JSONObject bodyInput = new JSONObject();
		String senderEmail = "testmail";
		String adminmail = "testmail";
		String intent = "set_up_survey";

		// add to input for function
		bodyInput.put("surveyID", surveyID);
		bodyInput.put("email", senderEmail);
		bodyInput.put("adminmail", adminmail);
		bodyInput.put("intent", intent);
		bodyInput.put("NameOfUser", NameOfUser);
		bodyInput.put("Password", Password);
		bodyInput.put("uri", uri);

		return bodyInput;
	}

	private JSONObject setDefsMultplIDS(){

		// define test survey values that gets passed on
		JSONObject bodyInput = new JSONObject();
		String senderEmail = "testmail";
		String adminmail = "testmail";
		String intent = "set_up_survey";

		// add to input for function
		bodyInput.put("surveyID", surveyID + "," + secondSurveyID);
		bodyInput.put("email", senderEmail);
		bodyInput.put("adminmail", adminmail);
		bodyInput.put("intent", intent);
		bodyInput.put("NameOfUser", NameOfUser);
		bodyInput.put("Password", Password);
		bodyInput.put("uri", uri);

		return bodyInput;
	}

	private JSONObject setAddParticipantDefs(String email){

		JSONObject bodyInput = setDefs();

		String intent = "add_participant";
		bodyInput.put("intent", intent);
		bodyInput.put("msg",email);

		return bodyInput;
	}

	private JSONObject setGetParticipantsDefs(){

		JSONObject bodyInput = setDefs();

		String intent = "get_participants";
		bodyInput.put("intent", intent);

		return bodyInput;
	}


	@Test
	public void testAddingParticipant() {
		// test to add participant and delete after
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			// Survey defined in LimeSurvey with following values
			String email = "testemail@mail.de";

			// set up survey
			JSONObject preDefs = setDefs();
			// choose administrating survey
			preDefs.put("msg", surveyID);
			ClientResponse secondResult = client.sendRequest("POST", mainPath + "adminSurvey", preDefs.toString());
			ClientResponse preResult = client.sendRequest("POST", mainPath + "adminSurvey", preDefs.toString());

			JSONObject defs = setAddParticipantDefs(email);

			ClientResponse result = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			Survey testSurvey = SurveyHandlerService.getSurveyBySurveyID(defs.getAsString("surveyID"));

			// test if participant added correctly
			Assert.assertEquals(testSurvey.getParticipants().get(0).getEmail(), email);

			defs = setGetParticipantsDefs();

			ClientResponse result2 = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			// test if participants listed correctly
			try{
				JSONParser p = new JSONParser();
				JSONObject minires = (JSONObject) p.parse(result.getResponse());
				String msgText = minires.getAsString("text");
				String participants = testSurvey.getParticipants().toString();
				if(msgText.contains("true")){
					Assert.assertTrue(true);
				} else{
					Assert.assertFalse(true);
				}
			} catch (Exception e){
				e.printStackTrace();
				Assert.assertFalse(true);
			}

			Assert.assertEquals(testSurvey.getParticipants().get(0).getEmail(), email);

			Participant participant = testSurvey.getParticipantByPID(email);
			testSurvey.deleteParticipant(participant);

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}


/*
	@Test
	public void testSendResultsToLimeSurvey() {
		// test to send results to dummy survey at Limesurvey
		try {

			JSONObject defs = setDefs();

			String ao = "Answer Option 1";

			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			addAnswers(SurveyHandlerService.getSurveyBySurveyID(defs.getAsString("surveyID")), ao, "en");

			JSONParser p = new JSONParser();

			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint(uri);
			HashMap<String, String> head = new HashMap<String, String>();

			ClientResponse minires = mini.sendRequest("POST", uri, ("{\"method\": \"get_session_key\", \"params\": [ \"" + NameOfUser + "\", \"" + Password + "\"], \"id\": 1}"), MediaType.APPLICATION_JSON, "", head);
			JSONObject minire = (JSONObject) p.parse(minires.getResponse());
			String sessionKeyString = minire.getAsString("result");

			ArrayList<Participant> pa = SurveyHandlerService.getSurveyBySurveyID(defs.getAsString("surveyID")).getParticipants();
			String content  = pa.get(0).getLSAnswersString();
			String contentFilled = "{" + content + "}";

			String responseData = "{\"method\": \"add_response\", \"params\": [\"" + sessionKeyString + "\",\"" + defs.getAsString("surveyID") + "\"," + contentFilled + "], \"id\": 1}";
			ClientResponse minires2 = mini.sendRequest("POST", uri, responseData, MediaType.APPLICATION_JSON, "", head);
			JSONObject minire2 = (JSONObject) p.parse(minires2.getResponse());
			String surveyResponseID = minire2.getAsString("result");

			// check if response id was returned
			Integer.parseInt(surveyResponseID);

			System.out.println("Result of 'testGet': " + surveyResponseID);


		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

 */

	private void addAnswers(Survey currSurvey, String answerOption, String language){

		MiniClient client = new MiniClient();
		client.setConnectorEndpoint(connector.getHttpEndpoint());
		client.setLogin(testAgent.getIdentifier(), testPass);

		String intent = "";
		String channel = "testchannel";
		String senderEmail = "testemail@mail.de";
		String token = "token";
		String messageTs = "testmessagets1";
		String NameOfUser = "user";
		String buttonIntent = "buttonAnswer";
		String msg = "message1";

		JSONObject input = new JSONObject();
		input.put("surveyID", currSurvey.getSid());
		input.put("intent", intent);
		input.put("channel", channel);
		input.put("email", senderEmail);
		input.put("slackToken", token);
		input.put("time", messageTs);
		input.put("NameOfUser", NameOfUser);
		input.put("buttonIntent", buttonIntent);
		input.put("msg", msg);

		// first message should be welcoming message
		ClientResponse result = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());

		try{
			JSONParser p = new JSONParser();
			JSONObject minires = (JSONObject) p.parse(result.getResponse());
			String msgText = minires.getAsString("text");
			String hello = SurveyHandlerService.texts.get("helloDefaultDE");
			String welcomeString = SurveyHandlerService.texts.get("welcomeString").replaceAll("\\{hello\\}", hello);
			welcomeString = welcomeString.replaceAll("\\{title\\}", currSurvey.getTitle());
			welcomeString = welcomeString.replaceAll("\\{questionsInSurvey\\}", String.valueOf(currSurvey.getQuestionAL(language).size()));
			welcomeString = welcomeString.replaceAll("\\{welcomeText\\}", currSurvey.getWelcomeText());
			String beginningText = welcomeString +
					SurveyHandlerService.texts.get("skipExplanation") +
					SurveyHandlerService.texts.get("first") +
					SurveyHandlerService.texts.get("changeAnswerExplanation") +
					SurveyHandlerService.texts.get("resultsGetSaved");
			Assert.assertEquals(msgText, beginningText);
		} catch (Exception e){
			e.printStackTrace();
		}


		msg = "message2";
		messageTs = "testmessagets2";

		JSONObject input2 = new JSONObject();
		input2.put("surveyID", currSurvey.getSid());
		input2.put("intent", intent);
		input2.put("channel", channel);
		input2.put("email", senderEmail);
		input2.put("slackToken", token);
		input2.put("time", messageTs);
		input2.put("NameOfUser", NameOfUser);
		input2.put("buttonIntent", buttonIntent);
		input2.put("msg", msg);

		// second message should be first question
		ClientResponse result2 = client.sendRequest("POST", mainPath + "takingSurvey", input2.toString());

		try{
			JSONParser p = new JSONParser();
			JSONObject minires = (JSONObject) p.parse(result.getResponse());
			String msgText = minires.getAsString("result");
			String questionText = currSurvey.getQuestionAL("en").get(0).getText();
			Assert.assertEquals(msgText, questionText);
		} catch (Exception e){
			e.printStackTrace();
		}

		// wrong answer
		intent = "answer";
		messageTs = "testmessagets3";
		msg = "test";

		JSONObject input3 = new JSONObject();
		input3.put("surveyID", currSurvey.getSid());
		input3.put("intent", intent);
		input3.put("channel", channel);
		input3.put("email", senderEmail);
		input3.put("slackToken", token);
		input3.put("time", messageTs);
		input3.put("NameOfUser", NameOfUser);
		input3.put("buttonIntent", buttonIntent);
		input3.put("msg", msg);

		// third message should be info to answer by clicking on button
		ClientResponse result3 = client.sendRequest("POST", mainPath + "takingSurvey", input3.toString());

		try{
			JSONParser p = new JSONParser();
			JSONObject minires = (JSONObject) p.parse(result.getResponse());
			String msgText = minires.getAsString("result");
			String answerWrong = SurveyHandlerService.texts.get("reasonButton");
			Assert.assertEquals(msgText, answerWrong);
		} catch (Exception e){
			e.printStackTrace();
		}


		// correct answer
		intent = "buttonAnswer";
		messageTs = "testmessagets4";
		msg = answerOption;

		JSONObject input4 = new JSONObject();
		input4.put("surveyID", currSurvey.getSid());
		input4.put("intent", intent);
		input4.put("channel", channel);
		input4.put("email", senderEmail);
		input4.put("slackToken", token);
		input4.put("time", messageTs);
		input4.put("NameOfUser", NameOfUser);
		input4.put("buttonIntent", buttonIntent);
		input4.put("msg", msg);

		// fourth message should be second question
		ClientResponse result4 = client.sendRequest("POST", mainPath + "takingSurvey", input4.toString());

		try{
			JSONParser p = new JSONParser();
			JSONObject minires = (JSONObject) p.parse(result.getResponse());
			String msgText = minires.getAsString("result");
			String questionText = currSurvey.getQuestionAL("en").get(1).getText();
			Assert.assertEquals(msgText, questionText);
		} catch (Exception e){
			e.printStackTrace();
		}

		// skip question
		intent = "skip";
		messageTs = "skiptestmessagets4";
		msg = "skip";

		JSONObject input5 = new JSONObject();
		input5.put("surveyID", currSurvey.getSid());
		input5.put("intent", intent);
		input5.put("channel", channel);
		input5.put("email", senderEmail);
		input5.put("slackToken", token);
		input5.put("time", messageTs);
		input5.put("NameOfUser", NameOfUser);
		input5.put("buttonIntent", buttonIntent);
		input5.put("msg", msg);

		// fourth message should be second question
		ClientResponse result5 = client.sendRequest("POST", mainPath + "takingSurvey", input5.toString());

		// check if in database a skipped question
		Participant p = currSurvey.getParticipantByPID(senderEmail);
		System.out.println("size: " + p.getSkippedQuestions().size());
		Assert.assertEquals("1", p.getSkippedQuestions().size());
	}



	@Test
	public void testLimeSurveyConnector() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			ClientResponse result = client.sendRequest("POST", mainPath + "responses", "{\"loginName\":" + NameOfUser
					+ ",\"url\":" + uri + ",\"surveyID\":" + surveyID + ",\"loginPassword\":" + Password + "}");
			JSONParser p = new JSONParser();
			JSONObject resultJSON = (JSONObject) p.parse(result.getResponse());
			String resultString = resultJSON.getAsString("text");

			// check for correct form
			try{
				JSONArray resultArray = (JSONArray) p.parse(resultString);

				for(int index= 0; index < resultArray.size(); index++){
					JSONObject resultObject = (JSONObject) resultArray.get(index);

					Assert.assertTrue(resultObject.containsKey("question"));
					Assert.assertTrue(resultObject.containsKey("title"));
					Assert.assertTrue(resultObject.containsKey("type"));
					Assert.assertTrue(resultObject.containsKey("responses"));

					// check for responses form
					String responses = resultObject.getAsString("responses");
					JSONObject responsesJSON = (JSONObject) p.parse(responses);

				}

			} catch(Exception e){
				e.printStackTrace();
				Assert.fail(e.toString());
			}

			System.out.println("Result of 'testGet': " + result.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testMultipleSurveyIds() {
		// test to set up two dummy limesurvey survey and switching between them
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			JSONObject defs = setDefsMultplIDS();

			ClientResponse result = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			// choose administrating survey
			defs.put("msg", surveyID);
			ClientResponse secondResult = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			// set up first survey
			ClientResponse thirdResult = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			// now switch survey
			defs.put("intent", "change_administration");
			defs.put("msg", secondSurveyID);
			ClientResponse fourthResult = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			// set up second survey
			defs.put("intent", "set_up_survey");
			ClientResponse fifthResult = client.sendRequest("POST", mainPath + "adminSurvey", defs.toString());

			String[] sids = defs.getAsString("surveyID").split(",");
			ArrayList<Survey> surveys = new ArrayList<>();
			for(String sid : sids){
				Survey testSurvey = SurveyHandlerService.getSurveyBySurveyID(sid);
				surveys.add(testSurvey);

				try{
					testSurvey.getTitle();
				} catch (Exception e){
					e.printStackTrace();
					Assert.fail(e.toString());
				}

			}

			// testSwitchingSurvey(surveys);

			System.out.println("Result of 'testGet': " + result.getResponse().trim());

			// now delete created surveys from db
			for(String sid : sids){
				SurveyHandlerService.deleteSurvey(sid);
			}

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	private void testSwitchingSurvey(ArrayList<Survey> surveys){
		System.out.println("testSwitchingSurvey");
		// Participant p = new Participant("testPartEmail");
		try{
			Survey currSurvey = surveys.get(0);
			String currSurveyID = currSurvey.getSid();
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			JSONObject defs = setDefsMultplIDS();

			String intent = "answer";
			String channel = "testchannel";
			String senderEmail = "testemail@mail.de";
			String messageTs = "testmessagets1";
			String NameOfUser = "user";
			String msg = "message1";

			JSONObject input = new JSONObject();
			input.put("surveyID", defs.getAsString("surveyID"));
			input.put("intent", intent);
			input.put("channel", channel);
			input.put("email", senderEmail);
			input.put("time", messageTs);
			input.put("NameOfUser", NameOfUser);
			input.put("msg", msg);

			ClientResponse result = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());

			// choose survey
			input.put("msg", currSurveyID);
			input.put("time", "11");
			ClientResponse result2 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());
			JSONParser p = new JSONParser();
			JSONObject resultJSON = (JSONObject) p.parse(result.getResponse());
			String resultString = resultJSON.getAsString("text");

			// store ts
			String chooseSurveyTS = "11";

			// check all participants
			int participants = currSurvey.getParticipants().size();
			Assert.assertEquals(1, participants);

			// answer to greeting and answer one question
			input.put("msg", msg);
			input.put("time", "3");
			ClientResponse result3 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());
			input.put("msg", msg);
			input.put("time", "4");
			ClientResponse result4 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());

			Survey secSurvey = surveys.get(1);
			String secSurveyID = secSurvey.getSid();

			// switch survey
			input.put("msg", secSurveyID);
			input.put("time", chooseSurveyTS);
			ClientResponse result5 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());
			// and answer to greeting
			input.put("msg", msg);
			input.put("time", "6");
			ClientResponse result6 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());

			// answer question
			input.put("msg", msg);
			input.put("time", "7");
			ClientResponse result7 = client.sendRequest("POST", mainPath + "takingSurvey", input.toString());

			// check all participants
			participants = secSurvey.getParticipants().size();
			Assert.assertEquals(1, participants);

			// switch back
			// ClientResponse result8 = client.sendRequest("POST", mainPath + "takingSurvey", defs.toString());

		} catch (Exception e){
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}


}



