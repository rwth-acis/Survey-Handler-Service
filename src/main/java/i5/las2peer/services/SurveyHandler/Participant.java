package i5.las2peer.services.SurveyHandler;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.bouncycastle.util.encoders.UTF8;
import org.web3j.abi.datatypes.Array;
import org.web3j.abi.datatypes.Bool;


import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.*;

public class Participant {

    // Database model identifier
    private String email;
    private String pid;
    private String sid;
    private String channel;
    private String lastquestion;
    private String lasttimeactive;
    private String surveyresponseid;
    private String language;
    private String languageTimestamp;
    private boolean participantcontacted;
    private boolean completedsurvey;
    private String lastChosenSurveyID;
    private String lastChosenSurveyTimestamp;
    // end Database model identifier

    // in case multiple choice with comment edited and new comment should be received
    private String qidFromEditedMCC = "";

    // if the lastquestion has subquestions (e.g. is MC) use this this AL to save and update answers for subquestions (= the choices, plus comments maybe)
    private ArrayList<Answer> currentSubquestionAnswers = new ArrayList<>();

    private ArrayList<Answer> givenAnswersAl = new ArrayList<>();
    private ArrayList<String> unaskedQuestions = new ArrayList<>();
    private ArrayList<String> skippedQuestions = new ArrayList<>();
    // first string: qid; second string: answer text

    private HashMap<String, String> answers = new HashMap<>();

    private Survey currentSurvey = null;


    public String test = "";
    String propertiesName = "i5.las2peer.services.SurveyHandler.Participant";

    public Participant(String email){
        this.addEmail(email);
        this.setPid(email);
    }

