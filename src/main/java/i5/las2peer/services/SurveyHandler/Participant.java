package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONObject;

import javax.ws.rs.core.Response;
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
            return Response.ok().entity(response).build();
        }

        // Participant wants to skip a question
        if(intent.equals("skip")){
            this.skippedQuestions.add(this.lastQuestion);
        } else{
            this.answers.put(this.lastQuestion, message);
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
            this.unaskedQuestions.remove(0);
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

    public boolean getCompletedSurvey(){ return this.completedSurvey;}

    public ArrayList<String> getUnaskedQuestions() {
        return this.unaskedQuestions;
    }

    public ArrayList<String> getSkippedQuestions() {
        return this.skippedQuestions;
    }

    public String getAnswers() {
        return this.answers.toString();
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

    public void setParticipantContacted(){ this.participantContacted = true; }

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
        this.answers.put(questionID, answer);
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
