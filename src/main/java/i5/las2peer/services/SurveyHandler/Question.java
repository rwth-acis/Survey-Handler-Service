package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.util.ArrayList;
import java.util.HashMap;

public class Question{

    // Database model identifier
    private String qid;
    private String gid;
    private String parentqid;
    private String text;
    private String type;
    private String qorder;
    private String sid;
    private String help;
    private String relevance;
    private String code;
    // end Database model identifier

    public static enum qType{
        // TODO use enum for all types
        SINGLECHOICECOMMENT("O");

        private final String name;
        private qType(String name){
            this.name= name;
        }

        @Override
        public String toString(){
            return this.name;
        }
    }
    // Has table answeroptions in db, Integer order starts at 1
    private HashMap<Integer, String> answerOptionsStringAl = new HashMap<>();

    private ArrayList<Question> subquestionAl = new ArrayList<>();
    private boolean isSubquestion = false;

    public Question()  {

    }

    public void initData(JSONObject q) throws Exception{
        JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
        this.qid = q.getAsString("qid");
        this.setParentqid(q.getAsString("parent_qid"));
        this.gid = q.getAsString("gid");
        this.qorder = q.getAsString("question_order");
        this.text = q.getAsString("question");
        this.sid = q.getAsString("sid");
        this.help = q.getAsString("help");
        this.type = q.getAsString("type");
        this.relevance = q.getAsString("relevance");
        this.code = q.getAsString("title");
        if(!q.getAsString("answeroptions").contains("options")){
            JSONObject answeroptions = (JSONObject) q.get("answeroptions");
            for(String s : answeroptions.keySet()){
                JSONObject temp = (JSONObject) answeroptions.get(s);
                this.answerOptionsStringAl.put(Integer.parseInt(temp.getAsString("order")), temp.getAsString("answer"));
            }
        }
        if (Integer.parseInt(this.parentqid)> 0){
            this.isSubquestion = true;
        }
    }

