package i5.las2peer.services.SurveyHandler;

import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class Question{

    // Database model identifier
    private String qid;
    private String gid;
    private String parentqid;
    private String text;
    private String type;
    private String qorder;
    private String gorder;
    private String sid;
    private String help;
    private String relevance;
    private String code;
    // end Database model identifier

    public static enum qType{
        ARRAY("F"),
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
        HUGEFREETEXT("U"),
        DICHOTOMOUS("D"),
        SCALE("SC");

        private final String name;
        private final int maxLength;
        private qType(String name){
            this.name= name;
            // TODO find correct values
            if(this.name.equals("T")){
                this.maxLength = 1200;
            }
            else if(this.name.equals("U")){
                this.maxLength = 1200;
            }
            else if(this.name.equals("S")){
                this.maxLength = 600;
            }
            else{
                this.maxLength = 1200;
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

    public void initLimeSurveyData(JSONObject q) throws Exception{
        JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
        this.qid = q.getAsString("qid");
        this.setParentQid(q.getAsString("parent_qid"));
        this.gid = q.getAsString("gid");
        this.qorder = q.getAsString("question_order");
        this.text = q.getAsString("question");
        if(text.contains("<b id=\"docs-internal-guid") || text.contains("<p>")){
            System.out.println("detected weird question text, fixing ...");
            String[] textA = text.split(">");
            for(String currT : textA){
                System.out.println(currT);
                String c = String.valueOf(currT.charAt(0));
                if(!c.equals("<")){
                    text = currT;
                    break;
                }
            }
            text = text.split("<")[0];
        }
        this.sid = q.getAsString("sid");
        this.help = q.getAsString("help");
        this.type = q.getAsString("type");
        this.relevance = q.getAsString("relevance");
        this.code = q.getAsString("title");
        this.gorder = q.getAsString("group_order");
        System.out.println("answeroptinos" + q.getAsString("answeroptions"));
        if(!q.getAsString("answeroptions").contains("No available answer options")){
            JSONObject answeroptions = (JSONObject) q.get("answeroptions");
            for(String s : answeroptions.keySet()){
                JSONObject temp = (JSONObject) answeroptions.get(s);
                AnswerOption newAnswerOption = new AnswerOption();
                newAnswerOption.setQid(this.qid);
                newAnswerOption.setSid(this.sid);
                newAnswerOption.setCode(s);
                newAnswerOption.setIndexi(Integer.parseInt(temp.getAsString("order")));
                newAnswerOption.setText(temp.getAsString("answer"));
                this.answerOptions.add(newAnswerOption);
            }
        }
        if (Integer.parseInt(this.parentqid)> 0){
            this.isSubquestion = true;
        }
    }

    public void initMobsosData(JSONObject q, int index) throws Exception{
        JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

        this.text = q.getAsString("instructions");
        this.sid = q.getAsString("sid");

        // the questions do not have a help text, relevance or are subquestions
        this.help = "";
        this.relevance = "1";
        this.setParentQid("0");

        // the question groups are not defined
        this.gid = "1";
        this.gorder = "1";

        // the question code will be deined as the index, since its unique
        this.code = String.valueOf(index);
        this.qorder = String.valueOf(index);


         // check for type, since information text does not have qid
        if(q.getAsString("type").equals("qu:InformationPageType")){
            this.qid = String.valueOf(index);
        }
        else{
            // if not display of information, set qid
            this.qid = q.getAsString("qid");
        }

        if(q.getAsString("type").equals("qu:InformationPageType")){
            this.type = "X";
        }
        else if(q.getAsString("type").equals("qu:OrdinalScaleQuestionPageType")){
            this.type = "SC";
            this.text += " Please rate on a scale of " + q.getAsString("minval") + " (" + q.getAsString("minlabel") + ") to " + q.getAsString("maxval") + " (" + q.getAsString("maxlabel") + ").";
            for(int i = Integer.parseInt(q.getAsString("minval")); i <= Integer.parseInt(q.getAsString("maxval")); i++){
                AnswerOption newAnswerOption = new AnswerOption();
                newAnswerOption.setQid(this.qid);
                newAnswerOption.setSid(this.sid);
                newAnswerOption.setCode(String.valueOf(i));
                newAnswerOption.setIndexi(i+1);
                newAnswerOption.setText(String.valueOf(i));
                this.answerOptions.add(newAnswerOption);
            }
        }
        else if(q.getAsString("type").equals("qu:DichotomousQuestionPageType")){
            this.type = "D";

            // set answer option 1
            AnswerOption newAnswerOption1 = new AnswerOption();
            newAnswerOption1.setQid(this.qid);
            newAnswerOption1.setSid(this.sid);
            newAnswerOption1.setCode("0");
            newAnswerOption1.setIndexi(1);
            newAnswerOption1.setText(q.getAsString("minlabel"));
            this.answerOptions.add(newAnswerOption1);

            // set answer option 2
            AnswerOption newAnswerOption2 = new AnswerOption();
            newAnswerOption2.setQid(this.qid);
            newAnswerOption2.setSid(this.sid);
            newAnswerOption2.setCode("1");
            newAnswerOption2.setIndexi(2);
            newAnswerOption2.setText(q.getAsString("maxlabel"));
            this.answerOptions.add(newAnswerOption2);

        }
        else if(q.getAsString("type").equals("qu:FreeTextQuestionPageType")){
            this.type = "U";
        }
        else{
            System.out.println("ERROR: Type for mobsos question not recongized!");
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

    public String getGorder() {
        return gorder;
    }

    public void setGorder(String gorder) {
        this.gorder = gorder;
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

    public Question getParentQuestion(){
        return SurveyHandlerService.getSurveyBySurveyID(this.sid).getQuestionByQid(this.parentqid);
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

    public String encodeJsonBodyAsString(Participant participant, boolean slack){
        return encodeJsonBodyAsString(false, false, "", participant, slack);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, boolean slack){
        return encodeJsonBodyAsString(newQuestionGroup, edit, buttonToColor, participant, slack, null);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, boolean slack, Integer arrayNumber){
        System.out.println("inside encodejsonbodyasstring. slack: " + slack);
        if(slack){
            return parseQuestion(newQuestionGroup, edit, buttonToColor, participant, arrayNumber);
        }
        else{
            return parseQuestionAsText(newQuestionGroup, participant, arrayNumber);
        }
    }

    public String parseQuestion(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, Integer arrayNumber){
        String resString = "";
        String subString = "";
        int index = 1;

        String questionText = this.text;
        int questionsLeft = participant.getUnaskedQuestions().size() + 1;
        // +1 because the question that is about to be sent is already removed from the list
        String newQGroupText = "";
        if(questionsLeft > 1){
            newQGroupText = "You completed a question group. There are " + questionsLeft + " questions left.\n";
        }
        else{
            newQGroupText = "You completed a question group. There is " + questionsLeft + " question left.\n";
        }

        if(newQuestionGroup){
            questionText = newQGroupText + questionText;
        }


        if(this.isSubquestion && !this.getParentQuestion().getType().equals(qType.ARRAY.toString())){
            subString += "{\n" +
                    "\"text\": {\n" +
                    "\"type\": \"plain_text\",\n" +
                    "\"text\":\"" + questionText + "\",\n" +
                    "\"emoji\": true\n" +
                    "},\n" +
                    "\"value\": \"" + this.qid + "\"\n" +
                    "},";
            return subString;
        } else if(this.isSubquestion && this.getParentQuestion().getType().equals(qType.ARRAY.toString())){
            subString += "\n" + questionText;
            subString += "\",\n" +
                    "\"emoji\": true\n" +
                    "}\n" +
                    "},\n";
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
                    resString += subq.encodeJsonBodyAsString(participant, true);
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
            if (this.subquestionAl.size() > 0 && !this.type.equals(qType.ARRAY.toString())) {
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
                    resString += subq.encodeJsonBodyAsString(participant, true);
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
            } else if(this.type.equals(qType.ARRAY.toString())){
                resString = "[\n" +
                        "{\n" +
                        "\"type\": \"section\",\n" +
                        "\"text\": {\n" +
                        "\"type\": \"plain_text\",\n" +
                        "\"text\": \"" + questionText + "\n";

                Question subq = this.getSubquestionByIndex(String.valueOf(arrayNumber));
                resString += subq.encodeJsonBodyAsString(participant, true);

                resString += "\t\t\t]\n" +
                        "\t\t}]}]";

                System.out.println("res: " + resString);
            }

        }

        resString += questionText;
        System.out.println(this.type);

        if(this.isSubquestion && !this.getParentQuestion().answerOptions.isEmpty()){
            resString = subString + "\t\t{\n" +
                    "\t\t\t\"type\": \"actions\",\n" +
                    "\t\t\t\"elements\": [\n" +
                    "\t\t\t\t{\n" +
                    "\t\t\t\t\t\"type\": \"radio_buttons\",\n" +
                    "\t\t\t\t\t\"options\": [";
            for(int i = 1; i < this.getParentQuestion().answerOptions.size() + 1; i++){
                String currAnswerOption = "{\n" +
                        "\t\t\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\t\t\"text\": \"" + this.getParentQuestion().getAnswerOptionByIndex(i).getText() + "\",\n" +
                        "\t\t\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t\t\t},\n" +
                        "\t\t\t\t\t\t\t\"value\": \"" + index + "\"\n" +
                        "\t\t\t\t\t\t},";

                resString += currAnswerOption;
                index++;

            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
        }

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
        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.DICHOTOMOUS.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.SCALE.toString()))) {

            System.out.println("inside answeroptions with type dichotomous or scale");
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
            for(int i = 1; i < answerOptions.size() + 1; i++){
                if(buttonToColor.equals(getAnswerOptionByIndex(i).getText())){
                    String currAnswerOption = "\t\t\t\t{\n" +
                                    "\t\t\t\t\t\"type\": \"button\",\n" +
                                    "\t\t\t\t\t\"text\": {\n" +
                                    "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                                    "\t\t\t\t\t\t\"text\": \"" + getAnswerOptionByIndex(i).getText() + "\",\n" +
                                    "\t\t\t\t\t},\n" +
                                    "\t\t\t\t\t\t\t\"style\": \"primary\"\n" +
                                    "\t\t\t\t},\n";
                    resString += currAnswerOption;
                    index++;
                } else{
                    String currAnswerOption = "\t\t\t\t{\n" +
                            "\t\t\t\t\t\"type\": \"button\",\n" +
                            "\t\t\t\t\t\"text\": {\n" +
                            "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                            "\t\t\t\t\t\t\"text\": \"" + getAnswerOptionByIndex(i).getText() + "\",\n" +
                            "\t\t\t\t\t}\n" +
                            "\t\t\t\t},\n";

                    resString += currAnswerOption;
                    index++;
                }

            }
            // remove last comma after the options
            resString = resString.substring(0, resString.length() - 1);
            resString += "\t\t\t]\n" +
                    "\t\t}\n" +
                    "\t]";

            System.out.println("resstring: " + resString);
        }

        //check if single choice question
        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.LISTRADIO.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.SINGLECHOICECOMMENT.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.LISTDROPDOWN.toString()))) {
            String askForComment = "";
            if(this.type.equals(qType.SINGLECHOICECOMMENT.toString())){
                askForComment = " Please write a comment for your chosen option.";
            }
            System.out.println("inside answeroptions with type ! or o or l");
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

    public String parseQuestionAsText(boolean newQuestionGroup, Participant participant){
        return parseQuestionAsText(newQuestionGroup, participant, null);
    }

    public String parseQuestionAsText(boolean newQuestionGroup, Participant participant, Integer arrayNumber){
        String resString = "";
        String subString = "";
        int index = 1;

        String exp = " Please choose one of the following options by sending the respective number as a response: \n";

        String questionText = this.text;
        int questionsLeft = participant.getUnaskedQuestions().size() + 1;
        // +1 because the question that is about to be sent is already removed from the list
        String newQGroupText = "";
        if(questionsLeft > 1){
            newQGroupText = "You completed a question group. There are " + questionsLeft + " questions left.\n";
        }
        else{
            newQGroupText = "You completed a question group. There is " + questionsLeft + " question left.\n";
        }

        if(newQuestionGroup){
            questionText = newQGroupText + questionText;
        }


        if(this.isSubquestion){
            subString += this.text;
            return subString;
        }

        resString += questionText;

        // Check if multiple choice question
        if (this.subquestionAl.size() > 0 && !this.type.equals(qType.ARRAY.toString())) {
            if(this.type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                resString += "Please choose from the following options by sending the respective number as a response as well as a comment for your chosen option in the format \"number of your chosen option\":\"your comment\" and do not use : in your answer. If you want to choose no option, please enter \"-\". If you choose more than one, please answer in the format \"number of your chosen option\":\"your comment\";\"number of your second chosen option\":\"your second comment\" and so on.";
            } else{
                // no comment required
                resString += "Please choose from the following options by sending the respective number as a response. If you choose more than one, please separate the numbers with a comma and no space. If you want to choose no option, please enter \"-\".";
            }
            for (Question subq : this.subquestionAl) {
                resString += "\n" + index + ". " + subq.encodeJsonBodyAsString(participant, false);
                index++;
            }

        } else if(this.subquestionAl.size() > 0 && !this.answerOptions.isEmpty() && this.type.equals(qType.ARRAY.toString())){
            // type array recognoized
            //System.out.println("subquestional size: " + this.subquestionAl.size());
            //System.out.println("arraynumber: " + arrayNumber);
            //System.out.println("subquestional: " + this.subquestionAl.get(arrayNumber - 1).getQid());

            Question subq = this.subquestionAl.get(arrayNumber - 1);
            resString += exp + subq.encodeJsonBodyAsString(participant, false) + "\n";

            //System.out.println("inside answeroptions with type array");
            for(int i = 1; i < answerOptions.size() + 1; i++){
                resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
            }

        }

        System.out.println(this.type);

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
                resString += exp;
                resString += " 1. Female ";
                resString += " 2. Male ";
                resString += " 3. No Answer ";
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
                resString += exp;
                resString += " 1. Yes ";
                resString += " 2. No ";
                resString += " 3. No Answer ";
                break;
            case "5":
                System.out.println("5 point choice");
                resString += " Please only answer with a number between 1 and 5.";
                break;

        }
        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.DICHOTOMOUS.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.SCALE.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.LISTRADIO.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.LISTDROPDOWN.toString()))) {

            System.out.println("inside answeroptions with type dichotomous, scale, listradio or listdropdown");
            resString += exp;
            for(int i = 1; i < answerOptions.size() + 1; i++){
                resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
                index++;
            }

            System.out.println("resstring: " + resString);
        }

        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.SINGLECHOICECOMMENT.toString()))){
            System.out.println("inside answeroptions with type singlechoicecomment");
            resString += " Please choose one of the following options by sending the respective number as a response as well as a comment for your chosen option in the format \"number of your chosen answer option\":\"your comment\": \n";
            for(int i = 1; i < answerOptions.size() + 1; i++){
                resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
                index++;
            }
        }

        if(!this.isSubquestion){
            if(this.help.length() > 0){
                resString += "\n This is the help: " + this.help + "";
            }
        }
        return resString;
    }

    public Question getSubquestionByIndex(String index){
        //System.out.println(index);
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
    public String createLimeAnswerString(Answer answer){
        System.out.println("inside createAnswerHashMap lime");
        boolean hasComment = false;
        if(answer.getComment().length() > 0){
            hasComment = true;
        }
        String answerKey = this.createAnswerKey(this.isSubquestion, this.code, false);
        String answerText = answer.getText();
        if(answerText.contains("\"") || answerText.contains("\n")){
            System.out.println("contains \" or \n, deleting now...");
            System.out.println("old: " + answerText);
            answerText = answerText.replaceAll("\"", "'");
            answerText = answerText.replaceAll("\n", " ");
            System.out.println("new: " + answerText);
        }
        String returnValue = "\"" + answerKey + "\":\"" + answerText + "\",";
        if(hasComment){
            // if the answer has a comment, get the comment answer
            answerKey = this.createAnswerKey(this.isSubquestion, this.code, hasComment);
            String commentText = answer.getComment();
            if(commentText.contains("\"") || commentText.contains("\n")){
                System.out.println("contains \" or \n, deleting now...");
                commentText = commentText.replaceAll("\"", "'");
                commentText = commentText.replaceAll("\n", " ");
            }
            returnValue += "\"" + answerKey + "\":\"" + commentText + "\",";
        }

        return returnValue;
    }

    public String createMobsosAnswerString(Answer answer){
        System.out.println("inside createAnswerHashMap mobsos");
        String answerKey = this.qid;
        String answerText = answer.getText();
        if(answerText.contains("\"") || answerText.contains("\n")){
            System.out.println("contains \" or \n, deleting now...");
            System.out.println("old: " + answerText);
            answerText = answerText.replaceAll("\"", "'");
            answerText = answerText.replaceAll("\n", " ");
            System.out.println("new: " + answerText);
        }
        String returnValue = "\"" + answerKey + "\":\"" + answerText + "\",";

        System.out.println("created mobsos answer string: " + returnValue);
        return returnValue;
    }

    private String createAnswerKey (boolean isSubquestion, String code, boolean comment){
        //System.out.println("inside createAnswerKey. isSubquestion :" + isSubquestion + " code: " + code + " iscomment " + comment + " qid: " + this.qid + " this parentqid: " + this.parentqid);
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
        //System.out.println("create answer function return value: " + returnValue);
        return returnValue;
    }

    public boolean answerIsPlausible(String textAnswer, boolean slack){

        if(slack){
            if(this.type.equals(qType.SINGLECHOICECOMMENT.toString()) || this.type.equals(qType.LISTRADIO.toString()) || this.type.equals(qType.LISTDROPDOWN.toString()) ||
                    this.type.equals(qType.DICHOTOMOUS.toString()) || this.type.equals(qType.SCALE.toString())){
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
                    if(q.answerIsPlausible(textAnswer, slack)){
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
        }
        else{
            // rocket chat
            if(this.type.equals(qType.LISTRADIO.toString()) || this.type.equals(qType.LISTDROPDOWN.toString()) ||
                    this.type.equals(qType.DICHOTOMOUS.toString()) || this.type.equals(qType.SCALE.toString())){
                System.out.println("Question type singlechoice recognized.");
                // for these types, a answeroptionslist is available, only answers equal to one of these options is ok
                int size = this.answerOptions.size();
                try{
                    if(0 < Integer.parseInt(textAnswer) && Integer.parseInt(textAnswer) < size + 1){
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }

            }

            if(this.type.equals(qType.GENDER.toString()) || this.type.equals(qType.YESNO.toString())){
                try{
                    if(0 < Integer.parseInt(textAnswer) && Integer.parseInt(textAnswer) < 4){
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }

            }

            if(this.type.equals(qType.ARRAY.toString())){
                int size = this.answerOptions.size();
                try{
                    if(0 < Integer.parseInt(textAnswer) && Integer.parseInt(textAnswer) < size + 1){
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }

            }

            if(this.type.equals(qType.SINGLECHOICECOMMENT.toString())){
                try{
                    String chosen = textAnswer.split(":")[0];
                    String comment = textAnswer.split(":")[1];
                    int size = this.answerOptions.size();
                    if(0 < Integer.parseInt(chosen) && Integer.parseInt(chosen) < size + 1){
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }

            }

            if(this.type.equals(qType.MULTIPLECHOICENOCOMMENT.toString())){
                try{
                    if(textAnswer.equals("-")){
                        return true;
                    }
                    else{
                        String[] chosen = textAnswer.split(",");
                        System.out.println("chosen: " + chosen);
                        int size = this.subquestionAl.size();
                        for(String s : chosen){
                            System.out.println("parsed int: " + Integer.parseInt(s) + "max size " + size);
                            if(!(0 < Integer.parseInt(s) && Integer.parseInt(s) < size + 1)){
                                return false;
                            }
                        }
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }


            }

            if(this.type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                try{
                    if(textAnswer.equals("-")){
                        return true;
                    }
                    else{
                        String[] all = textAnswer.split(";");
                        System.out.println("all: " + all);
                        ArrayList<String> chosen = new ArrayList<>();
                        ArrayList<String> comments = new ArrayList<>();
                        int size = this.subquestionAl.size();
                        for(String s : all){
                            chosen.add(s.split(":")[0]);
                            comments.add(s.split(":")[1]);
                        }
                        System.out.println("chosen: " + chosen);
                        System.out.println("comments: " + comments);
                        for(String s : chosen){
                            System.out.println("parsed int: " + Integer.parseInt(s) + "max size " + size);
                            if(!(0 < Integer.parseInt(s) && Integer.parseInt(s) < size + 1)){
                                return false;
                            }
                        }
                        return true;
                    }
                } catch(Exception e){
                    System.out.println("answer is not plausible");
                    return false;
                }


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

        System.out.println("answer is not plausible (function end)");
        return false;
    }

    public String reasonAnswerNotPlausible(boolean slack){

        String type = this.type;
        String reason = "";

        if(slack){
            if(type.equals(qType.LISTDROPDOWN.toString()) ||
                    type.equals(qType.LISTRADIO.toString()) ||
                    type.equals(qType.DICHOTOMOUS.toString()) ||
                    type.equals(qType.SCALE.toString()) ||
                    type.equals(qType.GENDER.toString()) ||
                    type.equals(qType.YESNO.toString())){
                reason = "Please answer by clicking on one of the displayed buttons.";
            }

            if(type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                reason = "Please check all the boxes of answers that aply and then click on the \"Submit\" button";
            }
        }
        else{
            if(type.equals(qType.LISTDROPDOWN.toString()) ||
                    type.equals(qType.LISTRADIO.toString()) ||
                    type.equals(qType.DICHOTOMOUS.toString()) ||
                    type.equals(qType.SCALE.toString()) ||
                    type.equals(qType.GENDER.toString()) ||
                    type.equals(qType.YESNO.toString()) ||
                    type.equals(qType.ARRAY.toString())){
                reason = "Please only answer with one of the given numbers written before the answer option";
            }

            if(type.equals(qType.SINGLECHOICECOMMENT.toString())){
                reason = "Please answer in the format \"number of your chosen option\":\"your comment\" and do not use : in your answer.";
            }

            if(type.equals(qType.MULTIPLECHOICENOCOMMENT.toString())){
                reason = "Please only answer with one of the given numbers written before the answer option and comma speparated with no spaces in between.";
            }

            if(type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                reason = "Please answer in the format \"number of your chosen option\":\"your comment\";\"number of your second chosen option\":\"your second comment\"...";
            }

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

    public AnswerOption getAnswerOptionByCode(String code){
        for(AnswerOption ao : answerOptions){
            if(ao.getCode().equals(code)){
                return ao;
            }
        }
        return null;
    }
}
