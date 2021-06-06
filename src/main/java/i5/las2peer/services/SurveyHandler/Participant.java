package i5.las2peer.services.SurveyHandler;

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
            first = " To start the second survey you need to answer all questions from the first one.";
        }

        String changeAnswerExplanation = "\nTo change your given answer edit your message, by clicking on the 3 points next to your text message and then choosing \"Edit Message\", or click on a button again. For multiple choice questions it is not neccessary to submit the answers again.";
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


        // check if it is the first contacting
        boolean participantContacted = this.participantcontacted;
        if (!participantContacted){
            return participantNewlyContacted(welcomeString, skipExplanation, changeAnswerExplanation, resultsGetSaved, first);
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
            response.put("text", completedSurvey);
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

    private Response AskNextQuestion(){
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
        JSONObject response = new JSONObject();
        // Check if logic applies to next question

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
            }

            this.unaskedQuestions.remove(0);
            this.lastquestion = nextId;
            System.out.println("setting last question to new question id");
            // update last question in database
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(newQuestionGroup, false, "", this);

            // If it is starting with "[" it is a block question
            if(Character.toString(messageText.charAt(0)).equals("[")){
                response.put("blocks", messageText);
            } // If it is a normal text message
            else{
                response.put("text", messageText);
            }
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
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(this);
            System.out.println("messageText " + messageText);
            String skipText = "This question was skipped by you, you can answer now or skip again: \n";

            if(this.currentSurvey.getQuestionByQid(nextId).isBlocksQuestion()){
                System.out.println("inside is blocks question, adding blocks...");
                response.put("text", skipText);
                response.put("blocks", messageText);
            } else{
                response.put("text", skipText + messageText);
            }

            return Response.ok().entity(response).build();
        }

        response.put("text", "Something went wrong on bot-side :(");
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
            System.out.println("curranswer ts: " + a.getMessageTs());
            if(a.getMessageTs() != null){
                if(a.getMessageTs().equals(messageTs)){
                    allAnswers.add(a);
                }
            }
            if(a.getCommentTs() != null){
                if(a.getCommentTs().equals(messageTs)){
                    allAnswers.add(a);
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
        Answer answer = getAnswerByTS(messageTs);
        if(answer != null) {
            if (answer.isFinalized()) {
                System.out.println("participant changed button answer...");
                return true;
            }
        }
        return false;
    }

    public Response updateAnswer(String intent, String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer, String token){
        // check if it is a skipped message, if yes ignore
        Answer a = getAnswerByTS(messageTs);
        if(a != null){
            if(a.isSkipped()){
                System.out.println("skipped message edited");
                JSONObject response = new JSONObject();
                response.put("text", "Please do not edit skipped answers, you will be asked the question again at the end of the survey.");
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
                return Response.ok().entity(response).build();
            }
        }


        if(participantChangedTextAnswer(currMessage, prevMessage)){
            return updateTextAnswer(intent, message, messageTs, currMessage, prevMessage, changedAnswer);
        }
        else if(participantChangedButtonAnswer(messageTs)){
            return updateButtonAnswer(intent, message, messageTs, changedAnswer, token);
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
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.LISTRADIO.toString())){
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
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.DICHOTOMOUS.toString()) ||
                this.currentSurvey.getQuestionByQid(answer.getQid()).getType().equals(Question.qType.SCALE.toString())){

            answer.setText(message);

            System.out.println("updating answer in database...");
            System.out.println("answertext: " + answer.getText());
            SurveyHandlerServiceQueries.updateAnswerInDB(answer, currentSurvey.getDatabase());

            // color the chosen button
            String messageText = currentSurvey.getQuestionByQid(answer.getQid()).encodeJsonBodyAsString(false, true, message, this);
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
            String messageText = currentSurvey.getQuestionByQid(answer.getQid()).encodeJsonBodyAsString(false, true, message, this);
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

        String changed = "Your answer to the question " + Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL()).getText() + " has been changed successfully.";
        response.put("text", changed);
        return Response.ok().entity(response).build();

    }

    public Response updateTextAnswer(String intent, String message, String messageTs, JSONObject currMessage, JSONObject prevMessage, String changedAnswer){
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
            return Response.ok().entity(response).build();
        }

        Question answerEdited = Question.getQuestionById(answer.getQid(), currentSurvey.getQuestionAL());
        if(!answerEdited.answerIsPlausible(message)){
            response.put("text", answerEdited.reasonAnswerNotPlausible(message));
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
        String changed = "Your answer to the question " + answerEdited.getText() + " has been changed successfully.";
        response.put("text", changed);
        return Response.ok().entity(response).build();
    }

    public Response calcNextResponse(String intent, String message, String buttonIntent, String messageTs, JSONObject currMessage, JSONObject prevMessage, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage, String token){
        JSONObject response = new JSONObject();
        Response res = null;

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
                    res = newTextAnswer(newAnswer, lastQuestion, message, messageTs, surveyDoneString, answerNotFittingQuestion, submittButtonPressedMessage);
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
        return this.AskNextQuestion();
    }

    public Response newButtonAnswer(Answer newAnswer, Question lastQuestion, String token, String message, String messageTs, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage){
        JSONObject response = new JSONObject();
        // message is a list of selected options in json format or a simple text message

        if (lastQuestion.getType().equals(Question.qType.LISTDROPDOWN.toString()) || lastQuestion.getType().equals(Question.qType.LISTRADIO.toString())){
            if(!lastQuestion.answerIsPlausible(message)){
                response.put("text", answerNotFittingQuestion);
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
            if(!lastQuestion.answerIsPlausible(message)){
                response.put("text", answerNotFittingQuestion);
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
            String messageText = lastQuestion.encodeJsonBodyAsString(false, true, message, this);
            editSlackMessage(token, messageTs, messageText);

        } else if(lastQuestion.getType().equals(Question.qType.FIVESCALE.toString()) || lastQuestion.getType().equals(Question.qType.DICHOTOMOUS.toString()) ||
                lastQuestion.getType().equals(Question.qType.SCALE.toString())) {
            if (!lastQuestion.answerIsPlausible(message)) {
                response.put("text", answerNotFittingQuestion);
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
            String messageText = lastQuestion.encodeJsonBodyAsString(false, true, message, this);
            editSlackMessage(token, messageTs, messageText);

        }else if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
            if (!lastQuestion.answerIsPlausible(message)) {
                response.put("text", answerNotFittingQuestion);
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

                        if(!lastQuestion.answerIsPlausible(text)){
                            response.put("text", answerNotFittingQuestion);
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

    public Response newTextAnswer(Answer newAnswer, Question lastQuestion, String message, String messageTs, String surveyDoneString, String answerNotFittingQuestion, String submittButtonPressedMessage){
        JSONObject response = new JSONObject();

        System.out.println("has no currentsubquestionAnswers: " + this.currentSubquestionAnswers.isEmpty());
        System.out.println("type: " + lastQuestion.getType());
        System.out.println("multitype: " + Question.qType.MULTIPLECHOICEWITHCOMMENT);

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


        // Check if a button message needs to be saved as well
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
                return Response.ok().entity(response).build();
            }
            if(lastQuestion.getType().equals(Question.qType.MULTIPLECHOICEWITHCOMMENT.toString()) && this.currentSubquestionAnswers.isEmpty()){
                // single choice comment requires selcted answer before comment
                response.put("text", "Please select options first, then you will be asked to write your comments");
                return Response.ok().entity(response).build();
            }

            if(!lastQuestion.answerIsPlausible(message)){
                response.put("text", lastQuestion.reasonAnswerNotPlausible(lastQuestion.getType()));
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
