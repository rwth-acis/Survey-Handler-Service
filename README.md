# Survey Handler Service

This service can be used to conduct surveys from LimeSurvey with bots created by the Social-Bot-Framework.

Build
--------
Execute the following command on your shell:

```shell
ant all 
```

Start
--------

To start the data-processing service, use one of the available start scripts:

Windows:

```shell
bin/start_network.bat
```

Unix/Mac:
```shell
bin/start_network.sh
```




# Starting a survey bot on slack:

### Creating a slack app
      

To create a survey chatbot, first a slack app needs to be created. Creating a classic bot app is possible [here](https://api.slack.com/apps?new_classic_app=1). (Wait for the app creation window to pop up, do not click on the green "Create New App" button).
Since the las2peersocial-bot-manager-service uses RTM, a classic app, instead of a new app, is needed.

1. Inside the app settings, create a bot user (on the left side Features: App Home, and then "Add Legacy Bot User")

2. On the left side Features: Incoming Webhooks: activate with button top right

3. Add the following oauth scopes under OAuth & Permissons:

    - channels:read

    - chat:write:bot
    
    - bot
    
    - users:read.email (users:read included)

    - incoming-webhook should already be there. If not add under basic information: incoming webhooks: activate incoming webhooks sliding button

    - Please do not update scopes!
    
Now install the app to your wished workspace. After this a token will be generated which is used in the redirect url.

1. Find the bot token: On the left side: OAuth and Permissons, the bot user oauth token (starting with xoxb).
   
2. Activate interactive components (on the left side: Basic Information: Add features and functionality, Interactive Components. After activating this feature, a Request URL is needed.)

3. Configuring the request url:

    - The ip address and port where slack posts the request (the address from the sbfmanager), slack app token, the bot name from the frontend, the instance name from the frontend and the buttonintent text are needed.
    http://ipAddress:port/SBFManager/bots/botName/appRequestURL/instanceName/buttonIntent/token.




### Frontend modeling:

1. Create a bot model by following the guide [here](https://github.com/rwth-acis/Social-Bot-Framework).

2. Under OAuth & Permissons inside your app management, you will find the bot user oauth token, which needs to be added to the messenger Authentication Token in the frontend bot modeling.

3. After creating the basic bot model, add the following Bot Actions with the Service Alias "SurveyHandler":

- Action Type: Service, Function Name: takingSurvey.

- Action Type: Service, Function Name: adminSurvey

    This bot action enables you as the admin to add participants to the survey, to start the survey and to see all answers from the participants. The bot action needs the following action parameters:
  
- The bot actions needs the following action parameters: (all Content Type: String, Static and Parameter Type: body)

    * Name: NameOfUser and Content: the username of your "https://limesurvey.tech4comp.dbis.rwth-aachen.de/" account;   
    * Name: Password and Content: the password of your limesurvey account; 
    * Name: surveyID and Content: The surveyID of the survey you want to conduct. You can find this by logging into your limesurvey account and then clicking on "List surveys". In the survey list you will see on the very left side the Survey ID.
    * Name: buttonIntent and Content: The button intent, which is going to be recognized when participants click on answer buttons
    * Name: sbfmUrl and Content: The URL where the social-bot-framework-manager can receive requests
    * Name: slackToken and Content: The slack token that has been added to the "Messenger" with type "Slack" as "Authentication Token"
    * Name: adminmail and Content: the email of the admin user (Content Type: String and Parameter Type: body)

4. It is possible to add routines for sending reminders or sending results to LimeSurvey.

    - for routine: Action Type: Service, Function Name: reminderRoutine.
    
    - for LimeSurvey results: Action Type: Service, Function Name:sendResultsToLimesurvey.

    - for MobSOS surveys results: Action Type: Service, Function Name:sendResultsToMobsosSurveys.

    - both of them need the action parameters from the main bot actions

### Creating a LimeSurvey Survey:

-enable under Participants settings: "Allow multiple responses or update responses with one token."
