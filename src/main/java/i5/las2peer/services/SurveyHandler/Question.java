package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Question{

    private String qid;
    private String parent_qid;
    private String gid;
    private String question_order;
    private String question_text;
    private String sid;
    private String help;
    private String type;
    private String relevance;
    private JSONObject answeroptions = new JSONObject();
    // Integer order starts at 1
    private HashMap<Integer, String> answerOptionsStringAl = new HashMap<>();
    private ArrayList<Question> subquestionAl = new ArrayList<>();
    private boolean isSubquestion = false;

    public Question(JSONObject q) throws Exception {

        JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
        this.qid = q.getAsString("qid");
        this.parent_qid = q.getAsString("parent_qid");
        this.gid = q.getAsString("gid");
        this.question_order = q.getAsString("question_order");
        this.question_text = q.getAsString("question");
        this.sid = q.getAsString("sid");
        this.help = q.getAsString("help");
        this.type = q.getAsString("type");
        this.relevance = q.getAsString("relevance");
        if(!q.getAsString("answeroptions").contains("options")){
            this.answeroptions = (JSONObject) q.get("answeroptions");
            for(String s : this.answeroptions.keySet()){
                JSONObject temp = (JSONObject) this.answeroptions.get(s);
                this.answerOptionsStringAl.put(Integer.parseInt(temp.getAsString("order")), temp.getAsString("answer"));
            }
        }
        if (Integer.parseInt(this.parent_qid )> 0){
            this.isSubquestion = true;
        }

    }

    public boolean isSubquestion(){
        return this.isSubquestion;
    }

    public void addSubquestion(Question subQ){
        this.subquestionAl.add(subQ);
    }

    public ArrayList<Question> getSubquestionAl(){
        return this.subquestionAl;
    }

    public String getQid() {
        return this.qid;
    }

    public String getGid() {
        return this.gid;
    }

    public String getType() {
        return this.type;
    }

    public String getQuestionOrder() {
        return this.question_order;
    }

    public String getParentQid() {
        return this.parent_qid;
    }

    public String getHelp() {
        return this.help;
    }

    public boolean hasHelp() {
        if(this.help != null){
            return true;
        }
        return false;
    }

    public String encodeJsonBodyAsString(){
        String resString = "";
        resString += this.question_text;
        int index = 1;

        System.out.println(this.type);

        // Switch case to check if question type is mask question (their answer options are not saved in question)
        // The question code is from LimeSurvey
        switch(this.type){
            case "D":
                System.out.println("Date/Time");
                resString += "Please enter a date in the format dd.mm.jjjj.";
                break;
            case "|":
                System.out.println("File upload");
                resString += " Please send a file.";
                break;
            case "G":
                System.out.println("Gender");
                resString += " Please enter your gender.";
                break;
            case "N":
                System.out.println("Numerical input");
                resString += " Please respond with a number.";
                break;
            case "X":
                System.out.println("Text display");
                break;
            case "Y":
                System.out.println("Yes/No");
                resString += " Please answer with Yes, No oder No answer.";
                break;
            case "5":
                System.out.println("5 point choice");
                resString += " Please rate on a scale of 1 to 5.";
                break;
        }

        // Check if multiple choice question
        if (this.subquestionAl.size() > 0) {
            resString += " Please choose all that apply from the following options by the corresponding number: \n";
            for (Question subq : this.subquestionAl) {
                resString += "\n" + index + ": ";
                index ++;
                resString += subq.encodeJsonBodyAsString();
            }
        }

        //check if single choice question
        if(this.answerOptionsStringAl.size() > 0) {
            if(!(this.type == "R" && this.type == "K")){
                resString += " Please choose one from the following options by the corresponding number: \n";
                for(int i = 1; i < answerOptionsStringAl.size() + 1; i++){
                    resString += "\n" + index + ": " + answerOptionsStringAl.get(i);
                    index++;
                }
            }
        }
        if(!this.isSubquestion){
            if(this.help.length() > 0){
                resString += "\n This is the help: " + this.help + "";
            }
        }
        return resString;
    }

    private Question getSubquestionByIndex(String index){
        System.out.println(index);
        return this.subquestionAl.get(Integer.parseInt(index) -1);
    }

    public HashMap<String, String> createAnswerHashMap(String answer){
        System.out.println("inside createAnswerHashMap");
        HashMap<String, String> returnValue = new HashMap<>();
        if(this.subquestionAl.size() > 0){
            //has sub questions/is multiple choice
            String[] parsedIndices = answer.replaceAll("\\s","").split(",");
            System.out.println(answer);
            System.out.println(parsedIndices);
            for(String s : parsedIndices){
                System.out.println(s);
                Question subquestion = this.getSubquestionByIndex(s);
                int subquestionIndex = Integer.parseInt(s);
                System.out.println(subquestionIndex);
                // Lime survey accepts only 5 letter answers
                returnValue.put(this.createAnswerKey(true, subquestionIndex), "yes");
            }
        } else{
            System.out.println("inside createAnswerHashMap else");
            returnValue.put(this.createAnswerKey(false, -1), answer);
        }
        return returnValue;
    }


    private String createAnswerKey (boolean isSubquestion, int subquestionIndex){
        String separator = "X";
        String returnValue = this.sid + separator + this.gid + separator + this.qid;
        if(isSubquestion){
            returnValue += "SQ" + String.format("%03d", subquestionIndex);
        }
        System.out.println("create answer function return value: " + returnValue);
        return returnValue;
    }
}
