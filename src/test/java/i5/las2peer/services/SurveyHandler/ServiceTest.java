package i5.las2peer.services.SurveyHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

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


/*

	@Test
	public void testSendResultsToLimesurvey() {
		try {

			String urlParameters = "token=xoxb-1627131500899-1875835159892-r288iln4PddzCwPVvRsCyYMj&user=U01JF3ZMPLK";
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

 */

/*

			JSONParser p = new JSONParser();
			MiniClient mini = new MiniClient();
			mini.setConnectorEndpoint("https://slack.com/api/users.info");
			//mini.setLogin(testAgent.getIdentifier(), testPass);
			HashMap<String, String> head = new HashMap<String, String>();
			String tok = "\"token\":\"xoxb-1627131500899-1875835159892-r288iln4PddzCwPVvRsCyYMj\"";
			String u = "\"user\":\"U01JF3ZMPLK\"";
			System.out.println(tok);
			System.out.println(u);
			String contentS ="token=xoxb-1627131500899-1875835159892-r288iln4PddzCwPVvRsCyYMj";
			System.out.println(contentS);
			ClientResponse minires = mini.sendRequest("POST", "", contentS, MediaType.APPLICATION_FORM_URLENCODED, MediaType.MEDIA_TYPE_WILDCARD, head);
			System.out.println("minires: " + minires);
			String cResult = java.net.URLDecoder.decode(minires.getResponse(), StandardCharsets.UTF_8.name());
			System.out.println(cResult);


			JSONParser p = new JSONParser();
			JSONObject resultJ = (JSONObject) p.parse(result);
			String userS = resultJ.getAsString("user");
			JSONObject userJson = (JSONObject) p.parse(userS);
			String profileS = userJson.getAsString("profile");
			JSONObject profileJson = (JSONObject) p.parse(profileS);
			String email = profileJson.getAsString("email");
			System.out.println(email);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

 */


/*
	@Test
	public void testSendResultsToLimesurvey() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			client.setLogin(testAgent.getIdentifier(), testPass);

			ClientResponse result = client.sendRequest("POST", mainPath + "takingSurvey", "");
			Assert.assertEquals(200, result.getHttpCode());
			Assert.assertEquals("adam", result.getResponse().trim());// YOUR RESULT VALUE HERE
			System.out.println("Result of 'testGet': " + result.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}


 */

/*

	// If its not the admin, the intent was parsed wrong, so pass on to takingSurvey
	MiniClient mini = new MiniClient();
				mini.setConnectorEndpoint("SurveyHandler/takingSurvey");
	HashMap<String, String> head = new HashMap<String, String>();

	ClientResponse miniCR = mini.sendRequest("POST", "", input, MediaType.APPLICATION_JSON, "", head);
	JSONObject miniJ = (JSONObject) p.parse(miniCR.getResponse());
	String responseS = miniJ.getAsString("response");
				response.put("text", responseS);
				return Response.ok().entity(response).build();



 */




}



