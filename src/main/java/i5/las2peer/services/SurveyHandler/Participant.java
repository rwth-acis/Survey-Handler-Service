package i5.las2peer.services.SurveyHandler;

import java.util.ArrayList;
import java.util.HashMap;

public class Participant {

    private String email;
    private boolean participantContacted;
    private boolean completedSurvey;

    private ArrayList<String> unaskedQuestions;
    private ArrayList<String> skippedQuestions;
    private HashMap<String, String> answers;
    private String lastQuestion;

    public Participant(String email, boolean participantContacted, boolean completedSurvey, ArrayList<String> unaskedQuestions, ArrayList<String> skippedQuestions, HashMap<String, String> answers){
        this.email = email;
        this.participantContacted = participantContacted;
        this.unaskedQuestions = unaskedQuestions;
        this.skippedQuestions = skippedQuestions;
        this.answers = answers;
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

    public String getLastQuestion(){
        return this.lastQuestion;
    }

    public void setParticipantContacted(){ this.participantContacted = true; }

    public void setCompletedSurvey(){ this.completedSurvey = true; }

    public void addEmail(String email){
        this.email = email;
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
}
