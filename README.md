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

To create a survey chatbot, first a slack app needs to be created. Creating a classic bot app is possible [here](https://api.slack.com/apps).
Since the las2peersocial-bot-manager-service uses RTM, a classic app, instead of a new app, is needed.

1. Inside the app settings, create a bot user (on the left side Features: App Home, and then )

2. Activate interactive components (on the left side: Basic Information: Add features and functionality, Interactive Components. After activating this feature, a Request URL is needed.)

3. Add the following oauth scopes under OAuth & Permissons:

    - channels:read

    - chat:write:bot

    - incoming-webhook should already be there. If not add under basic information: incoming webhooks: activate incoming webhooks sliding button



### Frontend modeling:

1. Create a bot model by following the guide [here](https://github.com/rwth-acis/Social-Bot-Framework).

2. Under OAuth & Permissons inside your app management, you will find the bot user oauth token, which needs to be added to the messenger Authentication Token in the frontend bot modeling.

3. After creating the basic bot model, add the following Bot Actions with the Service Alias "SurveyHandler":

- Action Type: Service, Function Name: takingSurvey.
  
    This bot action needs the following action parameters: (all Content Type: String and Parameter Type: body)
    
    * Name: NameOfUser and Content: the username of your "https://limesurvey.tech4comp.dbis.rwth-aachen.de/" account;   
    * Name: Password and Content: the password of your limesurvey account; 
    * Name: surveyIDString and Content: The surveyID of the survey you want to conduct. You can find this by logging into your limesurvey account and then clicking on "List surveys". In the survey list you will see on the very left side the Survey ID.

-Action Type: Service, Function Name: adminSurvey

- this bot action enables you as the admin to add participants to the survey, to start the survey and to see all answers from the participants. The bot action needs the following action parameters:
    * Name: adminmail and Content: the email of the admin user (Content Type: String and Parameter Type: body)


(routine bot action explanation will follow)



