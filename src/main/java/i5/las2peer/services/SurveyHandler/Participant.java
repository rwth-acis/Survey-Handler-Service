package i5.las2peer.services.SurveyHandler;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.web3j.abi.datatypes.Array;
import org.web3j.abi.datatypes.Bool;


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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

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
    private boolean participantcontacted;
    private boolean completedsurvey;
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



    public Participant(String email){
        this.addEmail(email);
        this.setPid(email);
    }

    // Based on the intent, decide what is sent back to the participant
    public Response calculateNextAction(String intent, String message, String buttonIntent, String messageTs, JSONObject currMessage, JSONObject prevMessage, String token, boolean secondSurvey){
        int questionsInSurvey = this.currentSurvey.getSortedQuestions().size();
        String hello = "Hello :slightly_smiling_face: \n";
        if(secondSurvey){
            hello = "Hello again :slightly_smiling_face: \n";
        }
        String welcomeString = hello + "Just send me a message and I will conduct the survey \"" + currentSurvey.getTitle() + "\" with you. There are " + questionsInSurvey + " questions for you to answer.\n \n Here are some hints:\n";
        String skipExplanation = " To skip a question just send \"skip\", you will be able to answer them later if you want.";
        String first = "";
        if(!secondSurvey){
            first = " To start a second survey you need to answer all questions from the first one.";
        }

        String changeAnswerExplanation = "\nTo change your given answer edit your message, by clicking on the 3 points next to your text message and then choosing \"Edit Message\".";

        if(buttonIntent != null){
            // remove last "."
            changeAnswerExplanation = changeAnswerExplanation.substring(0, changeAnswerExplanation.length() - 1);
            changeAnswerExplanation += ", or click on a button again. For multiple choice questions it is not neccessary to submit the answers again.";
        }
        String resultsGetSaved = "\nYour responses will be saved continuously.";
        String completedSurvey = "You already completed the survey." + changeAnswerExplanation;
        String firstEdit = "";
        if(!secondSurvey){
            firstEdit = " If you would like to change any answer to this survey, please do so before starting the next survey.";
        }
        String surveyDoneString = "Thank you for completing this survey :slightly_smiling_face:" + firstEdit;
        String answerNotFittingQuestion = "Your answer does not fit the question. Please change your answer.";
        String changedAnswer = "Your answer has been changed sucessfully.";
        String submittButtonPressedMessage = "Submit";
        JSONObject response = new JSONObject();
        Participant currParticipant = this;

        System.out.println("calculating next action...");

        // check which messenger is used
        boolean slack = false;
        if(token.length() > 0){
            // a slack token is set
            slack = true;
        }

        // check if it is the first contacting
        boolean participantContacted = this.participantcontacted;
        if (!participantContacted){
            return participantNewlyContacted(welcomeString, skipExplanation, changeAnswerExplanation, resultsGetSaved, first);
        }

        // check if the participant changed an answer to a previous question
        boolean participantChangedAnswer = participantChangedAnswer(messageTs, currMessage, prevMessage);
        if (participantChangedAnswer){
            System.out.println("participant changed answer");
            return updateAnswer(intent, message, messageTs, currMessage, prevMessage, changedAnswer, token, slack);
        }

        // check if participant has completed the survey
        boolean participantDone = this.completedsurvey;
        if (participantDone){
            response.put("text", completedSurvey);
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        return calcNextResponse(intent, message, buttonIntent, messageTs, currMessage, prevMessage, surveyDoneString, answerNotFittingQuestion, submittButtonPressedMessage, token);


    }

    private String answerOptionForComment(){
        String chosenOption;
        if(!this.currentSubquestionAnswers.isEmpty()){
            String questionId = this.currentSubquestionAnswers.get(0).getQid();
            chosenOption = this.currentSurvey.getQuestionByQid(questionId).getText();
            return chosenOption;
        } else{
            return null;
        }
    }

    private String answerOptionForNewComment(Answer a){
        String chosenOption;
        Question q = this.currentSurvey.getQuestionByQid(a.getQid());
        return q.getText();
    }

    private Response AskNextQuestion(boolean slack){
        // clear the answers for previous question
        for(Answer a : this.givenAnswersAl){
            if(!a.isFinalized()){
                System.out.println("found answer that is not finalized: " + a.getMessageTs());
                System.out.println("deleting...");
                System.out.println(SurveyHandlerServiceQueries.deleteAnswerFromDB(a, currentSurvey.getDatabase()));
            }
        }
        boolean newQuestionGroup = false;
        JSONObject response = new JSONObject();

        // Normal questions available
        if (this.unaskedQuestions.size() > 0){
            System.out.println("Found unasked questions. Next one is: " + this.unaskedQuestions.get(0));
            String nextId = this.unaskedQuestions.get(0);


            if(this.lastquestion != null){
                // Lastquestion is null, because no question was asked yet
                if(!this.currentSurvey.getQuestionByQid(this.lastquestion).getGid().equals(this.currentSurvey.getQuestionByQid(nextId).getGid())){
                    System.out.println("new questiongroup");
                    newQuestionGroup = true;
                }
            } else{
                System.out.println("was null");
                this.lastquestion = nextId;
            }

            Integer arrayNumber = 1;
            if(this.currentSurvey.getQuestionByQid(nextId).getType().equals(Question.qType.ARRAY.toString())){
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

                if(this.currentSurvey.getQuestionByQid(nextId).getSubquestionAl().size() == 1){
                    System.out.println("array has only one question, questino does not have to be asked again");
                    this.unaskedQuestions.remove(0);
                } else{
                    System.out.println("array has more than one question");
                    if(!this.givenAnswersAl.isEmpty()){
                        Answer lastGivenAnswer = this.givenAnswersAl.get(this.givenAnswersAl.size() - 1);
                        System.out.println("last given answer to questino: " + lastGivenAnswer.getQid());

                        int i = 1;
                        for(Question subq : this.currentSurvey.getQuestionByQid(nextId).getSubquestionAl()){
                            if(lastGivenAnswer.getQid().equals(subq.getQid())){
                                System.out.println("i: " + i);
                                arrayNumber = i + 1;
                                System.out.println("now check if only one answer is missing...");
                                if(i == this.currentSurvey.getQuestionByQid(nextId).getSubquestionAl().size() - 1){
                                    System.out.println("array has more than one questino and all but one have been asked");
                                    this.unaskedQuestions.remove(0);
                                }

                            }
                            i++;
                        }
                    }

                    // else: not deleting, since no answers have been given and there are several questions in this array

                }

            }

            System.out.println("setting last question to new question id");
            this.lastquestion = nextId;

            // update last question in database
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(newQuestionGroup, false, "", this, slack, arrayNumber);

            // If it is starting with "[" it is a block question
            if(Character.toString(messageText.charAt(0)).equals("[")){
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
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(this, slack);
            System.out.println("messageText " + messageText);
            String skipText = "This question was skipped by you, you can answer now or skip again: \n";

            if(this.currentSurvey.getQuestionByQid(nextId).isBlocksQuestion()){
                // check if messenger is slack
                if(slack){
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

    public String getLSAnswersString(){
        String returnValue = "";
        for(Answer a : this.givenAnswersAl){
            if(a.isFinalized() && !a.isSkipped()){
                // only add finalized answers
                returnValue += this.currentSurvey.getQuestionByQid(a.getQid()).createLimeAnswerString(a);
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
                returnValue += this.currentSurvey.getQuestionByQid(a.getQid()).createMobsosAnswerString(a);
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
            System.out.println("curranswer ts: " + a.getMessageTs());
            if(a.getMessageTs() != null){
                if(a.getMessageTs().equals(messageTs)){
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

    public Response participantNewlyContacted(String welcomeString, String skipExplanation, String changeAnswerExplanation, String resultsGetSaved, String second){
        JSONObject response = new JSONObject();
        // Participant has not started the survey yet
        System.out.println("participant newly contacted");
        this.participantcontacted = true;
        SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
        response.put("text", welcomeString + skipExplanation + second + changeAnswerExplanation + resultsGetSaved);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public boolean participantChangedAnswer(String messageTs, JSONObject currMessage, JSONObject prevMessage){
        if(participantChangedButtonAnswer(messageTs) || participantChangedTextAnswer(currMessage, prevMessage)){
            return true;
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
        Answer answer = getAnswerByTS(messageTs);
        if(answer != null) {
            if (answer.isFinalized()) {
                System.out.println("participant changed button answer...");
                return true;
            }
        }
        return false;
    }

    public Response updateAnswer(String intent, String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer, String token, boolean slack){
        // check if it is a skipped message, if yes ignore
        Answer a = getAnswerByTS(messageTs);
        if(a != null){
            if(a.isSkipped()){
                System.out.println("skipped message edited");
                JSONObject response = new JSONObject();
                response.put("text", "Please do not edit skipped answers, you will be asked the question again at the end of the survey.");
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
        }
        String prevTs = prevMessage.getAsString("ts");
        Answer b = getAnswerByTS(prevTs);
        if(b != null){
            if(b.isSkipped()){
                System.out.println("skipped message edited");
                JSONObject response = new JSONObject();
                response.put("text", "Please do not edit skipped answers, you will be asked the question again at the end of the survey.");
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
        }


        if(participantChangedTextAnswer(currMessage, prevMessage) && slack){
            return updateTextAnswer(intent, message, messageTs, currMessage, prevMessage, changedAnswer);
        }
        else if(participantChangedButtonAnswer(messageTs) && slack){
            return updateButtonAnswer(intent, message, messageTs, changedAnswer, token);
        }
        else if(messageTsFromEarlierMessage(messageTs) && !slack){
            return updateTextAnswer(intent, message, messageTs, changedAnswer);
        }
        return null;
    }

    public Response updateButtonAnswer(String intent, String message, String messageTs, String changedAnswer, String token){
        JSONObject response = new JSONObject();
        Answer answer = getAnswerByTS(messageTs);

        System.out.println("parent qid: " + this.currentSurvey.getParentQuestionBySQQid(answer.getQid()));

        // only change answer, if it is finalized (not a subquestion answer that has not been submitted)
        answer.setPrevMessageTs(messageTs);

        // check for question type
        if(this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.SINGLECHOICECOMMENT.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.LISTDROPDOWN.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.LISTRADIO.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.DICHOTOMOUS.toString())){
            Question q = this.currentSurvey.getQuestionByQid(answer.getQid());
            for(AnswerOption ao : q.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    answer.setText(ao.getCode());
                }
            }

            System.out.println("atext: " + answer.getText());

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

        }
        else if(this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.FIVESCALE.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.SCALE.toString())){

            answer.setText(message);

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            String messageText = currentSurvey.getQuestionByQid(answer.getQid()).encodeJsonBodyAsString(false, true, message, this, true);
            editSlackMessage(token, messageTs, messageText);

        }
        else if(this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.GENDER.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.YESNO.toString())){
            if(message.equals("No Answer")){
                answer.setText("-");
            } else{
                answer.setText(message.substring(0,1));
            }

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());

            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            String messageText = currentSurvey.getQuestionByQid(answer.getQid()).encodeJsonBodyAsString(false, true, message, this, true);
            editSlackMessage(token, messageTs, messageText);

        }
        // check the type of the parent question, since the subquestions of mc questions are of type text
        else if(this.currentSurvey.getParentQuestionBySQQid(answer.getQid()).getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                this.currentSurvey.getParentQuestionBySQQid(answer.getQid()).getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
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

                        System.out.println("type: "+this.currentSurvey.getParentQuestionBySQQid(newAnswer.getQid()).getType());
                        if (this.currentSurvey.getParentQuestionBySQQid(newAnswer.getQid()).getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
                            // if its a mc question with comment, ask for comment
                            System.out.println("inside ask for comment after edited newanswer");
                            String option = answerOptionForNewComment(newAnswer);

                            qidFromEditedMCC = s;
                            if(option != null){
                                response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
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


        String questionText = "";
        Question edited = this.currentSurvey.getQuestionByQid(answer.getQid());
        //Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL());
        if(edited.isSubquestion()){
            questionText = this.currentSurvey.getParentQuestionBySQQid(answer.getQid()).getText();
            //Question.getQuestionById(edited.getParentQid(), currentSurvey.getQuestionAL()).getText();
        } else{
            questionText = edited.getText();
        }
        String changed = "Your answer to the question \"" + questionText + "\" has been changed successfully.";
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();

    }

    public Response updateTextAnswer(String intent, String message, String messageTs, String changedAnswer){
        // Rocket chat text answer edited
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

        Question answerEdited = this.currentSurvey.getQuestionByQid(answer.getQid());
        if(answerEdited.isSubquestion()){
            answerEdited = this.currentSurvey.getQuestionByQid(answerEdited.getParentQid());
        }
        if(!answerEdited.answerIsPlausible(message, false)){
            response.put("text", answerEdited.reasonAnswerNotPlausible(false));
            Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
            return Response.ok().entity(response).build();
        }

        answer.setPrevMessageTs(messageTs);
        String type = answerEdited.getType();
        if(answerEdited.isSubquestion()){
            type = this.currentSurvey.getQuestionByQid(answerEdited.getParentQid()).getType();
        }

        // rocket chat
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
                    Question q = this.currentSurvey.getQuestionByQid(a.getQid());
                    Question pq = this.currentSurvey.getQuestionByQid(q.getParentQid());
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
                    Question q = this.currentSurvey.getQuestionByQid(a.getQid());
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
                    Question q = this.currentSurvey.getQuestionByQid(a.getQid());
                    Question pq = this.currentSurvey.getQuestionByQid(q.getParentQid());
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
                    Question q = this.currentSurvey.getQuestionByQid(a.getQid());
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

        System.out.println("updating answer in database...");
        SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.database);
        String changed = "Your answer to the question \"" + answerEdited.getText() + "\" has been changed successfully.";
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response updateTextAnswer(String intent, String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer){
        // Slack text answer edited
        JSONObject response = new JSONObject();
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

        Question answerEdited = this.currentSurvey.getQuestionByQid(answer.getQid());
        //Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL());
        if(!answerEdited.answerIsPlausible(message, true)){
            response.put("text", answerEdited.reasonAnswerNotPlausible(true));
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

        System.out.println("updating answer in database...");
        SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.database);
        String changed = "Your answer to the question \"" + answerEdited.getText() + "\" has been changed successfully.";
        response.put("text", changed);
        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
        return Response.ok().entity(response).build();
    }

    public Response calcNextResponse(String intent, String message, String buttonIntent, String messageTs, JSONObject currMessage, JSONObject prevMessage, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage, String token){
        JSONObject response = new JSONObject();
        Response res = null;

        // check which messenger is used
        boolean slack = false;
        if(token.length() > 0){
            // a slack token is set
            slack = true;
        }

        System.out.println("slack is used: " + slack);

        if(this.lastquestion != null){
            System.out.println("last question is not null recognized");
            Answer newAnswer = new Answer();
            Question lastQuestion = this.currentSurvey.getQuestionByQid(this.lastquestion);
            // Subquestions also have the same group id as the main question
            newAnswer.setGid(lastQuestion.getGid());
            newAnswer.setPid(this.getPid());
            newAnswer.setSid(this.getSid());
            newAnswer.setDtanswered(LocalDateTime.now().toString());
            newAnswer.setSkipped(true);
            newAnswer.setFinalized(true);

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
                    this.skippedQuestions.add(this.lastquestion);
                    newAnswer.setQid(this.lastquestion);
                    this.givenAnswersAl.add(newAnswer);
                    SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                    skipped = true;
                }
            }

            if(!skipped){
                if(intent.equals(buttonIntent)){
                    res = newButtonAnswer(newAnswer, lastQuestion, token, message, messageTs, surveyDoneString, answerNotFittingQuestion, submittButtonPressedMessage);
                } else {
                    res = newTextAnswer(newAnswer, lastQuestion, message, messageTs, surveyDoneString, answerNotFittingQuestion, submittButtonPressedMessage, slack);
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
        res = checkIfSurveyDone(surveyDoneString);
        if(res != null){
            return res;
        }

        // Check what questions are left
        return this.AskNextQuestion(slack);
    }

    public Response newButtonAnswer(Answer newAnswer, Question lastQuestion, String token, String message, String messageTs, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage){
        JSONObject response = new JSONObject();
        // message is a list of selected options in json format or a simple text message

        if (lastQuestion.getType().equals(Question.qType.LISTDROPDOWN.toString()) || lastQuestion.getType().equals(Question.qType.LISTRADIO.toString()) ||
            lastQuestion.getType().equals(Question.qType.DICHOTOMOUS.toString())){
            if(!lastQuestion.answerIsPlausible(message, true)){
                response.put("text", answerNotFittingQuestion);
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
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
            newAnswer.setMessageTs(messageTs);
            this.givenAnswersAl.add(newAnswer);
            System.out.println("saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

        } else if(lastQuestion.getType().equals(Question.qType.GENDER.toString()) || lastQuestion.getType().equals(Question.qType.YESNO.toString())){
            if(!lastQuestion.answerIsPlausible(message, true)){
                response.put("text", answerNotFittingQuestion);
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            // If mask question type Gender or Yes/No question, adjust message to only add one letter (limesurvey only accepts this format)
            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(this.lastquestion);
            newAnswer.setMessageTs(messageTs);

            if(message.equals("No Answer")){
                newAnswer.setText("-");
            } else{
                newAnswer.setText(message.substring(0,1));
            }

            this.givenAnswersAl.add(newAnswer);

            System.out.println("saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            // color the chosen button
            String messageText = lastQuestion.encodeJsonBodyAsString(false, true, message, this, true);
            editSlackMessage(token, messageTs, messageText);

        } else if(lastQuestion.getType().equals(Question.qType.FIVESCALE.toString()) ||
                lastQuestion.getType().equals(Question.qType.SCALE.toString())) {
            if (!lastQuestion.answerIsPlausible(message, true)) {
                response.put("text", answerNotFittingQuestion);
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            // we receive a number of 1-5 directly

            newAnswer.setSkipped(false);
            newAnswer.setFinalized(true);
            newAnswer.setQid(this.lastquestion);
            newAnswer.setMessageTs(messageTs);
            newAnswer.setText(message);

            this.givenAnswersAl.add(newAnswer);

            System.out.println("saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            // color the chosen button
            String messageText = lastQuestion.encodeJsonBodyAsString(false, true, message, this, true);
            editSlackMessage(token, messageTs, messageText);

        }else if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
            if (!lastQuestion.answerIsPlausible(message, true)) {
                response.put("text", answerNotFittingQuestion);
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
            for(AnswerOption ao : lastQuestion.getAnswerOptions()){
                if(ao.getText().equals(message)){
                    newAnswer.setText(ao.getCode());
                }
            }
            newAnswer.setQid(this.lastquestion);
            newAnswer.setMessageTs(messageTs);
            newAnswer.setPrevMessageTs(messageTs);
            newAnswer.setFinalized(false);
            this.currentSubquestionAnswers.add(newAnswer);
            this.givenAnswersAl.add(newAnswer);
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
            // return no content to wait for the comment
            return Response.noContent().build();

        } else if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString())) {
            // lastquestion is MC, handle accordingly
            JSONParser p = new JSONParser();
            JSONArray selectedOptionsJson;
            boolean submitButtonPressed = message.equals(submittButtonPressedMessage);
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
                                a.setPrevMessageTs(messageTs);
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
                            currAnswer.setSkipped(false);
                            currAnswer.setDtanswered(LocalDateTime.now().toString());
                            currAnswer.setQid(q.getQid());
                            currAnswer.setMessageTs(messageTs);
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
                            currAnswer.setSkipped(false);
                            currAnswer.setDtanswered(LocalDateTime.now().toString());
                            currAnswer.setQid(q.getQid());
                            currAnswer.setMessageTs(messageTs);
                            currAnswer.setPrevMessageTs(messageTs);
                            currAnswer.setText("N");
                            currAnswer.setFinalized(true);

                            this.givenAnswersAl.add(currAnswer);
                            SurveyHandlerServiceQueries.addAnswerToDB(currAnswer, currentSurvey.database);
                        }
                    }


                    String option = answerOptionForComment();
                    if(option != null){
                        response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
                        Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                        return Response.ok().entity(response).build();
                    }

                }

                // delete the submit button
                //String messageText = lastQuestion.encodeJsonBodyAsString(false, true, "", this);
                //editSlackMessage(token, messageTs, messageText);

                // submit button handling done

            } else {
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

                        if(!lastQuestion.answerIsPlausible(text, true)){
                            response.put("text", answerNotFittingQuestion);
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
                    currAnswer.setMessageTs(messageTs);
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
        }
        else{
            System.out.println("button click, but lastquestiontype not button question detected, returning no content...");
            return Response.noContent().build();
        }

        return null;

    }

    public Response newTextAnswer(Answer newAnswer, Question lastQuestion, String message, String messageTs, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage, boolean slack){
        JSONObject response = new JSONObject();

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
        if(lastQuestion.isBlocksQuestion() && !slack){
            System.out.println("blocks question and rocketchat recognized");

            if(!lastQuestion.answerIsPlausible(message, slack)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible(slack));
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
                newAnswer.setMessageTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            // it is an array question in rocket chat
            if(lastQuestion.getType().equals(Question.qType.ARRAY.toString())){
                System.out.println("array recognized");

                Integer index = 1;
                if(!this.getGivenAnswersAl().isEmpty() && this.currentSurvey.getQuestionByQid(this.lastquestion).getSubquestionAl().size() > 1){


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
                newAnswer.setMessageTs(messageTs);
                this.currentSubquestionAnswers.add(newAnswer);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
                SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);

            }

            if(lastQuestion.getType().equals(Question.qType.FIVESCALE.toString())){
                System.out.println("5 scale recognized");

                // answer is in valid form, so save to db
                newAnswer.setText(message);
                newAnswer.setSkipped(false);
                newAnswer.setFinalized(true);
                newAnswer.setQid(this.lastquestion);
                newAnswer.setMessageTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
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
                newAnswer.setMessageTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
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
                newAnswer.setMessageTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
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
                newAnswer.setMessageTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
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
                newAnswer.setMessageTs(messageTs);
                newAnswer.setComment(comment);
                newAnswer.setCommentTs(messageTs);
                this.givenAnswersAl.add(newAnswer);
                System.out.println("saving new answer to database");
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
                        newAnswer.setMessageTs(messageTs);
                        this.givenAnswersAl.add(newAnswer);
                        System.out.println("saving new answer to database");
                        SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                    }

                }
                System.out.println("all non chosen: " + nonchosen.toString());
                for(String qs : nonchosen){
                    Question q = this.currentSurvey.getQuestionByQid(qs);
                    Answer currAnswer = new Answer();
                    // Subquestions also have the same group id as the main question
                    currAnswer.setGid(q.getGid());
                    currAnswer.setPid(this.pid);
                    currAnswer.setSid(q.getSid());
                    currAnswer.setSkipped(false);
                    currAnswer.setDtanswered(LocalDateTime.now().toString());
                    currAnswer.setQid(q.getQid());
                    currAnswer.setMessageTs(messageTs);
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
                        newAnswer.setMessageTs(messageTs);
                        newAnswer.setComment(comments.get(0));
                        newAnswer.setCommentTs(messageTs);
                        this.givenAnswersAl.add(newAnswer);
                        System.out.println("saving new answer to database");
                        SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
                        comments.remove(0);
                    }
                }

                System.out.println("all nonchosen: " + nonchosen);
                for(String qs : nonchosen){
                    Question q = this.currentSurvey.getQuestionByQid(qs);
                    Answer currAnswer = new Answer();
                    // Subquestions also have the same group id as the main question
                    currAnswer.setGid(q.getGid());
                    currAnswer.setPid(this.pid);
                    currAnswer.setSid(q.getSid());
                    currAnswer.setSkipped(false);
                    currAnswer.setDtanswered(LocalDateTime.now().toString());
                    currAnswer.setQid(q.getQid());
                    currAnswer.setMessageTs(messageTs);
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
                    response.put("text", "Please add a comment to your chosen option: \"" + option + "\"");
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
                response.put("text", "Please select an answer first, then resend your comment.");
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }
            if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString()) && this.currentSubquestionAnswers.isEmpty()){
                // single choice comment requires selcted answer before comment
                response.put("text", "Please select options first, then you will be asked to write your comments");
                Context.get().monitorEvent(MonitoringEvent.RESPONSE_SENDING.toString());
                return Response.ok().entity(response).build();
            }

            if(!lastQuestion.answerIsPlausible(message, slack)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible(slack));
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
            this.givenAnswersAl.add(newAnswer);

            System.out.println("saving new answer to database at the end of function");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
        }

        return null;
    }

    public Response checkIfSurveyDone(String surveyDoneString){
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

    public void editSlackMessage(String token, String messageTs, String messageText){
        try{
            System.out.println("now editing the message...");
            // slack api call to get email for user id
            String urlParameters = "token=" + token + "&channel=" + channel + "&ts=" + messageTs + "&blocks=" + messageText;
            System.out.println(urlParameters);
            byte[] postData = urlParameters.getBytes( StandardCharsets.UTF_8 );
            int postDataLength = postData.length;
            String request = "https://slack.com/api/chat.update";
            URL url = new URL( request );
            HttpURLConnection conn= (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setUseCaches(false);
            try(DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
            }
            InputStream stream = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"), 8);
            String result = reader.readLine();
            System.out.println(result);

        } catch(Exception e){
            System.out.println("editing message did not work");
            e.printStackTrace();
        }
    }


    @Override
    public String toString() {
        return String.format(this.email);
    }
}