    public void setSubquestion(boolean subquestion) {
        isSubquestion = subquestion;
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

    public void setAnswerOptionsStringAl(HashMap<Integer, String> answerOptionsStringAl) {
        this.answerOptionsStringAl = answerOptionsStringAl;
    }

    public void setSubquestionAl(ArrayList<Question> subquestionAl) {
        this.subquestionAl = subquestionAl;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getRelevance() {
        return relevance;
    }

    public void setRelevance(String relevance) {
        this.relevance = relevance;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    public void setGid(String gid) {
        this.gid = gid;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getParentqid() {
        return parentqid;
    }

    public void setParentqid(String parentqid) {
        this.parentqid = parentqid;
        if (Integer.parseInt(this.parentqid) > 0){
            this.isSubquestion = true;
        }else {
            this.isSubquestion = false;
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getQorder() {
        return qorder;
    }

    public void setQorder(String qorder) {
        this.qorder = qorder;
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

    public String getParentQid() {
        return this.parentqid;
    }

    public String getHelp() {
        return this.help;
    }

    public String getAnswerOptions(Integer index) {
        return this.answerOptionsStringAl.get(index);
    }

    public boolean hasHelp() {
        if(this.help != null){
            return true;
        }
        return false;
    }

    public String encodeJsonBodyAsString(Participant participant){
        return encodeJsonBodyAsString(false, participant);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, Participant participant){
        String resString = "";
        String subString = "";
        int index = 1;

        String questionText = this.text;
        // +1 because the question that is about to be sent is already removed from the list
        String newQGroupText = "You completed a question group. There are " + participant.getUnaskedQuestions().size() + 1 + " questions left.\n";
        if(newQuestionGroup){
            questionText = newQGroupText + questionText;
        }


        if(this.isSubquestion){
            subString += "{\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\": " + questionText + ",\n" +
                    "\"emoji\": true\n" +
                    "},\n" +
                    "\"value\": " + this.qid + "\n" +
                    "},";
            return subString;
        }

        resString += questionText;
        System.out.println(this.type);

        // Switch case to check if question type is mask question (their answer options are not saved in question)
        // The question code is from LimeSurvey
        switch(this.type){
            case "D":
                System.out.println("Date/Time");
                resString += " Please enter a date in the format dd.mm.jjjj.";
                break;
            case "|":
                System.out.println("File upload");
                resString += " Please send a file.";
                break;
            case "G":
                System.out.println("Gender");
                resString = "[{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                        "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Female\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t},\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Male\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t},\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No Answer\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t}\n" +
                        "\t\t\t]\n" +
                        "\t\t}]";
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
                resString = "[{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                        "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Yes\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t},\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t},\n" +
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No Answer\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" +
                        "\t\t\t\t}\n" +
                        "\t\t\t]\n" +
                        "\t\t}]";
                break;
            case "5":
                System.out.println("5 point choice");
                resString += " Please rate on a scale of 1 to 5.";
                break;
        }

        // Check if multiple choice question
        if (this.subquestionAl.size() > 0) {
            resString = "[\n" +
                    "{\n" +
                    "\"type\": \"section\",\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\": \"" + questionText + "\",\n" +
                    "\"emoji\": true\n" +
                    "}\n" +
                    "},\n" +
                    "{\n" +
                    "\"type\": \"actions\",\n" +
                    "\"elements\": [\n" +
                    "{\n" +
                    "\"type\": \"checkboxes\",\n" +
                    "\"options\": [";
            for (Question subq : this.subquestionAl) {
                resString += subq.encodeJsonBodyAsString(participant);
            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
            resString += "],\n" +
                    "\"action_id\": \"" + this.qid + "\"\n" +
                    "}\n" +
                    "]\n" +
                    "},\n" +
                    "{\n" +
                    "\"type\": \"actions\",\n" +
                    "\"elements\": [\n" +
                    "{\n" +
                    "\"type\": \"button\",\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\": \"Submit\",\n" +
                    "\"emoji\": true\n" +
                    "},\n" +
                    "\"value\": \"submit\",\n" +
                    "\"action_id\": \"" + this.qid + "\"\n" +
                    "}\n" +
                    "]\n" +
                    "}" +
                    "]";
        }


        //check if single choice question
        if((this.answerOptionsStringAl.size() > 0 && this.type.equals("!")) || (this.answerOptionsStringAl.size() > 0 && this.type.equals("O"))) {
            System.out.println("inside answeroptions with type ! or o");
            resString = "[\n" +
                    "{\n" +
                    "\t\t\t\"type\": \"section\",\n" +
                    "\t\t\t\"text\": {\n" +
                    "\t\t\t\t\"type\": \"plain_text\",\n" +
                    "\t\t\t\t\"text\": \"" + questionText + "\",\n" +
                    "\t\t\t\t\"emoji\": true\n" +
                    "\t\t\t}\n" +
                    "\t\t}," +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"actions\",\n" +
                    "\t\t\t\"elements\": [\n" +
                    "\t\t\t\t{\n" +
                    "\t\t\t\t\t\"type\": \"radio_buttons\",\n" +
                    "\t\t\t\t\t\"options\": [";
            for(int i = 1; i < answerOptionsStringAl.size() + 1; i++){
                String currAnswerOption = "{\n" +
                        "\t\t\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\t\t\"text\": \"" + answerOptionsStringAl.get(i) + "\",\n" +
                        "\t\t\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t\t\t},\n" +
                        "\t\t\t\t\t\t\t\"value\": \"" + index + "\"\n" +
                        "\t\t\t\t\t\t},";

                resString += currAnswerOption;
                index++;
            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
            resString += "\t\t\t\t\t]" +
                    "\t\t\t\t}\n" +
                    "\t\t\t]\n" +
                    "\t\t}\n" +
                    "\t]";

            System.out.println("resstring: " + resString);
        }
        if(this.answerOptionsStringAl.size() > 0 && this.type.equals("L")) {
            System.out.println("inside type L");
            resString = "[\n" +
                    "{\n" +
                    "\"type\": \"section\",\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\": " + questionText + "\n" +
                    "}\n" +
                    "},\n" +
                    "{\n" +
                    "\"type\": \"actions\",\n" +
                    "\"elements\": [\n";
            for(int i = 1; i < answerOptionsStringAl.size() + 1; i++){
                String currAnswerOption = "{\n" +
                        "\"type\": \"button\",\n" +
                        "\"text\": {\n" +
                        "\"type\": \"plain_text\",\n" +
                        "\"text\": " + answerOptionsStringAl.get(i) + "\n" +
                        "}\n" +
                        "},";

                resString += currAnswerOption;
                index++;
            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
            resString += "]\n" +
                    "}\n" +
                    "]\n";

            System.out.println("resstring: " + resString);
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

    private Question getSubquestionByText(String text){
        System.out.println(text);
        for(Question q : subquestionAl){
            if(q.getText().equals(text)){
                return q;
            }
        }
        System.out.println("did not find question for text: " + text);
        return null;
    }

    // gets answer text and creates hashmap of format (sidXgidXqidXsqid, answertext). This format can be used for limesurvey communication directly
    public HashMap<String, String> createAnswerHashMap(String answer, boolean comment){
        System.out.println("inside createAnswerHashMap");
        HashMap<String, String> returnValue = new HashMap<>();
        if(this.subquestionAl.size() > 0){
            //has sub questions/is multiple choice
            String[] parsedIndices = answer.split(",");
            System.out.println(answer);
            System.out.println(parsedIndices);
            for(String s : parsedIndices){
                System.out.println("answerString: " + s);
                Question subquestion = this.getSubquestionByText(s);
                String subQCode = subquestion.getCode();
                // Lime survey accepts only 5 letter answers
                returnValue.put(this.createAnswerKey(true, subQCode, comment), "yes");
            }
        } else{
            System.out.println("inside createAnswerHashMap else");
            returnValue.put(this.createAnswerKey(false, "", comment), answer);
        }
        return returnValue;
    }


    private String createAnswerKey (boolean isSubquestion, String code, boolean comment){
        String separator = "X";
        String returnValue = this.sid + separator + this.gid + separator + this.qid;
        if(isSubquestion){
            returnValue += code;
        }
        if(comment){
            returnValue += "comment";
        }
        System.out.println("create answer function return value: " + returnValue);
        return returnValue;
    }

    public boolean checkIfQidInSubQs(String qid){
        for (Question subQ : this.getSubquestionAl()){
            if (subQ.getQid().equals(qid)){
                return true;
            }
        }
        return false;
    }
    // Utility functions
    public static Question getQuestionById(String qid, ArrayList<Question> qAL){
        for (Question q: qAL){
            if (q.getQid().equals(qid)){
                return q;
            }
        }
        return null;
    }

    public HashMap<Integer, String> getAnswerOptionsStringAl() {
        return answerOptionsStringAl;
    }
}
