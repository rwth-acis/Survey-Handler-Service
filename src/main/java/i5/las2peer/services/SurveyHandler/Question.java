package i5.las2peer.services.SurveyHandler;

import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
        SINGLECHOICECOMMENT("O"),
        MULTIPLECHOICENOCOMMENT("M"),
        MULTIPLECHOICEWITHCOMMENT("P"),
        LISTDROPDOWN("!"),
        LISTRADIO("L"),
        GENDER("G"),
        YESNO("Y"),
        DATETIME("D"),
        TEXTDISPLAY("X"),
        NUMERICALINPUT("N"),
        FILEUPLOAD("|"),
        FIVESCALE("5"),
        LONGFREETEXT("T"),
        SHORTFREETEXT("S"),
        HUGEFREETEXT("U");

        private final String name;
        private final int maxLength;
        private qType(String name){
            this.name= name;
            // TODO find correct values
            if(this.name.equals("T")){
                this.maxLength = 500;
            }
            else if(this.name.equals("U")){
                this.maxLength = 700;
            }
            else if(this.name.equals("S")){
                this.maxLength = 100;
            }
            else{
                this.maxLength = 700;
            }
        }

        public int getMaxLength(){
            return this.maxLength;
        }

        @Override
        public String toString(){
            return this.name;
        }
    }
    // Has table answeroptions in db
    //private HashMap<Integer, String> answerOptionsStringAl = new HashMap<>();

    private ArrayList<AnswerOption> answerOptions = new ArrayList<>();

    private ArrayList<Question> subquestionAl = new ArrayList<>();
    private boolean isSubquestion = false;

    public Question()  {

    }

    public void initData(JSONObject q) throws Exception{
        JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
        this.qid = q.getAsString("qid");
        this.setParentQid(q.getAsString("parent_qid"));
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
                AnswerOption newAnswerOption = new AnswerOption();
                newAnswerOption.setQid(this.qid);
                newAnswerOption.setSid(this.sid);
                newAnswerOption.setCode(s);
                newAnswerOption.setIndexi(Integer.parseInt(temp.getAsString("order")));
                newAnswerOption.setText(temp.getAsString("answer"));
                answerOptions.add(newAnswerOption);
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

    public void setParentQid(String parentqid) {
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

    public boolean hasHelp() {
        if(this.help != null){
            return true;
        }
        return false;
    }

    public String encodeJsonBodyAsString(Participant participant){
        return encodeJsonBodyAsString(false, false, "", participant);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant){
        String resString = "";
        String subString = "";
        int index = 1;

        String questionText = this.text;
        int questionsLeft = participant.getUnaskedQuestions().size() + 1;
        // +1 because the question that is about to be sent is already removed from the list
        String newQGroupText = "You completed a question group. There are " + questionsLeft + " questions left.\n";
        if(newQuestionGroup){
            questionText = newQGroupText + questionText;
        }


        if(this.isSubquestion){
            subString += "{\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\":\"" + questionText + "\",\n" +
                    "\"emoji\": true\n" +
                    "},\n" +
                    "\"value\": \"" + this.qid + "\"\n" +
                    "},";
            return subString;
        }

        if(edit){
            // Check if multiple choice question
            if (this.subquestionAl.size() > 0) {
                // no submit button
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
                        "}\n" +
                        "]";

                return resString;
            }


        } else{
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

        }

        resString += questionText;
        System.out.println(this.type);

        // Switch case to check if question type is mask question (their answer options are not saved in question)
        // The question code is from LimeSurvey
        String add = "";
        String firstAdd = "";
        String secondAdd = "";
        String thirdAdd = "";
        String fourthAdd = "";
        String fifthAdd = "";
        if(buttonToColor.length() > 0){
            add = ",\"style\": \"primary\"";
            switch(buttonToColor){
                case "1", "Female", "Yes":
                    firstAdd = add;
                    break;
                case "2", "Male", "No":
                    secondAdd = add;
                    break;
                case "3", "No Answer":
                    thirdAdd = add;
                    break;
                case "4":
                    fourthAdd = add;
                    break;
                case "5":
                    fifthAdd = add;
                    break;
            }

        }

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
                        "\t\t\t\"elements\": [\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Female\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + firstAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Male\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + secondAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No Answer\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + thirdAdd +
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
                        "\t\t\t\"elements\": [\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"Yes\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + firstAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + secondAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"No Answer\",\n" +
                        "\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t}\n" + thirdAdd +
                        "\t\t\t\t}\n" +
                        "\t\t\t]\n" +
                        "\t\t}]";
                break;
            case "5":
                System.out.println("5 point choice");
                resString = "[\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\"text\":\"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"1\"\n" +
                        "\t\t\t\t\t}\n" + firstAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"2\"\n" +
                        "\t\t\t\t\t}\n" + secondAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"3\"\n" +
                        "\t\t\t\t\t}\n" + thirdAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"4\"\n" +
                        "\t\t\t\t\t}\n" + fourthAdd +
                        "\t\t\t\t},\n";
                resString +=
                        "\t\t\t\t{\n" +
                        "\t\t\t\t\t\"type\": \"button\",\n" +
                        "\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\"text\": \"5\"\n" +
                        "\t\t\t\t\t}\n" + fifthAdd +
                        "\t\t\t\t}\n" +
                        "\t\t\t]\n" +
                        "\t\t}\n" +
                        "\t]";
                break;
        }


        //check if single choice question
        if((!(this.answerOptions.isEmpty()) && this.type.equals("L")) || (!(this.answerOptions.isEmpty()) && this.type.equals("O")) || (!(this.answerOptions.isEmpty()) && this.type.equals("!"))) {
            String askForComment = "";
            if(this.type.equals("O")){
                askForComment = " Please write a comment for your chosen option.";
            }
            System.out.println("inside answeroptions with type ! or o");
            resString = "[\n" +
                    "{\n" +
                    "\t\t\t\"type\": \"section\",\n" +
                    "\t\t\t\"text\": {\n" +
                    "\t\t\t\t\"type\": \"plain_text\",\n" +
                    "\t\t\t\t\"text\": \"" + questionText + askForComment + "\",\n" +
                    "\t\t\t\t\"emoji\": true\n" +
                    "\t\t\t}\n" +
                    "\t\t}," +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"actions\",\n" +
                    "\t\t\t\"elements\": [\n" +
                    "\t\t\t\t{\n" +
                    "\t\t\t\t\t\"type\": \"radio_buttons\",\n" +
                    "\t\t\t\t\t\"options\": [";
            for(int i = 1; i < answerOptions.size() + 1; i++){
                if(buttonToColor.equals(getAnswerOptionByIndex(i).getText())){
                    String currAnswerOption = "{\n" +
                            "\t\t\t\t\t\t\t\"text\": {\n" +
                            "\t\t\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                            "\t\t\t\t\t\t\t\t\"text\": \"" + getAnswerOptionByIndex(i).getText() + "\",\n" +
                            "\t\t\t\t\t\t\t\t\"emoji\": true\n" +
                            "\t\t\t\t\t\t\t},\n" +
                            "\t\t\t\t\t\t\t\"style\": \"primary\",\n" +
                            "\t\t\t\t\t\t\t\"value\": \"" + index + "\"\n" +
                            "\t\t\t\t\t\t},";
                    resString += currAnswerOption;
                    index++;
                } else{
                    String currAnswerOption = "{\n" +
                            "\t\t\t\t\t\t\t\"text\": {\n" +
                            "\t\t\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                            "\t\t\t\t\t\t\t\t\"text\": \"" + getAnswerOptionByIndex(i).getText() + "\",\n" +
                            "\t\t\t\t\t\t\t\t\"emoji\": true\n" +
                            "\t\t\t\t\t\t\t},\n" +
                            "\t\t\t\t\t\t\t\"value\": \"" + index + "\"\n" +
                            "\t\t\t\t\t\t},";

                    resString += currAnswerOption;
                    index++;
                }

            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
            resString += "\t\t\t\t\t]," +
                    "\"action_id\": \"" + this.qid + "\"\n" +
                    "\t\t\t\t}\n" +
                    "\t\t\t]\n" +
                    "\t\t}\n" +
                    "\t]";

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

    // gets answer object text and creates string of format ""sidXgidXqidXsqid":"answertext"". This format can be used for limesurvey communication directly
    public String createAnswerString(Answer answer){
        System.out.println("inside createAnswerHashMap");
        boolean hasComment = false;
        if(answer.getComment().length() > 0){
            hasComment = true;
        }
        String answerKey = this.createAnswerKey(this.isSubquestion, this.code, false);
        String returnValue = "\"" + answerKey + "\":\"" + answer.getText() + "\",";

        if(hasComment){
            // if the answer has a comment, get the comment answer
            answerKey = this.createAnswerKey(this.isSubquestion, this.code, hasComment);
            returnValue += "\"" + answerKey + "\":\"" + answer.getComment() + "\",";
        }

        return returnValue;
    }

    private String createAnswerKey (boolean isSubquestion, String code, boolean comment){
        System.out.println("inside createAnswerKey. isSubquestion :" + isSubquestion + " code: " + code + " iscomment " + comment + " qid: " + this.qid + " this parentqid: " + this.parentqid);
        String separator = "X";
        String returnValue = "";
        if(isSubquestion){
            returnValue = this.sid + separator + this.gid + separator + this.parentqid + code;
        }
        else{
            returnValue = this.sid + separator + this.gid + separator + this.qid;
        }

        if(comment){
            returnValue += "comment";
        }
        System.out.println("create answer function return value: " + returnValue);
        return returnValue;
    }

    public boolean answerIsPlausible(String textAnswer){

        if(this.type.equals(qType.SINGLECHOICECOMMENT.toString()) || this.type.equals(qType.LISTRADIO.toString()) || this.type.equals(qType.LISTDROPDOWN.toString())){
            System.out.println("Question type singlechoice recognized.");
            // for these types, a answeroptionslist is available, only answers equal to one of these options is ok
            for(AnswerOption ao : answerOptions){
                if(ao.getText().equals(textAnswer)){
                    System.out.println("Answer is valid.");
                    return true;
                }
            }
        }

        if(!this.subquestionAl.isEmpty()){
            System.out.println("Question type multiple choice recognized.");
            // If it a mulitple choice question, check if textAnswer equals one answer option (which is saves as text from subquestion)
            for(Question q : this.subquestionAl){
                System.out.println("calling answer plausible recursively...");
                if(q.answerIsPlausible(textAnswer)){
                    System.out.println("Answer is valid.");
                    return true;
                }
            }
        }

        if(this.isSubquestion){
            System.out.println("Question type (multiple choice) subquestion recognized.");
            // if it is an answer to a mulitple choice question answer option, it is exactly that subquestion text
            if(this.text.equals(textAnswer)){
                System.out.println("textanswer: " + textAnswer + " text: " + this.text);
                System.out.println("Answer is valid.");
                return true;
            }
            return false;
        }

        if(this.type.equals(qType.GENDER.toString())){
            System.out.println("Question type gender recognized.");
            // a gender question only has these three options
            if(textAnswer.equals("Female") || textAnswer.equals("Male") || textAnswer.equals("No Answer")){
                System.out.println("Answer is valid.");
                return true;
            }
        }

        if(this.type.equals(qType.YESNO.toString())){
            System.out.println("Question type yesno recognized.");
            // yes no question has only these three answers
            if(textAnswer.equals("Yes") || textAnswer.equals("No") || textAnswer.equals("No Answer")){
                System.out.println("Answer is valid.");
                return true;
            }
        }

        if(this.type.equals(qType.SHORTFREETEXT.toString())){
            System.out.println("Question type free text recognized.");
            if(textAnswer.length() < qType.SHORTFREETEXT.getMaxLength()){
                System.out.println("Answer is valid.");
                return true;
            }
        }

        if(this.type.equals(qType.LONGFREETEXT.toString())){
            System.out.println("Question type free text recognized.");
            if(textAnswer.length() < qType.LONGFREETEXT.getMaxLength()){
                System.out.println("Answer is valid.");
                return true;
            }
        }

        if(this.type.equals(qType.HUGEFREETEXT.toString())){
            System.out.println("Question type free text recognized.");
            if(textAnswer.length() < qType.HUGEFREETEXT.getMaxLength()){
                System.out.println("Answer is valid.");
                return true;
            }
        }

        if(this.type.equals(qType.FIVESCALE.toString())){
            System.out.println("Question type 5 scale rating recognized.");
            try{
                int var = Integer.parseInt(textAnswer);
                if(var < 6 && 0 < var){
                    System.out.println("Answer is valid.");
                    return true;
                }
            } catch(Exception e){
                System.out.println("answer is not plausible");
                return false;
            }
        }

        if(this.type.equals(qType.NUMERICALINPUT.toString())){
            System.out.println("Question type numerical input recognized.");
            try{
                int var = Integer.parseInt(textAnswer);
                return true;
            } catch(Exception e){
                System.out.println("answer is not plausible");
                return false;
            }
        }

        if(this.type.equals(qType.DATETIME.toString())){
            System.out.println("Question type datetime recognized.");
            try{
                // TODO
                DateFormat sourceFormat = new SimpleDateFormat("dd.MM.yyyy");
                sourceFormat.parse(textAnswer);
                return true;
            } catch(Exception e){
                System.out.println("answer is not plausible");
                return false;
            }
        }

        System.out.println("answer is not plausible");
        return false;
    }

    public String reasonAnswerNotPlausible(String type){

        String reason = "";

        if(type.equals(qType.LISTDROPDOWN.toString()) ||
           type.equals(qType.LISTRADIO.toString()) ||
           type.equals(qType.GENDER.toString()) ||
           type.equals(qType.YESNO.toString())){
            reason = "Please answer by clicking on one of the displayed buttons.";
        }

        if(type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
            reason = "Please check all the boxes of answers that aply and then click on the \"Submit\" button";
        }

        if(type.equals(qType.SHORTFREETEXT.toString()) ||
           type.equals(qType.HUGEFREETEXT.toString()) ||
           type.equals(qType.LONGFREETEXT.toString())){
            reason = "Your answer is too long, please shorten it and send again.";
        }

        if(type.equals(qType.DATETIME.toString())){
            reason = "Please answer with a date in the format dd.mm.yyyy.";
        }

        if(type.equals(qType.FIVESCALE.toString())){
            reason = "Please only answer with a number between 1 and 5.";
        }

        if(type.equals(qType.NUMERICALINPUT.toString())){
            reason = "Please answer with a number.";
        }

        return reason;
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

    public boolean isBlocksQuestion(){
        if(this.type.equals(qType.SHORTFREETEXT.toString()) ||
                this.type.equals(qType.LONGFREETEXT.toString()) ||
                this.type.equals(qType.HUGEFREETEXT.toString()) ||
                this.type.equals(qType.NUMERICALINPUT.toString()) ||
                this.type.equals(qType.DATETIME.toString()) ||
                this.type.equals(qType.FILEUPLOAD.toString()) ||
                this.type.equals(qType.TEXTDISPLAY.toString())){
            System.out.println("isblocksquestion false");
            return false;
        } else{
            return true;
        }
    }

    public ArrayList<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public void setAnswerOptions(ArrayList<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions;
    }

    public void setAnswerOption(AnswerOption answerOption){
        this.answerOptions.add(answerOption);
    }

    public AnswerOption getAnswerOptionByIndex(Integer index){
        for(AnswerOption ao : answerOptions){
            if(ao.getIndexi().equals(index)){
                return ao;
            }
        }
        return null;
    }
}