    // Based on the intent, decide what is sent back to the participant
    public Response calculateNextAction(String intent, String message, String messageId, String buttonIntent, String messageTs, JSONObject currMessage, JSONObject prevMessage, String token, boolean secondSurvey, String beginningTextEN, String beginningTextDE){

        String beginningText = "";
        JSONObject response = new JSONObject();
        Participant currParticipant = this;

        String languages = "";
        for(String s : this.currentSurvey.getLanguages()){
            languages += "\"" + s + "\"";
            languages += " or ";
        }
        // remove last "or"
        languages = languages.substring(0, languages.length() - 4);

        String languageChoosing = SurveyHandlerService.texts.get("languageChoosing") + languages + ".";


        if(!this.currentSurvey.hasMoreThanOneLanguage()){
            // set language to only language available
            this.language = this.currentSurvey.getLanguages().get(0);
            if(this.unaskedQuestions.isEmpty() && !this.participantcontacted){
                this.setUnaskedQuestions(this.currentSurvey.getSortedQuestionIds(this.language));
            }
        }
        else{
            // let participant choose language
            if(this.language == null){
                this.language = "";
                return chooseLanguage(languageChoosing);
            }
            if(!languageSet()){
                // participant got asked which language and has sent answer
                for(String currLanguage : this.currentSurvey.getLanguages()){
                    if(currLanguage.equals(message)){
                        this.language = currLanguage;
                        System.out.println("setting lts... \nTo: " + messageTs);
                        this.languageTimestamp = messageTs;
                        this.setUnaskedQuestions(this.currentSurvey.getSortedQuestionIds(this.language));
                    }
                }
                if(!languageSet()){
                    // participant sent non existent lanugage code
                    response.put("text", languageChoosing);
                    Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                    return Response.ok().entity(response).build();
                }
            }
        }


        int questionsInSurvey = this.currentSurvey.numberOfQuestions();
        String hello = SurveyHandlerService.texts.get("helloDefault");
        if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
            hello = SurveyHandlerService.texts.get("helloTelegram");
        }
        if(secondSurvey){
            hello = SurveyHandlerService.texts.get("helloDefaultAgain");
            if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                hello = SurveyHandlerService.texts.get("helloTelegramAgain");
            }
        }

        String title = "";
        String welcomeText = "";
        if(this.currentSurvey.getLanguages().get(0).equals(this.language)){
            title = currentSurvey.getTitle();
            if(currentSurvey.getWelcomeText() != null){
                welcomeText = currentSurvey.getWelcomeText();
            }
        } else{
            title = currentSurvey.getTitleOtherLanguage();
            if(currentSurvey.getWelcomeText() != null){
                welcomeText = currentSurvey.getWelcomeTextOtherLanguage();
            }
        }

        String welcomeString = SurveyHandlerService.texts.get("welcomeString").replaceAll("\\{hello\\}", hello);
        welcomeString = welcomeString.replaceAll("\\{title\\}", title);
        welcomeString = welcomeString.replaceAll("\\{questionsInSurvey\\}", String.valueOf(questionsInSurvey));
        welcomeString = welcomeString.replaceAll("\\{welcomeText\\}", welcomeText);
        String skipExplanation = SurveyHandlerService.texts.get("skipExplanation");
        String first = "";
        if(!secondSurvey){
            first = SurveyHandlerService.texts.get("first");
        }

        String changeAnswerExplanation = SurveyHandlerService.texts.get("changeAnswerExplanation");

        if(buttonIntent != null){
            // remove last "."
            changeAnswerExplanation = changeAnswerExplanation.substring(0, changeAnswerExplanation.length() - 1);
            changeAnswerExplanation += SurveyHandlerService.texts.get("changeAnswerExplanationButton");
        }
        //String resultsGetSaved = resultsGetSaved;
        String completedSurvey = SurveyHandlerService.texts.get("completedSurvey") + changeAnswerExplanation;
        String firstEdit = "";
        if(!secondSurvey){
            firstEdit = SurveyHandlerService.texts.get("firstEdit");
        }
        String surveyDoneString = SurveyHandlerService.texts.get("surveyDoneString") + firstEdit;
        String changedAnswer = SurveyHandlerService.texts.get("changedAnswer");
        String submitButton = SurveyHandlerService.texts.get("submitButton");
        String resultsGetSaved = SurveyHandlerService.texts.get("resultsGetSaved");

        if(this.language != null){
            if(languageSet()){
                if(languageIsGerman()){
                    System.out.println("language de");
                    hello = SurveyHandlerService.texts.get("helloDefaultDE");
                    if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                        hello = SurveyHandlerService.texts.get("helloTelegramDE");
                    }
                    if(secondSurvey){
                        hello = SurveyHandlerService.texts.get("helloDefaultAgainDE");
                        if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                            hello = SurveyHandlerService.texts.get("helloTelegramAgainDE");
                        }
                    }

                    welcomeString = SurveyHandlerService.texts.get("welcomeStringDE").replaceAll("\\{hello\\}", hello);
                    welcomeString = welcomeString.replaceAll("\\{title\\}", title);
                    welcomeString = welcomeString.replaceAll("\\{questionsInSurvey\\}", String.valueOf(questionsInSurvey));
                    welcomeString = welcomeString.replaceAll("\\{welcomeText\\}", welcomeText);
                    skipExplanation = SurveyHandlerService.texts.get("skipExplanationDE");
                    first = "";
                    if(!secondSurvey){
                        first = SurveyHandlerService.texts.get("firstDE");
                    }

                    changeAnswerExplanation = SurveyHandlerService.texts.get("changeAnswerExplanationDE");

                    if(buttonIntent != null){
                        // remove last "."
                        changeAnswerExplanation = changeAnswerExplanation.substring(0, changeAnswerExplanation.length() - 1);
                        changeAnswerExplanation += SurveyHandlerService.texts.get("changeAnswerExplanationButtonDE");
                    }
                    resultsGetSaved = SurveyHandlerService.texts.get("resultsGetSavedDE");
                    completedSurvey = SurveyHandlerService.texts.get("completedSurveyDE") + changeAnswerExplanation;
                    firstEdit = "";
                    if(!secondSurvey){
                        firstEdit = SurveyHandlerService.texts.get("firstEditDE");
                    }
                    surveyDoneString = SurveyHandlerService.texts.get("surveyDoneStringDE") + firstEdit;
                    changedAnswer = SurveyHandlerService.texts.get("changedAnswerDE");
                    submitButton = SurveyHandlerService.texts.get("submitButton");
                }
            }
        }
        //System.out.println("we: " + welcomeString);
        //System.out.println("beginningT length: " + beginningText.length());
        if(beginningTextEN.length() < 1 && beginningTextDE.length() < 1){
            // if no text is set in frontend, use predefined text
            beginningText = welcomeString + skipExplanation + first + changeAnswerExplanation + resultsGetSaved;
        } else if(languageIsGerman()){
            beginningText = beginningTextDE;
        } else{
            beginningText = beginningTextEN;
        }

        System.out.println("calculating next action...");

        // check if it is the first contacting
        boolean participantContacted = this.participantcontacted;

        if (!participantContacted){
            System.out.println("newly contacted...");
            return participantNewlyContacted(beginningText);
        }

        // check if participant changed language
        boolean participantChangedLanguage = participantChangedLanguage(messageTs);
        if(!prevMessage.isEmpty()){
            if(!prevMessage.isEmpty()){
                String prevTs = prevMessage.getAsString("ts");
                participantChangedLanguage = participantChangedLanguage(prevTs);
            }
        }

        if(participantChangedLanguage){
            System.out.println("participant changed language");
            return updateLanguage(message, languageChoosing);
        }

        // check if the participant changed an answer to a previous question
        boolean participantChangedAnswer = participantChangedAnswer(messageTs, currMessage, prevMessage);
        if (participantChangedAnswer){
            System.out.println("participant changed answer");
            return updateAnswer(intent, message, messageTs, currMessage, prevMessage, changedAnswer, token);
        }

        // check if participant has completed the survey
        boolean participantDone = this.completedsurvey;
        if (participantDone){
            System.out.println("participant done");
            response.put("text", completedSurvey);
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        return calcNextResponse(intent, message, buttonIntent, messageTs, messageId, currMessage, prevMessage, surveyDoneString, submitButton, token);


    }

    public void changeLanguage(String language){
        System.out.println("chagning language to: " + language);
        this.language = language;
    }

    public boolean languageSet(){
        if(this.language.length() > 0){
            return true;
        }

        return false;
    }

    public boolean languageIsGerman(){
        if(this.language.equals("de")  || this.language.startsWith("de")){
            return true;
        }

        return false;
    }

    private String answerOptionForComment(){
        String chosenOption;
        if(!this.currentSubquestionAnswers.isEmpty()){
            String questionId = this.currentSubquestionAnswers.get(0).getQid();
            chosenOption = this.currentSurvey.getQuestionByQid(questionId, this.language).getText();
            return chosenOption;
        } else{
            return null;
        }
    }

    private String answerOptionForNewComment(Answer a){
        String chosenOption;
        Question q = this.currentSurvey.getQuestionByQid(a.getQid(), this.language);
        return q.getText();
    }

    private Response AskNextQuestion(String surveyDoneString){
        // clear the answers for previous question
        this.currentSubquestionAnswers.clear();
        for(Answer a : this.givenAnswersAl){
            if(!a.isFinalized()){
                System.out.println("found answer that is not finalized: " + a.getMessageTs());
                System.out.println("deleting...");
                System.out.println(SurveyHandlerServiceQueries.deleteAnswerFromDB(a, currentSurvey.getDatabase()));
            }
        }
        boolean newQuestionGroup = false;
        boolean questionAsked = true;
        JSONObject response = new JSONObject();

        // Normal questions available
        if (this.unaskedQuestions.size() > 0){
            System.out.println("Found unasked questions. Next one is: " + this.unaskedQuestions.get(0));
            String nextId = this.unaskedQuestions.get(0);


            if(this.lastquestion != null){
                // Lastquestion is null, because no question was asked yet
                if(!this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getGid().equals(this.currentSurvey.getQuestionByQid(nextId, this.language).getGid())){
                    System.out.println("new questiongroup");
                    newQuestionGroup = true;
                }
            } else{
                System.out.println("was null");
                this.lastquestion = nextId;
            }

            Integer arrayNumber = 1;
            if(this.currentSurvey.getQuestionByQid(nextId, this.language).getType().equals(Question.qType.ARRAY.toString())){
                // last question of type array, so mutliple questions to be asked
                /*
                System.out.println("next question is array and needs to be asked multiple times: \n" + this.currentSurvey.getQuestionByQid(nextId).getType() +
                        " and size: " + this.currentSurvey.getQuestionByQid(nextId).getSubquestionAl().size() +
                        "\n only deleting if all but one subquestion have been asked" +
                        "\n check if all but one subquestion have answers" +
                        "\n now checking if that is the prelast subquestion..." +
                        "\n now checking if lastgivenanswer is answer to lastsubquestion...");
                */

                // 4 possiblilities
                // 1: array only has one question
                // 2: array has more than one question and none have been asked
                // 3: array has more thzan one question and some have been asked, but more than one remain
                // 4: array has more than one questino and all but one have been asked

                if(this.currentSurvey.getQuestionByQid(nextId, this.language).getSubquestionAl().size() == 1){
                    System.out.println("array has only one question, questino does not have to be asked again");
                }
                else if(this.skippedQuestions.contains(nextId) && !this.unaskedQuestions.isEmpty()){
                    System.out.println("array question was skipped and was asked only once, so removing from unasked question list");
                }
                else{
                    questionAsked = false;
                    System.out.println("array has more than one question");
                    if(!this.givenAnswersAl.isEmpty()){
                        Answer lastGivenAnswer = this.givenAnswersAl.get(this.givenAnswersAl.size() - 1);
                        System.out.println("last given answer to questino: " + lastGivenAnswer.getQid());

                        int i = 1;
                        for(Question subq : this.currentSurvey.getQuestionByQid(nextId, this.language).getSubquestionAl()){
                            System.out.println("first: " + subq.getQid());
                            if(lastGivenAnswer.getQid().equals(subq.getQid())){
                                System.out.println("i: " + i);
                                System.out.println("array: " + arrayNumber);
                                arrayNumber = i + 1;
                                System.out.println("array: " + arrayNumber);
                                System.out.println("now check if only one answer is missing...");
                                if(i == this.currentSurvey.getQuestionByQid(nextId, this.language).getSubquestionAl().size() - 1){
                                    System.out.println("array has more than one questino and all but one have been asked");
                                    questionAsked = true;
                                }

                            }
                            i++;
                        }
                    }

                    // else: not deleting, since no answers have been given and there are several questions in this array

                }

            }

            if(questionAsked){
                this.unaskedQuestions.remove(0);
            }

            System.out.println("setting last question to new question id");
            this.lastquestion = nextId;

            // checking if requirements to ask next questions are met
            if(!this.currentSurvey.getQuestionByQid(nextId, this.language).isRelevant(this)){
                // requirement is not met, so skipping question
                System.out.println("removing question from list of unasked questions, since requirements are not met");
                if(isSurveyDone()){
                    return surveyDone(surveyDoneString);
                }
                nextId = this.unaskedQuestions.get(0);
            }

            // update last question in database
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            String messageText = this.currentSurvey.getQuestionByQid(nextId, this.language).encodeJsonBodyAsString(newQuestionGroup,this, arrayNumber);

            // If it is starting with "[" it is a block question
            if(Character.toString(messageText.charAt(0)).equals("[") || Character.toString(messageText.charAt(0)).equals("{")){
                response.put("blocks", messageText);
            } // If it is a normal text message
            else{
                response.put("text", messageText);
            }
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        // Skipped questions available
        if (this.skippedQuestions.size() > 0){
            String nextId = this.skippedQuestions.get(0);
            this.skippedQuestions.remove(0);
            // remove skipped answer from db
            Answer skippedAnswer = getAnswer(nextId);
            SurveyHandlerServiceQueries.deleteAnswerFromDB(skippedAnswer, currentSurvey.database);
            this.lastquestion = nextId;
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            String messageText = this.currentSurvey.getQuestionByQid(nextId, this.language).encodeJsonBodyAsString(this);
            System.out.println("messageText " + messageText);
            String skipText = SurveyHandlerService.texts.get("skipText");
            if(languageIsGerman()){
                skipText = SurveyHandlerService.texts.get("skipTextDE");
            }

            if(this.currentSurvey.getQuestionByQid(nextId, this.language).isBlocksQuestion()){
                // check if messenger is slack
                if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK) ||
                        SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                    System.out.println("inside is blocks question, adding blocks...");
                    response.put("text", skipText);
                    response.put("blocks", messageText);
                }else{
                    response.put("text", skipText + messageText);
                }

            } else{
                response.put("text", skipText + messageText);
            }
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        response.put("text", "Something went wrong on bot-side :(");
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }
    public void setCurrentSurvey(Survey s){
        this.currentSurvey = s;
        this.sid = s.getSid();
    }
    public void setUnaskedQuestions(ArrayList<String> orderedQuestionList){
        this.unaskedQuestions = new ArrayList<>(orderedQuestionList);
    }


    public boolean isParticipantcontacted() {
        return this.participantcontacted;
    }

    public boolean isCompletedsurvey() {
        return this.completedsurvey;
    }

    public void setLastquestion(String lastquestion) {
        this.lastquestion = lastquestion;
    }

    public String getSurveyresponseid() {
        return surveyresponseid;
    }

    public void setSurveyresponseid(String surveyresponseid) {
        this.surveyresponseid = surveyresponseid;
    }

    public void setParticipantcontacted(boolean participantcontacted) {
        this.participantcontacted = participantcontacted;
    }

    public void setCompletedsurvey(boolean completedsurvey) {
        this.completedsurvey = completedsurvey;
    }

    public String getEmail(){
        return this.email;
    }

    public String getSurveyResponseID(){
        return this.surveyresponseid;
    }

    public String getChannel(){
        return this.channel;
    }

    public void setChannel(String channel){
        this.channel = channel;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguageTimestamp() {
        return languageTimestamp;
    }

    public void setLanguageTimestamp(String languageTimestamp) {
        this.languageTimestamp = languageTimestamp;
    }

    public ArrayList<Answer> getGivenAnswersAl() {
        return givenAnswersAl;
    }

    public ArrayList<String> getUnaskedQuestions() {
        return this.unaskedQuestions;
    }

    public ArrayList<String> getSkippedQuestions() {
        return this.skippedQuestions;
    }

    public String getLasttimeactive(){
        return this.lasttimeactive;
    }

    public String getLastChosenSurveyID() {
        return lastChosenSurveyID;
    }

    public boolean hasLastChosenSurveyID(){
        if(Objects.isNull(this.getLastChosenSurveyID())){
            return false;
        }

        return true;
    }

    public void setLastChosenSurveyID(String lastChosenSurveyID) {
        this.lastChosenSurveyID = lastChosenSurveyID;
    }

    public String getLastChosenSurveyTimestamp() {
        return lastChosenSurveyTimestamp;
    }

    public void setLastChosenSurveyTimestamp(String lastChosenSurveyTimestamp) {
        this.lastChosenSurveyTimestamp = lastChosenSurveyTimestamp;
    }

    public String getLSAnswersString(){
        String returnValue = "";
        for(Answer a : this.givenAnswersAl){
            if(a.isFinalized() && !a.isSkipped()){
                // only add finalized answers
                returnValue += this.currentSurvey.getQuestionByQid(a.getQid(), this.language).createLimeAnswerString(a);
            }
        }
        // Only delete comma if there are answers
        if(returnValue.length() > 1){
            return returnValue.substring(0, returnValue.length() - 1);
        }
        return returnValue;
    }

    public String getMSAnswersString(){
        String returnValue = "";
        for(Answer a : this.givenAnswersAl){
            if(a.isFinalized() && !a.isSkipped()){
                // only add finalized answers
                returnValue += this.currentSurvey.getQuestionByQid(a.getQid(), this.language).createMobsosAnswerString(a);
            }
        }
        // Only delete comma if there are answers
        if(returnValue.length() > 1){
            return returnValue.substring(0, returnValue.length() - 1);
        }
        return returnValue;
    }

    public Answer getAnswer(String questionID) {
        for(Answer a : this.givenAnswersAl){
            if(a.getQid().equals(questionID)){
                return a;
            }
        }
        return null;
    }

    public boolean hasAnswer(String questionID) {
        for(Answer a : givenAnswersAl){
            if(a.getQid().equals(questionID)){
                return true;
            }
        }
        return false;
    }

    public String getLastquestion(){
        return this.lastquestion;
    }

    public void setLasttimeactive(String localDateTime){
        this.lasttimeactive = localDateTime;
    }

    public void setSurveyResponseID(String responseID){
        this.surveyresponseid = responseID;
    }

    public void addEmail(String email){
        //slack adds this mailto part when messaging an email
        if(email.contains("<mailto:")){
            this.email = email.split("\\|")[1];
            this.email = this.email.split("\\>")[0];
        } else{
            this.email = email;
        }
    }

    public void addUnaskedQuestion(String questionID){
        addUnaskedQuestion(questionID, true);
    }
    public void addUnaskedQuestion(String questionID, boolean insertInFront){
        System.out.println(questionID);
        if (insertInFront) {
            this.unaskedQuestions.add(0, questionID);
        } else{
            this.unaskedQuestions.add(questionID);
        }
    }

    public void removeUnaskedQuestion(String questionID){
        this.unaskedQuestions.remove(questionID);
    }

    public void addSkippedQuestion(String questionID){
        this.skippedQuestions.add(questionID);
    }

    public void removeSkippedQuestion(String questionID){
        this.skippedQuestions.remove(questionID);
    }

    public void addLastQuestion(String questionID){
        this.lastquestion = questionID;
    }

    public void addAnswer(Answer a){
        this.getGivenAnswersAl().add(a);
    }

    public void addAnswerFromDb(Answer a){
        // check if the answer was skipped
        if (a.isSkipped()){
            this.addSkippedQuestion(a.getQid());
        } else if(!a.isFinalized()){
            this.currentSubquestionAnswers.add(a);
            this.addAnswer(a);
        } else if(a.getComment().length() > 0){
            this.addAnswer(a);
        } else{
            this.addAnswer(a);
        }
    }

    public Answer getAnswerByTS(String messageTs){
        System.out.println("trying to find answer for ts: " + messageTs);
        for(Answer a : this.givenAnswersAl){
            //System.out.println("curranswer ts: " + a.getMessageTs());
            if(a.getMessageTs() != null){
                //System.out.println("if" + a.getMessageTs().contains(messageTs));
                if(a.getMessageTs().equals(messageTs)){
                    //System.out.println("found answer that has been edited");
                    return a;
                }
            }
            if(a.getCommentTs() != null){
                if(a.getCommentTs().equals(messageTs)){
                    return a;
                }
            }

        }
        return null;
    }

    public Answer getAnswerByCommentTS(String messageTs){
        System.out.println("trying to find answer for comment ts: " + messageTs);
        for(Answer a : this.givenAnswersAl){
            //System.out.println("curranswer ts: " + a.getMessageTs());
            if(a.getMessageTs() != null){
                //System.out.println("if" + a.getMessageTs().contains(messageTs));
                if(a.getMessageTs().equals(messageTs)){
                    //System.out.println("found answer that has been edited");
                    return null;
                }
            }
            if(a.getCommentTs() != null){
                if(a.getCommentTs().equals(messageTs)){
                    return a;
                }
            }

        }
        return null;
    }

    public ArrayList<Answer> getAnswersByTS(String messageTs){
        System.out.println("trying to find answers for ts: " + messageTs);
        ArrayList<Answer> allAnswers = new ArrayList<>();
        for(Answer a : this.givenAnswersAl){
            boolean added = false;
            System.out.println("curranswer ts: " + a.getMessageTs());
            if(a.getMessageTs() != null){
                if(a.getMessageTs().equals(messageTs)){
                    allAnswers.add(a);
                    added = true;
                }
            }
            if(a.getCommentTs() != null && !added){
                if(a.getCommentTs().equals(messageTs)){
                    allAnswers.add(a);
                    added = true;
                }
            }

        }
        return allAnswers;
    }

    public Response participantNewlyContacted(String beginningText){
        JSONObject response = new JSONObject();
        // Participant has not started the survey yet
        System.out.println("participant newly contacted");
        this.participantcontacted = true;
        SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
        response.put("text", beginningText);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response chooseLanguage(String languageChoosing){
        JSONObject response = new JSONObject();
        // Participant has not started the survey yet
        System.out.println("participant going to choose language");
        response.put("text", languageChoosing);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response chooseSurvey(String titles){
        JSONObject response = new JSONObject();
        // Participant has not picked a survey yet
        System.out.println("participant going to choose survey");
        String surveyChoosing = SurveyHandlerService.texts.get("surveyChoosing") + titles + ".";
        response.put("text", surveyChoosing);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public boolean participantChangedAnswer(String messageTs, JSONObject currMessage, JSONObject prevMessage){
        if(participantChangedButtonAnswer(messageTs) || participantChangedTextAnswer(currMessage, prevMessage)){
            return true;
        }
        return false;
    }

    public boolean participantChangedLanguage(String messageTs){
        if(this.languageTimestamp != null){
            System.out.println("languagets: " + this.languageTimestamp);
            System.out.println("messagets: " + messageTs);
            if(this.languageTimestamp.equals(messageTs)){
                return true;
            }
        }

        return false;
    }

    public boolean participantChangedTextAnswer(JSONObject currMessage, JSONObject prevMessage){
        if(!prevMessage.isEmpty() && !currMessage.isEmpty()){
            return true;
        }
        return false;
    }

    public boolean participantChangedButtonAnswer(String messageTs){
        return messageTsFromEarlierMessage(messageTs);
    }

    public boolean messageTsFromEarlierMessage(String messageTs){
        System.out.println("in messagetsfromealier");
        Answer answer = getAnswerByTS(messageTs);
        if(answer != null) {
            if (answer.isFinalized()) {
                System.out.println("participant changed button answer...");
                return true;
            }
        }
        return false;
    }

    public Response updateLanguage(String message, String errorMessage){
        JSONObject response = new JSONObject();
        boolean changed = false;

        // participant got asked which language and has sent answer
        for(String currLanguage : this.currentSurvey.getLanguages()){
            if(currLanguage.equals(message)){
                if(this.language.equals(message)){
                    String languageNotChangedEN = SurveyHandlerService.texts.get("languageNotChangedEN");
                    String languageNotChangedDE = SurveyHandlerService.texts.get("languageNotChangedDE");
                    if(languageIsGerman()){
                        response.put("text", languageNotChangedDE);
                    }
                    else{
                        response.put("text", languageNotChangedEN);
                    }
                    Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                    return Response.ok().entity(response).build();
                }
                changed = true;
                this.language = currLanguage;

                String languageChangedEN = SurveyHandlerService.texts.get("languageChangedEN");
                String languageChangedDE = SurveyHandlerService.texts.get("languageChangedDE");
                if(languageIsGerman()){
                    System.out.println("changing language to german");
                    response.put("text", languageChangedDE);
                }
                else{
                    System.out.println("changing language to english");
                    response.put("text", languageChangedEN);
                }
                SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
        }
        if(!changed){
            // participant sent non existent lanugage code
            response.put("text", errorMessage);
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        response.put("text", errorMessage);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();

    }

    public Response updateAnswer(String intent, String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer, String token){
        // check if it is a skipped message, if yes ignore
        System.out.println("now updating answer");
        String check = SurveyHandlerService.check;
        Answer a = getAnswerByTS(messageTs);
        if(a != null){
            if(a.isSkipped()){
                System.out.println("skipped message edited");
                JSONObject response = new JSONObject();
                if(this.languageIsGerman()){
                    response.put("text", "Bitte editiere keine geskippten Fragen, du wirst diese am Ende der Umfrage nochmal gestellt bekommen.");
                } else{
                    response.put("text", "Please do not edit skipped answers, you will be asked the question again at the end of the survey.");
                }
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
        }
        String prevTs = prevMessage.getAsString("ts");
        if(prevTs != null){
            Answer b = getAnswerByTS(prevTs);
            if(b != null){
                if(b.isSkipped()){
                    System.out.println("skipped message edited");
                    JSONObject response = new JSONObject();
                    if(this.languageIsGerman()){
                        response.put("text", "Bitte editiere keine geskippten Fragen, du wirst diese am Ende der Umfrage nochmal gestellt bekommen.");
                    } else{
                        response.put("text", "Please do not edit skipped answers, you will be asked the question again at the end of the survey.");
                    }
                    Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                    return Response.ok().entity(response).build();
                }
            }
        }


        if(participantChangedTextAnswer(currMessage, prevMessage) && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK)){
            return updateTextAnswer(message, messageTs, currMessage, prevMessage, changedAnswer);
        }
        else if(participantChangedButtonAnswer(messageTs) && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK)){
            return updateButtonAnswer(message, messageTs, changedAnswer, token);
        }
        else if(messageTsFromEarlierMessage(messageTs) && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.ROCKETCHAT)){
            return updateTextAnswer(message, messageTs, changedAnswer, token);
        }
        else if(messageTsFromEarlierMessage(messageTs) && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
            return updateTextAnswer(message, messageTs, changedAnswer, token);
        }
        return null;
    }

    public Response updateButtonAnswer(String message, String messageTs, String changedAnswer, String token){
        System.out.println("updating button answer...");
        JSONObject response = new JSONObject();
        String check = SurveyHandlerService.check;
        Answer answer = getAnswerByTS(messageTs);

        System.out.println("parent qid: " + this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language));
        System.out.println("answer: " + answer);

        // only change answer, if it is finalized (not a subquestion answer that has not been submitted)
        answer.setPrevMessageTs(messageTs);

        // check for question type
        if(this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.SINGLECHOICECOMMENT.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.LISTDROPDOWN.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.LISTRADIO.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.DICHOTOMOUS.toString())){
            Question q = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);
            for(AnswerOption ao : q.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    answer.setText(ao.getCode());
                }
            }

            if(answer.getText() == null){
                // if the language has been changed after answering this question
                q = this.currentSurvey.getQuestionByQid(answer.getQid(), currentSurvey.getOtherLanguage(this.language));
                for(AnswerOption ao : q.getAnswerOptions()){
                    if(ao.getText().equals(message)){
                        answer.setText(ao.getCode());
                    }
                }
            }
            System.out.println("atext: " + answer.getText());

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // mark the chosen button for telegram
            if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                editMessage(q, answer, token, message);
            }
        }
        else if(this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.FIVESCALE.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.SCALE.toString())){

            answer.setText(message);

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            Question q = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);
            editMessage(q, answer, token, message);

        }
        else if(this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.GENDER.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getType().equals(Question.qType.YESNO.toString())){
            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                answer.setText("-");
            } else{
                answer.setText(message.substring(0,1));
            }

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());

            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            Question q = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);
            editMessage(q, answer, token, message);

        }
        else if(this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.ARRAY.toString())){
            System.out.println("inside array");
            Question q = this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language);
            for(AnswerOption ao : q.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    answer.setText(ao.getCode());
                }
            }

            if(answer.getText() == null){
                // if the language has been changed after answering this question
                q = this.currentSurvey.getQuestionByQid(answer.getQid(), currentSurvey.getOtherLanguage(this.language));
                for(AnswerOption ao : q.getAnswerOptions()){
                    if(ao.getText().equals(message)){
                        answer.setText(ao.getCode());
                    }
                }
            }
            System.out.println("atext: " + answer.getText());

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            editMessage(q, answer, token, message);
        }
        // check the type of the parent question, since the subquestions of mc questions are of type text
        else if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK) &&
                (this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString()))) {
            System.out.println("inside mc");
            try{
                JSONParser p = new JSONParser();
                JSONArray selectedOptionsJson = (JSONArray) p.parse(message);

                // get all answers for that message
                ArrayList<Answer> answers = getAnswersByTS(messageTs);

                ArrayList<String> nonSelected = new ArrayList<>();
                ArrayList<String> selected = new ArrayList<>();
                for(Answer a : answers){
                    boolean currSelected = false;
                    for(Object o : selectedOptionsJson){
                        JSONObject currSelectedOption = (JSONObject) o;
                        String currQid = currSelectedOption.getAsString("value");
                        if(a.getQid().equals(currQid)){
                            currSelected = true;
                        }
                    }
                    if(currSelected){
                        selected.add(a.getQid());
                    } else{
                        nonSelected.add(a.getQid());
                    }
                }

                System.out.println("nonselected" + nonSelected.toString());
                System.out.println("selected" + selected.toString());

                for(String nonS : nonSelected){
                    Answer newAnswer = getAnswer(nonS);

                    // check if newly unchosen
                    if(!newAnswer.getText().equals("N")){
                        // only update if it has been changed
                        newAnswer.setText("N");
                        newAnswer.setPrevMessageTs(messageTs);
                        SurveyHandlerServiceQueries.updateAnswerInDB(newAnswer, currentSurvey.database);
                    }
                }

                for(String s : selected){
                    Answer newAnswer = getAnswer(s);

                    // check if newly chosen
                    if(!newAnswer.getText().equals("Y")){
                        // only update if it has been changed
                        newAnswer.setText("Y");
                        newAnswer.setPrevMessageTs(messageTs);
                        SurveyHandlerServiceQueries.updateAnswerInDB(newAnswer, currentSurvey.database);

                        System.out.println("type: "+this.currentSurvey.getParentQuestionBySQQid(newAnswer.getQid(), this.language).getType());
                        if (this.currentSurvey.getParentQuestionBySQQid(newAnswer.getQid(), this.language).getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
                            // if its a mc question with comment, ask for comment
                            System.out.println("inside ask for comment after edited newanswer");
                            String option = answerOptionForNewComment(newAnswer);

                            qidFromEditedMCC = s;
                            if(option != null){
                                if(this.languageIsGerman()){
                                    response.put("text", "Bitte schreibe einen Kommentar fuer die ausgewaehlte Option: \"" + option + "\"");
                                } else{
                                    response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
                                }
                                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                                return Response.ok().entity(response).build();
                            }
                        }
                    }
                }

            }
            catch (Exception e){
                e.printStackTrace();
                return null;
            }
        }
        else if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM) &&
                (this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString()))) {
            System.out.println("inside mc telegram");

            // get subquestion that was clicked
            Question subq = null;
            Question parentQ = this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language);
            for(Question sub : this.currentSurvey.getQuestionByQid(parentQ.getQid(), this.language).getSubquestionAl()){
                System.out.println("subqgetetxt: " + sub.getText() + " and " + message);
                // TODO delete contains later after finding solution
                if(message.equals(sub.getText()) || message.equals(check + sub.getText())  || message.contains(sub.getText())){
                    subq = sub;
                    break;
                }
            }
            System.out.println("question: " + subq + " ");

            // get answer
            answer = getAnswer(subq.getQid());

            // update answer
            if((check + this.currentSurvey.getQuestionByQid(answer.getQid(), this.language).getText()).equals(message)){
                answer.setText("N");
                answer.setPrevMessageTs(answer.getMessageTs());
            } else{
                answer.setText("Y");
                answer.setPrevMessageTs(answer.getMessageTs());
            }
            System.out.println("and now " + answer.getText());

            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            Question q = this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language);
            editMessage(q, answer, token, message);
        }

        String questionText = "";
        Question edited = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);
        //Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL());
        if(edited.isSubquestion() && !this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getType().equals(Question.qType.ARRAY.toString())){
            questionText = this.currentSurvey.getParentQuestionBySQQid(answer.getQid(), this.language).getText();
            //Question.getQuestionById(edited.getParentQid(), currentSurvey.getQuestionAL()).getText();
        } else{
            questionText = edited.getText();
        }
        String changed = "";
        if(languageIsGerman()){
            changed = SurveyHandlerService.texts.get("changedDE").replaceAll("\\{questionText\\}", questionText);
        }
        else{
            changed = SurveyHandlerService.texts.get("changed").replaceAll("\\{questionText\\}", questionText);
        }
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();

    }

    public Response updateTextAnswer(String message, String messageTs, String changedAnswer, String token){
        // Rocket chat text answer or telegram text answer edited
        JSONObject response = new JSONObject();
        // the participant edited a text answer
        System.out.println("text answer editing detected...");

        Answer answer = getAnswerByTS(messageTs);
        System.out.println(answer.getQid());

        if(answer == null){
            // the answer to the original text has been deleted, this can happen with MC comment messages, when unchecking the mc box
            response.put("text", "The answer you edited is no longer relevant.");
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        Question answerEdited = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);

        System.out.println("blocks: " + answerEdited.isBlocksQuestion() + " messenger telegram: " + SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM) +
                " qid " + answerEdited.getQid() + " type: " + answerEdited.getType());
        System.out.println("subq: " + answerEdited.isSubquestion() + " parent qid: " + answerEdited.getParentQid());

        boolean blocksQuestion = answerEdited.isBlocksQuestion();
        if(answerEdited.isSubquestion() && answerEdited.getParentQuestion().isBlocksQuestion()){
            blocksQuestion = true;
        }

        boolean commentForBlocksQuestion = (getAnswerByCommentTS(messageTs) != null);
        if(blocksQuestion && !commentForBlocksQuestion && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
            System.out.println("\nis button answer from telegram instead\n");
            return updateButtonAnswer(message, messageTs, changedAnswer, token);
        }

        if(answerEdited.isSubquestion()){
            answerEdited = this.currentSurvey.getQuestionByQid(answerEdited.getParentQid(), this.language);
        }

        answer.setPrevMessageTs(messageTs);
        String type = answerEdited.getType();
        if(answerEdited.isSubquestion()){
            type = this.currentSurvey.getQuestionByQid(answerEdited.getParentQid(), this.language).getType();
        }

        if(blocksQuestion && commentForBlocksQuestion && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
            System.out.println("\nis comment for button answer\n");
            answer.setComment(message);
            answer.setPrevMessageTs(answer.getMessageTs());
        }

        // rocket chat
        if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.ROCKETCHAT)){
            if(!answerEdited.answerIsPlausible(message, SurveyHandlerService.check)){
                response.put("text", answerEdited.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }


            if(type.equals(Question.qType.LISTDROPDOWN.toString()) ||
                    type.equals(Question.qType.LISTRADIO.toString()) ||
                    type.equals(Question.qType.ARRAY.toString()) ||
                    type.equals(Question.qType.DICHOTOMOUS.toString())){

                String text = answerEdited.getAnswerOptionByIndex(Integer.parseInt(message)).getCode();
                answer.setText(text);
                answer.setMessageTs(messageTs);
            }

            if(type.equals(Question.qType.YESNO.toString())){
                System.out.println("single choice yes no recognized");

                if(message.equals("1")){
                    answer.setText("Y");
                } else if(message.equals("2")){
                    answer.setText("N");
                } else if(message.equals("3")){
                    answer.setText("-");
                }
                answer.setMessageTs(messageTs);
            }

            if(type.equals(Question.qType.GENDER.toString())){
                System.out.println("single choice gender recognized");

                if(message.equals("1")){
                    answer.setText("F");
                } else if(message.equals("2")){
                    answer.setText("M");
                } else if(message.equals("3")){
                    answer.setText("-");
                }
                answer.setMessageTs(messageTs);

            }

            if(type.equals(Question.qType.SINGLECHOICECOMMENT.toString())){
                String chosenAO = message.split(":")[0];
                String comment = message.split(":")[1];

                String text = answerEdited.getAnswerOptionByIndex(Integer.parseInt(chosenAO)).getCode();
                answer.setText(text);
                answer.setComment(comment);
                answer.setMessageTs(messageTs);
                answer.setCommentTs(messageTs);
            }

            if(type.equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString())){
                ArrayList<Answer> allA = this.getAnswersByTS(messageTs);
                ArrayList<String> nonchosen = new ArrayList<>();

                if(message.equals("-")){
                    // no option was chosen, add all to notchosen array
                    for(Answer a : allA){
                        a.setText("N");
                        SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.getDatabase());
                    }
                }else{
                    // split message into chosen options
                    String[] chosenOptions = message.split(",");
                    ArrayList<String> chosen = new ArrayList<>();
                    System.out.println("chosenoptions: " + chosenOptions);
                    // find non chosen
                    for(Answer a : allA){
                        boolean chosenOption = false;
                        Question q = this.currentSurvey.getQuestionByQid(a.getQid(), this.language);
                        Question pq = this.currentSurvey.getQuestionByQid(q.getParentQid(), this.language);
                        for(String s : chosenOptions){
                            if(a.getQid().equals(pq.getSubquestionByIndex(s).getQid())){
                                chosenOption = true;
                                chosen.add(a.getQid());
                            }
                        }
                        if(!chosenOption){
                            nonchosen.add(q.getQid());
                        }
                    }

                    // answer is in valid form, so save to db
                    for(String co : chosen){
                        Answer a = this.getAnswer(co);
                        Question q = this.currentSurvey.getQuestionByQid(a.getQid(), this.language);
                        a.setText("Y");
                        a.setPrevMessageTs(messageTs);

                        SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                    }

                }
                System.out.println("all non chosen: " + nonchosen.toString() + " or all non chosen: " + message.equals("-"));
                for(String qs : nonchosen){
                    Answer a = this.getAnswer(qs);
                    a.setText("N");
                    a.setPrevMessageTs(messageTs);
                    SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                }
            }

            if(type.equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())){

                ArrayList<Answer> allA = this.getAnswersByTS(messageTs);
                ArrayList<String> nonchosen = new ArrayList<>();
                ArrayList<String> chosenQids = new ArrayList<>();

                if(message.equals("-")){
                    // no option was chosen, add all to notchosen array
                    for(Answer a : allA){
                        a.setText("N");
                        SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.getDatabase());
                    }
                }else{
                    // split message into chosen options
                    String[] all = message.split(";");
                    ArrayList<String> chosen = new ArrayList<>();
                    ArrayList<String> comments = new ArrayList<>();
                    for(String s : all){
                        chosen.add(s.split(":")[0]);
                        comments.add(s.split(":")[1]);
                    }
                    System.out.println("chosen: " + chosen);
                    System.out.println("comments: " + comments);
                    // find non chosen
                    for(Answer a : allA){
                        boolean chosenOption = false;
                        Question q = this.currentSurvey.getQuestionByQid(a.getQid(), this.language);
                        Question pq = this.currentSurvey.getQuestionByQid(q.getParentQid(), this.language);
                        for(String s : chosen){
                            if(a.getQid().equals(pq.getSubquestionByIndex(s).getQid())){
                                chosenOption = true;
                                chosenQids.add(a.getQid());
                            }
                        }

                        if(!chosenOption){
                            nonchosen.add(q.getQid());
                        }
                    }
                    // answer is in valid form, so save to db
                    for(String co : chosenQids){
                        Answer a = this.getAnswer(co);
                        Question q = this.currentSurvey.getQuestionByQid(a.getQid(), this.language);
                        a.setText("Y");
                        a.setPrevMessageTs(messageTs);
                        a.setComment(comments.get(0));
                        a.setCommentTs(messageTs);
                        comments.remove(0);

                        SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                    }

                }
                System.out.println("all non chosen: " + nonchosen.toString());
                for(String qs : nonchosen){
                    Answer a = this.getAnswer(qs);
                    a.setText("N");
                    a.setComment("");
                    a.setPrevMessageTs(messageTs);
                    a.setCommentTs(messageTs);
                    SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                }
            }
        }

        // normal text
        if(answerEdited.isTextQuestion()){
            if(!answerEdited.answerIsPlausible(message, SurveyHandlerService.check)){
                response.put("text", answerEdited.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            // text answer edited
            answer.setText(message);
        }

        System.out.println("updating text answer in database...");
        SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.database);
        String changed = "";
        if(languageIsGerman()){
            changed = SurveyHandlerService.texts.get("changedDE").replaceAll("\\{questionText\\}", answerEdited.getText());
        }
        else{
            changed = SurveyHandlerService.texts.get("changed").replaceAll("\\{questionText\\}", answerEdited.getText());
        }
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response updateTextAnswer(String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer){
        // Slack text answer edited
        JSONObject response = new JSONObject();
        String check = SurveyHandlerService.check;
        // the participant edited a text answer
        System.out.println("text answer editing detected...");

        String originalTs = prevMessage.getAsString("ts");
        String newTs= currMessage.getAsString("ts");
        String newText = currMessage.getAsString("text");

        System.out.println("ogts: " + originalTs);
        System.out.println("nts: " + newTs);
        System.out.println("newtext: " + newText);

        Answer answer = getAnswerByTS(originalTs);
        System.out.println(answer.getQid());

        if(answer == null){
            // the answer to the original text has been deleted, this can happen with MC comment messages, when unchecking the mc box
            response.put("text", "The answer you edited is no longer relevant.");
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        Question answerEdited = this.currentSurvey.getQuestionByQid(answer.getQid(), this.language);
        //Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL());
        if(!answerEdited.answerIsPlausible(message, check)){
            response.put("text", answerEdited.reasonAnswerNotPlausible());
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        answer.setPrevMessageTs(originalTs);

        if(answer.getComment().length() > 0){
            // if the question requires a comment, this has been edited (button presses only pass on curr message)
            System.out.println("updating comment");
            answer.setPrevMessageTs(answer.getMessageTs());
            answer.setComment(newText);
            answer.setCommentTs(newTs);
        }
        else{
            // the answer text has been edited
            System.out.println("updating text");
            answer.setText(newText);
            answer.setMessageTs(newTs);
        }

        System.out.println("updating text answer in database...");
        boolean updated = SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.database);
        String changed = "";
        if(languageIsGerman()){
            changed = SurveyHandlerService.texts.get("changedDE").replaceAll("\\{questionText\\}", answerEdited.getText());
        }
        else{
            changed = SurveyHandlerService.texts.get("changed").replaceAll("\\{questionText\\}", answerEdited.getText());
        }
        if(!updated){
            changed = "An error occured while updating your answer.";
        }
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response calcNextResponse(String intent, String message, String buttonIntent, String messageTs, String messageId, JSONObject currMessage, JSONObject prevMessage, String surveyDoneString, String submitButton, String token){
        JSONObject response = new JSONObject();
        Response res = null;

        System.out.println("slack is used: " + SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK));

        if(this.lastquestion != null){
            System.out.println("last question is not null recognized");
            Answer newAnswer = new Answer();
            Question lastQuestion = this.currentSurvey.getQuestionByQid(this.lastquestion, this.language);
            // Subquestions also have the same group id as the main question
            newAnswer.setGid(lastQuestion.getGid());
            newAnswer.setPid(this.getPid());
            newAnswer.setSid(this.getSid());
            newAnswer.setDtanswered(LocalDateTime.now().toString());
            newAnswer.setSkipped(true);
            newAnswer.setFinalized(true);
            newAnswer.setMessageId(messageId);
            newAnswer.setMessageTs(messageTs);

            //newAnswer.setQid(this.lastquestion);

            System.out.println("intent is: " + intent);
            System.out.println("buttonintent is: " + buttonIntent);

            // Participant wants to skip a question
            boolean skipped = false;
            if(intent.equals("skip")){
                System.out.println("message: " + message);
                System.out.println(message.equals("skip"));
                //skip intent does not get recognized correctly
                if(message.equals("skip") || message.equals("Skip") || message.length()<5){
                    try{
                        if(this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).isMandatory()){
                            if(languageIsGerman()){
                                response.put("text", "Diese Frage ist obligatorisch, bitte beantworte diese.");
                            }
                            else{
                                response.put("text", "This question is mandatory, please answer it.");
                            }
                            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                            return Response.ok().entity(response).build();
                        }
                    } catch(Exception e){
                        System.out.println("mandatory value is not set");
                    }

                    if(this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getType().equals(Question.qType.ARRAY.toString())){
                        // skipped array questions only skip the current subquestion, not the entire question
                        if(this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionAl().size() == 1
                            || this.givenAnswersAl.isEmpty()){
                            // add subquestion qid to skipped question list
                            // index for subquestions starts at 1
                            this.skippedQuestions.add(this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionByIndex("1").getQid());
                            newAnswer.setQid(this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionByIndex("1").getQid());
                        }
                        else{
                            if(!this.givenAnswersAl.isEmpty()){
                                Answer lastGivenAnswer = this.givenAnswersAl.get(this.givenAnswersAl.size() - 1);
                                System.out.println("last given answer to question: " + lastGivenAnswer.getQid());
                                int index = 1;
                                for(Question subq : this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionAl()){
                                    System.out.println("first: " + subq.getQid());
                                    index++;
                                    if(lastGivenAnswer.getQid().equals(subq.getQid())){
                                        // add qid of current skipped subquestion
                                        this.skippedQuestions.add(subq.getSubquestionByIndex(String.valueOf(index)).getQid());
                                        newAnswer.setQid(subq.getSubquestionByIndex(String.valueOf(index)).getQid());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    else{
                        this.skippedQuestions.add(this.lastquestion);
                        newAnswer.setQid(this.lastquestion);
                    }
                    this.givenAnswersAl.add(newAnswer);
                    SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                    skipped = true;
                }
            }

            if(!skipped){
                if(intent.equals(buttonIntent)){
                    res = newButtonAnswer(newAnswer, lastQuestion, token, message, surveyDoneString, submitButton);
                }
                else if(lastQuestion.isBlocksQuestion() && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
                    // check if last question blocks but comment expected
                    if(!this.currentSubquestionAnswers.isEmpty() && lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
                        System.out.println("curent subquestion answers not empty, so expecting comment");
                        res = newTextAnswer(newAnswer, lastQuestion, message, surveyDoneString, submitButton);
                    }
                    else if(!this.currentSubquestionAnswers.isEmpty() && lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                        // check if answers have been submitted
                        if(this.currentSubquestionAnswers.get(0).isFinalized()){
                            System.out.println("expecting comment since answers have been submitted");
                            // if one has been submitted, all of them have
                            res = newTextAnswer(newAnswer, lastQuestion, message, surveyDoneString, submitButton);
                        }
                        else{
                            res = newButtonAnswer(newAnswer, lastQuestion, token, message, surveyDoneString, submitButton);
                        }
                    }
                    else{
                        System.out.println("lastquestion was blcoks and messenger telegram");
                        res = newButtonAnswer(newAnswer, lastQuestion, token, message, surveyDoneString, submitButton);
                    }
                } else {
                    res = newTextAnswer(newAnswer, lastQuestion, message, surveyDoneString, submitButton);
                }
            }


            if(res == Response.serverError().build()){
                // parsing went wrong
                return res;
            }
            else if(res == Response.noContent().build()){
                // waiting for comment for single choice question
                return res;
            }
            else if(res != null){
                // not asking next question, but send specific response
                return res;
            }

        } else{
            // This is the first response after question to start the survey was sent
            // do not add confirmation to start survey to answers
        }

        // Calculate next question to ask
        res = surveyDone(surveyDoneString);
        if(res != null){
            return res;
        }

        // Check what questions are left
        return this.AskNextQuestion(surveyDoneString);
    }

    public Response newButtonAnswer(Answer newAnswer, Question lastQuestion, String token, String message, String surveyDoneString, String submitButton){
        JSONObject response = new JSONObject();
        System.out.println("inside newbuttonanswer...");
        String check = SurveyHandlerService.check;
        String messageTs = newAnswer.getMessageTs();
        String messageId = newAnswer.getMessageId();
        // message is a list of selected options in json format or a simple text message

        if (lastQuestion.getType().equals(Question.qType.LISTDROPDOWN.toString()) || lastQuestion.getType().equals(Question.qType.LISTRADIO.toString()) ||
                lastQuestion.getType().equals(Question.qType.DICHOTOMOUS.toString())){
            System.out.println("list question detected");
            if(!lastQuestion.answerIsPlausible(message, check)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                newAnswer.setText("-");
            }
            // we receive the single choice answer as text directly, so find answer option code
            for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    newAnswer.setText(ao.getCode());
                }
            }
            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(this.lastquestion);
            this.givenAnswersAl.add(newAnswer);
            System.out.println("a saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);


            if(lastQuestion.getType().equals(Question.qType.DICHOTOMOUS.toString())){
                // mark the chosen button for dichotomous question
                editMessage(lastQuestion, newAnswer, token, message);
            }

        } else if(lastQuestion.getType().equals(Question.qType.GENDER.toString()) || lastQuestion.getType().equals(Question.qType.YESNO.toString())){
            if(!lastQuestion.answerIsPlausible(message, check)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            // If mask question type Gender or Yes/No question, adjust message to only add one letter (limesurvey only accepts this format)
            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(this.lastquestion);

            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                newAnswer.setText("-");
            } else{
                newAnswer.setText(message.substring(0,1));
            }

            this.givenAnswersAl.add(newAnswer);

            System.out.println("b saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            // color the chosen button
            editMessage(lastQuestion, newAnswer, token, message);

        } else if(lastQuestion.getType().equals(Question.qType.ARRAY.toString())){
            if(!lastQuestion.answerIsPlausible(message, check)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                newAnswer.setText("-");
            }
            // we receive the single choice answer as text directly, so find answer option code
            for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    newAnswer.setText(ao.getCode());
                }
            }

            Integer index = 1;
            if(!this.getGivenAnswersAl().isEmpty() && this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionAl().size() > 1){
                String aQid = this.getGivenAnswersAl().get(this.getGivenAnswersAl().size() - 1).getQid();
                //System.out.println("aqid: " + aQid);
                int i = 1;
                for(Question q : lastQuestion.getSubquestionAl()){
                    if(q.getQid().equals(aQid)){
                        index = i+1;
                    }
                    i++;
                }
            }

            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(lastQuestion.getSubquestionByIndex(String.valueOf(index)).getQid());
            this.givenAnswersAl.add(newAnswer);
            System.out.println("c saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

        } else if(lastQuestion.getType().equals(Question.qType.FIVESCALE.toString()) ||
                lastQuestion.getType().equals(Question.qType.SCALE.toString())) {
            if (!lastQuestion.answerIsPlausible(message, check)) {
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            // we receive a number of 1-5 directly

            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(this.lastquestion);
            newAnswer.setText(message);
            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                newAnswer.setText("-");
            }
            this.givenAnswersAl.add(newAnswer);

            System.out.println("d saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            // color the chosen button
            editMessage(lastQuestion, newAnswer, token, message);

        }else if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
            if (!lastQuestion.answerIsPlausible(message, check)) {
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            this.currentSubquestionAnswers.clear();
            Answer objectToRemove = new Answer();
            for(Answer a : givenAnswersAl){
                // find all answer objects to remove
                if(!a.isFinalized()){
                    objectToRemove = a;
                }
            }
            // delete answer object
            givenAnswersAl.remove(objectToRemove);
            SurveyHandlerServiceQueries.deleteAnswerFromDB(objectToRemove, currentSurvey.database);

            newAnswer.setSkipped(false);
            if(message.equals("No Answer") || message.equals("Keine Antwort")){
                newAnswer.setText("-");
            }
            for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    newAnswer.setText(ao.getCode());
                }
            }
            newAnswer.setQid(this.lastquestion);
            newAnswer.setPrevMessageTs(messageTs);
            newAnswer.setFinalized(false);
            this.currentSubquestionAnswers.add(newAnswer);
            this.givenAnswersAl.add(newAnswer);
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
            if(!newAnswer.getText().equals("-")){
                // return no content to wait for the comment
                return Response.noContent().build();
            }

        } else if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
            // lastquestion is MC, handle accordingly
            JSONParser p = new JSONParser();
            JSONArray selectedOptionsJson;
            boolean submitButtonPressed = message.equals(submitButton);
            System.out.println("submitButtonPressed is " + submitButtonPressed + " and message is: " + message);


            if (submitButtonPressed){
                System.out.println("Submit button press detected");
                System.out.println("curr subquestiuonanswers: " + this.currentSubquestionAnswers.toString());

                if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString())){
                    // submit button of multiple choice question sent (multiple choice options are given as subquestions)
                    for(Question q : lastQuestion.getSubquestionAl()){
                        boolean chosen = false;
                        for(Answer a : this.currentSubquestionAnswers){
                            if(a.getQid().equals(q.getQid())){
                                this.givenAnswersAl.add(a);
                                a.setFinalized(true);
                                System.out.println("submit pressed, updating to finalized: " + a.isFinalized());
                                SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                                chosen = true;
                            }
                        }
                        if(!chosen){
                            Answer currAnswer = new Answer();
                            // Subquestions also have the same group id as the main question
                            currAnswer.setGid(q.getGid());
                            currAnswer.setPid(this.pid);
                            currAnswer.setSid(q.getSid());
                            currAnswer.setMessageId(messageId);
                            currAnswer.setMessageTs(messageTs);
                            currAnswer.setSkipped(false);
                            currAnswer.setDtanswered(LocalDateTime.now().toString());
                            currAnswer.setQid(q.getQid());
                            currAnswer.setText("N");
                            currAnswer.setFinalized(true);

                            this.givenAnswersAl.add(currAnswer);
                            SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                        }
                    }

                }

                if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
                    // Submit button pressed, now save for all non chosen options no and send back answers to selected options to get comments

                    // save all non chosen options as "N"
                    for(Question q : lastQuestion.getSubquestionAl()){
                        boolean chosen = false;
                        for(Answer a : this.currentSubquestionAnswers){
                            if(a.getQid().equals(q.getQid())){
                                chosen = true;
                            }
                        }
                        if(!chosen){
                            Answer currAnswer = new Answer();
                            // Subquestions also have the same group id as the main question
                            currAnswer.setGid(q.getGid());
                            currAnswer.setPid(this.pid);
                            currAnswer.setSid(q.getSid());
                            currAnswer.setMessageId(messageId);
                            currAnswer.setMessageTs(messageTs);
                            currAnswer.setSkipped(false);
                            currAnswer.setDtanswered(LocalDateTime.now().toString());
                            currAnswer.setQid(q.getQid());
                            currAnswer.setText("N");
                            currAnswer.setFinalized(true);

                            this.givenAnswersAl.add(currAnswer);
                            SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                        }
                    }


                    String option = answerOptionForComment();
                    if(option != null){
                        if(this.languageIsGerman()){
                            response.put("text", "Bitte schreibe einen Kommentar fuer die ausgewaehlte Option: \"" + option + "\"");
                        } else{
                            response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
                        }
                        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                        return Response.ok().entity(response).build();
                    }

                }

                // delete the submit button
                String messageText = lastQuestion.encodeJsonBodyAsString(false, true, "", this);
                editMessage(lastQuestion, newAnswer, token, messageText);
                // submit button handling done

            } else {
                if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK)){
                    // no submit button pressed, but update to chosen options and to db
                    try{
                        selectedOptionsJson = (JSONArray) p.parse(message);}
                    catch (Exception e){
                        e.printStackTrace();
                        System.out.println("Failed parsing buttonIntent message.");
                        return Response.serverError().build();
                    }


                    System.out.println("deleting currentsubquestionanswers...");
                    this.currentSubquestionAnswers.clear();
                    ArrayList<Answer> objectsToRemove = new ArrayList<>();
                    for(Answer a : givenAnswersAl){
                        // find all answer objects to remove
                        if(!a.isFinalized()){
                            objectsToRemove.add(a);
                        }
                    }
                    for(Answer a : objectsToRemove){
                        // delete answer objects
                        givenAnswersAl.remove(a);
                        SurveyHandlerServiceQueries.deleteAnswerFromDB(a, currentSurvey.database);
                    }

                    // creating new answer objects
                    for(Object jarrayObject : selectedOptionsJson) {
                        String text = "";
                        JSONObject jO = (JSONObject) jarrayObject;
                        String value = jO.getAsString("value");

                        try{
                            String textObjectString = jO.getAsString("text");
                            JSONObject textJO = (JSONObject) p.parse(textObjectString);
                            text = textJO.getAsString("text");

                            if(!lastQuestion.answerIsPlausible(text, check)){
                                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                                return Response.ok().entity(response).build();
                            }
                        } catch(Exception e){
                            e.printStackTrace();
                            System.out.println("Failed to parse textObject.");
                            return Response.serverError().build();
                        }

                        System.out.println("parsing mesage got text: " + text);
                        Answer currAnswer = new Answer();
                        // Subquestions also have the same group id as the main question
                        currAnswer.setGid(lastQuestion.getGid());
                        currAnswer.setPid(this.pid);
                        currAnswer.setSid(this.sid);
                        currAnswer.setSkipped(false);
                        currAnswer.setDtanswered(LocalDateTime.now().toString());
                        currAnswer.setQid(value);
                        currAnswer.setText("Y");
                        currAnswer.setFinalized(false);
                        this.currentSubquestionAnswers.add(currAnswer);
                        this.givenAnswersAl.add(currAnswer);
                        SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                        System.out.println("curr subquestion answers: " +this.currentSubquestionAnswers + "size: " + this.currentSubquestionAnswers.size());
                    }
                    System.out.println("all subquestion answers: " +this.currentSubquestionAnswers);
                    return Response.noContent().build();
                }
                else if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){

                    if (!lastQuestion.answerIsPlausible(message, check)) {
                        response.put("text", lastQuestion.reasonAnswerNotPlausible());
                        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                        return Response.ok().entity(response).build();
                    }

                    // get subquestion that was clicked
                    Question subq = null;
                    for(Question sub : this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionAl()){
                        System.out.println("subqgetetxt: " + sub.getText() + " and " + message);
                        // TODO find solution for check check
                        if(message.equals(sub.getText()) || message.equals(check + sub.getText()) || message.contains(sub.getText())){
                            subq = sub;
                            break;
                        }
                    }
                    System.out.println("question: " + subq + " ");

                    Answer answer = getAnswer(subq.getQid());
                    if(answer != null){
                        // update answer
                        System.out.println("curr text " + answer.getText() + " and starts with check: " + check + "? " + (check + answer.getText()).equals(message));
                        if((check + answer.getText()).equals(message)){
                            answer.setText("N");
                        } else{
                            answer.setText("Y");
                        }
                        System.out.println("and now " + answer.getText());

                        SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

                        // color the chosen button
                        editMessage(lastQuestion, answer, token, message);

                    } else{
                        newAnswer.setQid(subq.getQid());
                        newAnswer.setSkipped(false);
                        newAnswer.setFinalized(false);
                        newAnswer.setText("Y");

                        this.currentSubquestionAnswers.add(newAnswer);
                        this.givenAnswersAl.add(newAnswer);

                        System.out.println("e saving new answer to database");
                        SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

                        // color the chosen button
                        editMessage(lastQuestion, newAnswer, token, message);
                    }

                    System.out.println("all subquestion answers: " + this.currentSubquestionAnswers);
                    return Response.noContent().build();
                }
            }
        }
        else{
            System.out.println("button click, but lastquestiontype not button question detected, returning no content...");
            return Response.noContent().build();
        }

        return null;

    }

    public Response newTextAnswer(Answer newAnswer, Question lastQuestion, String message, String surveyDoneString, String submitButton){
        JSONObject response = new JSONObject();
        String check = SurveyHandlerService.check;
        String messageId = newAnswer.getMessageId();
        String messageTs = newAnswer.getMessageTs();


        System.out.println("has no currentsubquestionAnswers: " + this.currentSubquestionAnswers.isEmpty());
        System.out.println("type: " + lastQuestion.getType());

        // check if an answer from a mcc question is expected
        if(this.qidFromEditedMCC.length() > 0){
            Answer a = getAnswer(this.qidFromEditedMCC);
            a.setComment(message);
            a.setCommentTs(messageTs);
            a.setFinalized(true);
            a.setPrevMessageTs(messageTs);
            this.givenAnswersAl.remove(getAnswer(this.qidFromEditedMCC));
            this.givenAnswersAl.add(a);
            SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
            this.qidFromEditedMCC = "";
            // only one answer option more chosen so return
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().build();
            /*
            String option = answerOptionForComment();
            qidFromEditedMCC = "";
            if(option != null){
                response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
                return Response.ok().entity(response).build();
            }
             */
        }

        // Check if it is a text answer for button questions in rocket chat
        if(lastQuestion.isBlocksQuestion() && SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.ROCKETCHAT)){
            if(message.length() == 2 && String.valueOf(message.charAt(1)).equals(".")){
                // check if message asking for a number contains a "."
                JSONParser p = new JSONParser();
                try{
                    Integer.parseInt(message.substring(0,1));
                    // remove "."
                    message = message.substring(0,1);
                } catch (Exception e){
                }
            }
            System.out.println("blocks question and rocketchat recognized");

            if(!lastQuestion.answerIsPlausible(message, check)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            // it is a button question in rocket chat
            if(lastQuestion.getType().equals(Question.qType.LISTRADIO.toString()) ||
                    lastQuestion.getType().equals(Question.qType.LISTDROPDOWN.toString()) ||
                    lastQuestion.getType().equals(Question.qType.DICHOTOMOUS.toString())){
                System.out.println("single choice list or dicho recognized");


                // answer is in valid form, so save to db
                for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                    if(String.valueOf(ao.getIndexi()).equals(message)){
                        newAnswer.setText(ao.getCode());
                    }
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("f saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            // it is an array question in rocket chat
            if(lastQuestion.getType().equals(Question.qType.ARRAY.toString())){
                System.out.println("array recognized");

                Integer index = 1;
                if(!this.getGivenAnswersAl().isEmpty() && this.currentSurvey.getQuestionByQid(this.lastquestion, this.language).getSubquestionAl().size() > 1){


                    String aQid = this.getGivenAnswersAl().get(this.getGivenAnswersAl().size() - 1).getQid();
                    //System.out.println("aqid: " + aQid);

                    int i = 1;
                    for(Question q : lastQuestion.getSubquestionAl()){
                        if(q.getQid().equals(aQid)){
                            index = i+1;
                        }
                        i++;
                    }
                }
                //System.out.println("index: " + index);


                // answer is in valid form, so save to db
                for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                    if(String.valueOf(ao.getIndexi()).equals(message)){
                        newAnswer.setText(ao.getCode());
                    }
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                //System.out.println("qid: " + lastQuestion.getSubquestionByIndex(String.valueOf(index)).getQid());
                newAnswer.setQid(lastQuestion.getSubquestionByIndex(String.valueOf(index)).getQid());
                //this.currentSubquestionAnswers.add(newAnswer);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("g saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            if(lastQuestion.getType().equals(Question.qType.FIVESCALE.toString())){
                System.out.println("5 scale recognized");

                // answer is in valid form, so save to db
                newAnswer.setText(message);
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("h saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            if(lastQuestion.getType().equals(Question.qType.SCALE.toString())){
                System.out.println("single choice scale recognized");

                // answer is in valid form, so save to db
                for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                    if(String.valueOf(ao.getIndexi()).equals(message)){
                        newAnswer.setText(ao.getText());
                    }
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("i saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            if(lastQuestion.getType().equals(Question.qType.YESNO.toString())){
                System.out.println("single choice yes no recognized");

                if(message.equals("1")){
                    newAnswer.setText("Y");
                } else if(message.equals("2")){
                    newAnswer.setText("N");
                } else if(message.equals("3")){
                    newAnswer.setText("-");
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("j saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
            }

            if(lastQuestion.getType().equals(Question.qType.GENDER.toString())){
                System.out.println("single choice gender recognized");

                if(message.equals("1")){
                    newAnswer.setText("F");
                } else if(message.equals("2")){
                    newAnswer.setText("M");
                } else if(message.equals("3")){
                    newAnswer.setText("-");
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("k saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
            }

            if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
                System.out.println("single choice comment recognized");

                String chosenAO = message.split(":")[0];
                String comment = message.split(":")[1];

                // answer is in valid form, so save to db
                for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                    System.out.println("chosen: " + chosenAO + " index: " + String.valueOf(ao.getIndexi()));
                    if(String.valueOf(ao.getIndexi()).equals(chosenAO)){
                        newAnswer.setText(ao.getCode());
                    }
                }
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                newAnswer.setComment(comment);
                newAnswer.setCommentTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("l saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString())){
                System.out.println("multiple choice recognized");

                ArrayList<String> nonchosen = new ArrayList<>();
                ArrayList<String> chosen = new ArrayList<>();
                System.out.println("symbol: - and message: " + message + " equal: " + message.equals("-"));
                if(message.equals("-")){
                    // no option was chosen, add all to notchosen array
                    for(Question q : lastQuestion.getSubquestionAl()){
                        nonchosen.add(q.getQid());
                    }
                }else{
                    // split message into chosen options
                    String[] chosenOptions = message.split(",");

                    // find non chosen
                    for(Question q : lastQuestion.getSubquestionAl()){
                        boolean chosenOption = false;
                        for(String a : chosenOptions){
                            if(q.equals(lastQuestion.getSubquestionByIndex(a))){
                                chosen.add(q.getQid());
                                chosenOption = true;
                            }
                        }
                        if(!chosenOption){
                            nonchosen.add(q.getQid());
                        }
                    }

                    // answer is in valid form, so save to db
                    for(String co : chosen){
                        newAnswer.setText("Y");
                        newAnswer.setSkipped(false);
                        newAnswer.setFinalized(true);
                        newAnswer.setQid(co);
                        this.givenAnswersAl.add(newAnswer);
                        System.out.println("m saving new answer to database");
                        SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                    }

                }
                System.out.println("all non chosen: " + nonchosen.toString());
                for(String qs : nonchosen){
                    Question q = this.currentSurvey.getQuestionByQid(qs, this.language);
                    Answer currAnswer = new Answer();
                    // Subquestions also have the same group id as the main question
                    currAnswer.setGid(q.getGid());
                    currAnswer.setPid(this.pid);
                    currAnswer.setSid(q.getSid());
                    currAnswer.setSkipped(false);
                    currAnswer.setDtanswered(LocalDateTime.now().toString());
                    currAnswer.setQid(q.getQid());
                    currAnswer.setMessageTs(messageTs);
                    currAnswer.setMessageId(messageId);
                    currAnswer.setText("N");
                    currAnswer.setFinalized(true);

                    this.givenAnswersAl.add(currAnswer);
                    SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                }

            }



            if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                System.out.println("multiple choice comment recognized");

                ArrayList<String> nonchosen = new ArrayList<>();

                if(message.equals("-")){
                    // no option was chosen, add all to notchosen array
                    for(Question q : lastQuestion.getSubquestionAl()){
                        nonchosen.add(q.getQid());
                    }
                }else{
                    // split message into chosen options
                    String[] all = message.split(";");
                    ArrayList<String> chosen = new ArrayList<>();
                    ArrayList<String> comments = new ArrayList<>();
                    for(String s : all){
                        chosen.add(s.split(":")[0]);
                        comments.add(s.split(":")[1]);
                    }

                    // find non chosen
                    for(Question q : lastQuestion.getSubquestionAl()){
                        boolean chosenOption = false;
                        for(String a : chosen){
                            if(q.equals(lastQuestion.getSubquestionByIndex(a))){
                                chosenOption = true;
                            }
                        }
                        if(!chosenOption){
                            nonchosen.add(q.getQid());
                        }
                    }

                    // answer is in valid form, so save to db
                    System.out.println("all chosen: " + chosen);
                    for(String co : chosen){
                        newAnswer.setText("Y");
                        newAnswer.setSkipped(false);
                        newAnswer.setFinalized(true);
                        newAnswer.setQid(lastQuestion.getSubquestionByIndex(co).getQid());
                        newAnswer.setComment(comments.get(0));
                        newAnswer.setCommentTs(messageTs);
                        this.givenAnswersAl.add(newAnswer);
                        System.out.println("n saving new answer to database");
                        SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                        comments.remove(0);
                    }
                }

                System.out.println("all nonchosen: " + nonchosen);
                for(String qs : nonchosen){
                    Question q = this.currentSurvey.getQuestionByQid(qs, this.language);
                    Answer currAnswer = new Answer();
                    // Subquestions also have the same group id as the main question
                    currAnswer.setGid(q.getGid());
                    currAnswer.setPid(this.pid);
                    currAnswer.setSid(q.getSid());
                    currAnswer.setSkipped(false);
                    currAnswer.setDtanswered(LocalDateTime.now().toString());
                    currAnswer.setQid(q.getQid());
                    currAnswer.setMessageTs(messageTs);
                    currAnswer.setMessageId(messageId);
                    currAnswer.setText("N");
                    currAnswer.setCommentTs(messageTs);
                    currAnswer.setComment("");
                    currAnswer.setFinalized(true);

                    this.givenAnswersAl.add(currAnswer);
                    SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                }


            }

            return null;

        }


        // Check if a button message needs to be saved as well, if an subquestion answer has been saved this way, the messenger is nor roket.chat
        if(!this.currentSubquestionAnswers.isEmpty()){
            System.out.println("inside not empty currsubquestionanswers");
            // if it has subquestionanswers it is a single choice or multiple choice with comment question, and this current message is the comment
            if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
                // there is only one answer in the currentSubquestion, because it is single choice
                newAnswer = getAnswer(this.lastquestion);
                newAnswer.setComment(message);
                newAnswer.setCommentTs(messageTs);
                for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                    if(ao.getText().equals(this.currentSubquestionAnswers.get(0).getText())){
                        newAnswer.setText(ao.getCode());
                    }
                }
                newAnswer.setQid(this.lastquestion);
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                this.givenAnswersAl.add(newAnswer);
                SurveyHandlerServiceQueries.updateAnswerInDB(newAnswer, currentSurvey.database);
            } else if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                System.out.println("inside multicomments...");

                // add comment for the answer and ask for comment for next option
                Answer a = this.currentSubquestionAnswers.get(0);
                System.out.println("this.currentsubquestionanswer qid: " + a.getQid());

                a.setComment(message);
                a.setCommentTs(messageTs);
                a.setFinalized(true);
                a.setPrevMessageTs(a.getMessageTs());
                // neccessary?
                this.givenAnswersAl.remove(this.currentSubquestionAnswers.get(0));
                this.givenAnswersAl.add(a);
                SurveyHandlerServiceQueries.updateAnswerInDB(a, currentSurvey.database);
                this.currentSubquestionAnswers.remove(0);
                String option = answerOptionForComment();
                if(option != null){
                    if(this.languageIsGerman()){
                        response.put("text", "Bitte schreibe einen Kommentar fuer die ausgewaehlte Option: \"" + option + "\"");
                    } else{
                        response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
                    }
                    Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                    return Response.ok().entity(response).build();
                } else{
                    for(Question q : lastQuestion.getSubquestionAl()){
                        boolean chosen = false;
                        for(Answer answer : givenAnswersAl){
                            chosen = true;
                        }
                        if(!chosen){
                            Answer currAnswer = new Answer();
                            // Subquestions also have the same group id as the main question
                            currAnswer.setGid(q.getGid());
                            currAnswer.setPid(this.pid);
                            currAnswer.setSid(q.getSid());
                            currAnswer.setSkipped(false);
                            currAnswer.setDtanswered(LocalDateTime.now().toString());
                            currAnswer.setQid(q.getQid());
                            currAnswer.setMessageTs(messageTs);
                            currAnswer.setMessageId(messageId);
                            currAnswer.setText("N");
                            currAnswer.setFinalized(true);

                            this.givenAnswersAl.add(currAnswer);
                            SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                        }
                    }
                }

            }
            else{
                System.out.println("checking for type went wrong. Type: " + lastQuestion.getType());
                return Response.serverError().build();
            }


        } else{

            if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString()) && this.currentSubquestionAnswers.isEmpty()){
                // single choice comment requires selcted answer before comment
                if(this.languageIsGerman()){
                    response.put("text", "Bitte waehle erst eine Antwortmoeglichkeit aus und sende dann deinen Kommentar.");
                } else{
                    response.put("text", "Please select an answer first, then resend your comment.");
                }
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString()) && this.currentSubquestionAnswers.isEmpty()){
                // single choice comment requires selcted answer before comment
                if(this.languageIsGerman()){
                    response.put("text", "Bitte waehle erst Antwortmoeglichkeiten aus, du wirst dann nach jeweils einen Kommentar gefragt.");
                } else{
                    response.put("text", "Please select options first, then you will be asked to write your comments");
                }
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            if(!lastQuestion.answerIsPlausible(message, check)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible());
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            // adding normal answer

            System.out.println("adding text answer with qid..." + this.lastquestion);
            newAnswer.setGid(lastQuestion.getGid());
            newAnswer.setPid(this.getPid());
            newAnswer.setSid(this.getSid());
            newAnswer.setSkipped(false);
            newAnswer.setQid(this.lastquestion);
            newAnswer.setText(message);
            newAnswer.setMessageTs(messageTs);
            newAnswer.setMessageId(messageId);
            this.givenAnswersAl.add(newAnswer);

            System.out.println("saving new answer to database at the end of function");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
        }

        return null;
    }

    public Response surveyDone(String surveyDoneString){
        System.out.println("questions left unasked: " + this.unaskedQuestions.size() + " skipped left: " + this.skippedQuestions.size());
        JSONObject response = new JSONObject();
        // Check if survey is completed
        if (this.unaskedQuestions.size() == 0 && this.skippedQuestions.size() == 0){
            // No questions remaining, survey done.
            this.completedsurvey = true;
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            response.put("text", surveyDoneString); //+ currParticipant.getEmail() + currParticipant.getUnaskedQuestions() + currParticipant.getSkippedQuestions()
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }
        return null;
    }

    public boolean isSurveyDone(){
        return (this.unaskedQuestions.size() == 0 && this.skippedQuestions.size() == 0);
    }

    public void editMessage(Question q, Answer a, String token, String message){
        String messageText = q.encodeJsonBodyAsString(false, false, message, this);
        if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.SLACK)){
            String messageTs = a.getMessageTs();
            editSlackMessage(token, messageTs, messageText);
        }
        else if(SurveyHandlerService.messenger.equals(SurveyHandlerService.messenger.TELEGRAM)){
            String messageId = a.getMessageId();
            editTelegramMessage(token, messageId, messageText);
        }
    }

    public void editTelegramMessage(String token, String messageId, String messageText){
        System.out.println("now editing telegram message...");
        // post request to sbfmanager to edit slack message
        JSONObject content = new JSONObject();
        content.put("ts", messageId);
        //messageText = messageText.replaceAll(":check:", "\u2713 ");
        System.out.println("messagtext: " + messageText);
        content.put("blocks", messageText);
        System.out.println("beofre sendintg");

        String SBFManagerURL = "SBFManager";
        String uri = SBFManagerURL + "/editMessage/" + token + "/" + channel;
        HashMap<String, String> head = new HashMap<>();

        MiniClient client = new MiniClient();
        SurveyHandlerService service = new SurveyHandlerService();
        System.out.println("sbfmurl_ " + service.sbfmURL);
        client.setConnectorEndpoint(service.sbfmURL);



        ClientResponse result = client.sendRequest("POST", uri, content.toString(), "application/json", "*/*", head);
        System.out.println("after sendintg");
        String resString = result.getResponse();
        System.out.println(resString);

    }

    public void editSlackMessage(String token, String messageTs, String messageText){

        // post request to sbfmanager to edit slack message
        String SBFManagerURL = "SBFManager";
        String uri = SBFManagerURL + "/editMessage/" + token + "/" + email;
        HashMap<String, String> head = new HashMap<>();

        MiniClient client = new MiniClient();
        SurveyHandlerService service = new SurveyHandlerService();
        System.out.println("sbfmurl_ " + service.sbfmURL);
        client.setConnectorEndpoint(service.sbfmURL);

        JSONObject content = new JSONObject();
        content.put("ts", messageTs);
        content.put("blocks", messageText);
        System.out.println("beofre sendintg");

        ClientResponse result = client.sendRequest("POST", uri, content.toString(), "application/json", "*/*", head);
        System.out.println("after sendintg");
        String resString = result.getResponse();
        System.out.println(resString);

    }


    @Override
    public String toString() {
        return String.format(this.email);
    }
}
