package i5.las2peer.services.SurveyHandler;

import java.util.ArrayList;
import java.util.HashMap;

public class Admin {

    // Database model identifier
    private String aid;
    private String language;
    private String currAdministrating;
    // end Database model identifier

    public ArrayList<Survey> surveys = new ArrayList<>();

    public Admin(String aid){
        this.setAid(aid);
    }

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCurrAdministrating() {
        return currAdministrating;
    }

    public void setCurrAdministrating(String currAdministrating) {
        this.currAdministrating = currAdministrating;
    }

    public ArrayList<Survey> getSurveys() {
        return surveys;
    }

    public void setSurveys(ArrayList<Survey> surveys) {
        this.surveys = surveys;
    }
}
