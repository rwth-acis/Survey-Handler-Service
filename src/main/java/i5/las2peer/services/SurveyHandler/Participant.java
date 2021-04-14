package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONObject;

import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

public class Participant {

    private String email;
    private boolean participantContacted;
    private boolean completedSurvey;

    private ArrayList<String> unaskedQuestions;
    private ArrayList<String> skippedQuestions = new ArrayList<>();
    private HashMap<String, String> answers = new HashMap<>();
    private String lastQuestion;
    private Survey currentSurvey = null;
    private LocalDateTime lastTimeActive;
    private String surveyResponseId;

    public Participant(String email){
        this.addEmail(email);
    }

    // Based on the intent, decide what is sent back to the participant
    public Response calculateNextAction(String intent, String message){
        String welcomeString = "Would you like to start the survey \"" + currentSurvey.getTitle() + "\"?";

        JSONObject response = new JSONObject();
        Participant currParticipant = this;

        // Participant has not started the survey yet
        if(!(this.participantContacted)){
            this.participantContacted = true;
            response.put("text", welcomeString);
            return Response.ok().entity(response).build();
        }

        // Participant has completed the survey
        if(this.completedSurvey) {
            //TODO option to change answers
            response.put("text", "You already completed the survey.");
            return Response.ok().entity(response).build();
        }

        // Participant wants to skip a question
        if(intent.equals("skip")){
            this.skippedQuestions.add(this.lastQuestion);
        } else{
            //TODO check for confirmation to start survey
            if(this.lastQuestion == null){
                //do not add confirmation to start survey to answers
            } else{
                this.addAnswer(this.lastQuestion, message);
            }
        }

        // Calculate next question to ask

        // Check if survey is completed
        if (this.unaskedQuestions.size() == 0 && this.skippedQuestions.size() == 0){
            // No questions remaining, survey done.
            String surveyDoneString = "Thank you for completing this survey.";
            this.completedSurvey = true;
            response.put("text",surveyDoneString); //+ currParticipant.getEmail() + currParticipant.getUnaskedQuestions() + currParticipant.getSkippedQuestions()
            return Response.ok().entity(response).build();
        }

        // Check what questions are left
        return this.AskNextQuestion();

    }

    private Response AskNextQuestion(){

        JSONObject response = new JSONObject();
        // Check if logic applies to next question

        // Normal questions available
        if (this.unaskedQuestions.size() >0){
            String nextId = this.unaskedQuestions.get(0);
            this.unaskedQuestions.remove(0);
            this.lastQuestion = nextId;
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString();
            response.put("text", messageText);
            return Response.ok().entity(response).build();
        }

        // Skipped questions available
        if (this.skippedQuestions.size() >0){
            String nextId = this.skippedQuestions.get(0);
            this.skippedQuestions.remove(0);
            this.lastQuestion = nextId;
            String messageText = this.currentSurvey.getQuestionByQid(nextId).encodeJsonBodyAsString();
            messageText = "This question was skipped by you, you can answer now or skip again: \n"+ messageText;
            response.put("text", messageText);
            return Response.ok().entity(response).build();
        }

        response.put("text", "Something went wrong on bot-side :(");
        return Response.ok().entity(response).build();
    }
    public void setCurrentSurvey(Survey s){
        this.currentSurvey = s;
    }
    public void setUnaskedQuestions(ArrayList<String> orderedQuestionList){
        this.unaskedQuestions = new ArrayList<>(orderedQuestionList);
    }

    public String getEmail(){
        return this.email;
    }

    public boolean getParticipantContacted(){
        return this.participantContacted;
    }

    public String getSurveyResponseID(){
        return this.surveyResponseId;
    }

    public boolean getCompletedSurvey(){ return this.completedSurvey;}

    public ArrayList<String> getUnaskedQuestions() {
        return this.unaskedQuestions;
    }

    public ArrayList<String> getSkippedQuestions() {
        return this.skippedQuestions;
    }

    public HashMap<String, String> getAnswers() {
        return this.answers;
    }

    public LocalDateTime getLastTimeActive(){
        return this.lastTimeActive;
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

    public String getLastQuestion(){
        return this.lastQuestion;
    }

    public void setLastTimeActive(LocalDateTime localDateTime){
        this.lastTimeActive = localDateTime;
    }

    public void setParticipantContacted(){ this.participantContacted = true; }

    public void setSurveyResponseID(String responseID){
        this.surveyResponseId = responseID;
    }

    public void setCompletedSurvey(){ this.completedSurvey = true; }

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
        this.unaskedQuestions.add(0, questionID);
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

    public void addAnswer(String questionID, String answer){
        HashMap<String, String> subQs = new HashMap<>();
        Question currQuestion = this.currentSurvey.getQuestionByQid(questionID);
        this.answers.putAll(currQuestion.createAnswerHashMap(answer));
    }

    public void removeAnswer(String questionID){
        this.answers.remove(questionID);
    }

    public void addLastQuestion(String questionID){
        this.lastQuestion = questionID;
    }

    @Override
    public String toString() {
        return String.format(this.email);
    }
}
