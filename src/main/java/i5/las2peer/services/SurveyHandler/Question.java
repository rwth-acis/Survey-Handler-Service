package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.util.ArrayList;

public class Question{

    private String qid;
    private String parent_qid;
    private String gid;
    private String question_order;
    private String question_text;
    private String sid;
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

    public String getQid() {
        return this.qid;
    }

    public String getGid() {
        return this.gid;
    }

    public String getQuestionOrder() {
        return this.question_order;
    }

    public String getParentQid() {
        return this.parent_qid;
    }

    public String encodeJsonBodyAsString(){
        String resString = "";
        resString += this.question_text;
        if (this.subquestionAl.size() > 0) {
            resString += ". Please choose from the following options: \n";
            for (Question subq : this.subquestionAl) {
                resString += "- \n";
                resString += subq.encodeJsonBodyAsString();
            }
        }
        return resString;
    }
}
