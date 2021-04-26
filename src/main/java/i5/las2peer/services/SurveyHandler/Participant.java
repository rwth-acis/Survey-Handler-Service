package i5.las2peer.services.SurveyHandler;

import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONObject;
import org.web3j.abi.datatypes.Bool;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

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

    String allChosen = "";

    private ArrayList<Answer> givenAnswersAl = new ArrayList<>();
    private ArrayList<String> unaskedQuestions = new ArrayList<>();
    private ArrayList<String> skippedQuestions = new ArrayList<>();
    // first string: qid; second string: answer text
    // TODO refactor everything to use arraylist<Answer> instead
    private HashMap<String, String> answers = new HashMap<>();

    private Survey currentSurvey = null;



    public Participant(String email){
        this.addEmail(email);
        this.setPid(email);
    }

    // Based on the intent, decide what is sent back to the participant
    public Response calculateNextAction(String intent, String message, String buttonIntent){
        int questionsInSUrvey = this.currentSurvey.getSortedQuestions().size();
        String welcomeString = "Would you like to start the survey \"" + currentSurvey.getTitle() + "\"? There are " + questionsInSUrvey + " questions in this survey.";
        String explanation = " To skip a question just send \"skip\", you will be able to answer them later if you want.";
        String completedSurvey = "You already completed the survey.";
        String surveyDoneString = "Thank you for completing this survey.";
        JSONObject response = new JSONObject();
        Participant currParticipant = this;

        System.out.println("calculating next action...");
        // Participant has not started the survey yet
        if(!(this.participantcontacted)){
            System.out.println("participant newly contacted");
            this.participantcontacted = true;
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            response.put("text", welcomeString + explanation);
            return Response.ok().entity(response).build();
        }

        // Participant has completed the survey
        if(this.completedsurvey) {
            //TODO option to change answers
            response.put("text", completedSurvey);
            return Response.ok().entity(response).build();
        }

        System.out.println("last question is: " + this.lastquestion);

        if(this.lastquestion != null){
            System.out.println("last question is not null recognized");
            Answer newAnswer = new Answer();
            Question lastQuestion = this.currentSurvey.getQuestionByQid(this.lastquestion);
            newAnswer.setGid(lastQuestion.getGid());
            newAnswer.setPid(this.getPid());
            newAnswer.setSid(this.getSid());
            newAnswer.setSkipped(true);
            newAnswer.setQid(this.lastquestion);

            System.out.println("intent is: " + intent);
            System.out.println("buttonintent is: " + buttonIntent);
            System.out.println("all chosen: " + this.allChosen);

            // Participant wants to skip a question
            if(intent.equals("skip")){
                this.skippedQuestions.add(this.lastquestion);
            } else if(intent.equals(buttonIntent)){
                // check if last question was mulitple choice, to wait for all answers
                if(!(lastQuestion.getSubquestionAl().isEmpty()) && message.equals("Submit")){
                    // submit button of multiple choice question sent
                    // add all chosen options to answers
                    System.out.println("all chosen: " + this.allChosen);
                    this.addAnswer(this.lastquestion, this.allChosen, false);
                    newAnswer.setSkipped(false);
                    newAnswer.setText(this.allChosen);

                } else if(!(lastQuestion.getSubquestionAl().isEmpty())){
                    // it is an answer to multiple choice question, but submit button was not pressed yet
                    // adding answer options to allChosen, to save for later
                    // message contains all currently checked answers
                    this.allChosen = message;
                    System.out.println("not an answer, so retuning null to wait for submit button. Current answers: " + this.allChosen);
                    return null;
                } else if(lastQuestion.getType().equals(Question.qType.SINGLECHOICECOMMENT.toString())){
                    System.out.println("inside type single choice comments. Actual type: " + lastQuestion.getType());
                    // last question was not multiple choice, but single choice and requires a comment
                    this.allChosen = message;
                    return null;
                } else{
                    // Check if mask question type Gender or Yes/No question
                    System.out.println("lastQuestionType: " + lastQuestion.getType() + " and message: " + message);
                    if(lastQuestion.getType().equals("G") || lastQuestion.getType().equals("Y")){
                        this.addAnswer(this.lastquestion, message.substring(0,1), false);
                        newAnswer.setSkipped(false);
                        newAnswer.setText(message.substring(0,1));
                    }
                    // adding normal button answer
                    this.addAnswer(this.lastquestion, message, false);
                    newAnswer.setSkipped(false);
                    newAnswer.setText(message);
                }
            } else{
                // Check if a button message needs to be saved as well
                if(this.allChosen.length() > 0){
                    this.addAnswer(this.lastquestion, this.allChosen, false);
                    newAnswer.setComment(message);
                    newAnswer.setText(this.allChosen);
                    newAnswer.setSkipped(false);
                    this.addAnswer(this.lastquestion, message, true);
                } else{
                    // adding normal answer
                    this.addAnswer(this.lastquestion, message, false);
                    newAnswer.setSkipped(false);
                    newAnswer.setText(message);
                }
            }

            System.out.println("saving new answer to database");
            SurveyHandlerServiceQueries.addAnswerToDB(newAnswer, currentSurvey.database);
        } else{
            // This is the first response after question to start the survey was sent
            // do not add confirmation to start survey to answers
            // TODO check for confirmation to start survey
        }

        // Calculate next question to ask

        // Check if survey is completed
        if (this.unaskedQuestions.size() == 0 && this.skippedQuestions.size() == 0){
            // No questions remaining, survey done.
            this.completedsurvey = true;
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            response.put("text",surveyDoneString); //+ currParticipant.getEmail() + currParticipant.getUnaskedQuestions() + currParticipant.getSkippedQuestions()
            return Response.ok().entity(response).build();
        }

        // Check what questions are left
        return this.AskNextQuestion();

    }

    private Response AskNextQuestion(){
        boolean newQuestionGroup = false;
        JSONObject response = new JSONObject();
        // Check if logic applies to next question

        // Normal questions available
        if (this.unaskedQuestions.size() >0){
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
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(newQuestionGroup, this);

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
        if (this.skippedQuestions.size() >0){
            String nextId = this.skippedQuestions.get(0);
            this.skippedQuestions.remove(0);
            this.lastquestion = nextId;
            SurveyHandlerServiceQueries.updateParticipantInDB(this, this.currentSurvey.database);
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString(this);
            messageText = "This question was skipped by you, you can answer now or skip again: \n"+ messageText;
            response.put("text", messageText);
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

    public HashMap<String, String> getAnswers() {
        return this.answers;
    }

    public String getLasttimeactive(){
        return this.lasttimeactive;
    }

    public String getAnswersString(){
        String returnValue = "";
        for(String s : this.answers.keySet()){
            returnValue += "\"" + s + "\":\"" + this.answers.get(s) + "\",";
        }
        // Only delete comma if there are answers
        if(returnValue.length() > 1){
            return returnValue.substring(0, returnValue.length() - 1);
        }
        return returnValue;
    }

    public String getAnswer(String questionID) {
        return this.answers.get(questionID);
    }

    public boolean hasAnswer(String questionID) {
        return this.answers.containsKey(questionID);
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

    public void addAnswer(String questionID, String answer, boolean comment){
        //HashMap<String, String> subQs = new HashMap<>();
        Question currQuestion = this.currentSurvey.getQuestionByQid(questionID);
        this.answers.putAll(currQuestion.createAnswerHashMap(answer, comment));
    }

    public void removeAnswer(String questionID){
        this.answers.remove(questionID);
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
        } else if(a.getComment().length() > 0){
            this.addAnswer(a);
            this.addAnswer(a.getQid(), a.getText(), true);
        } else{
            this.addAnswer(a);
            this.addAnswer(a.getQid(),a.getText(), false);
        }
    }



    @Override
    public String toString() {
        return String.format(this.email);
    }
}
