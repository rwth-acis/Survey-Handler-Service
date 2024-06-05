package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

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
    private String language;
    private boolean mandatory;
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
        DICHOTOMOUS("DI"),
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

    private ArrayList<AnswerOption> answerOptions = new ArrayList<>();

    private ArrayList<Question> subquestionAl = new ArrayList<>();
    private boolean isSubquestion = false;

    public Question()  {

    }

    public void initLimeSurveyData(JSONObject q){
        this.qid = q.getAsString("qid");
        this.setParentQid(q.getAsString("parent_qid"));
        this.gid = q.getAsString("gid");
        this.qorder = q.getAsString("question_order");
        this.text = q.getAsString("question");
        text = text.replaceAll("\"", "\\\\\"");
        if(text.contains("<b id=\"docs-internal-guid") || text.contains("<p>") || (text.contains("<") && text.contains(">"))){
            System.out.println("detected weird question text, fixing ...");
            System.out.println("before: " + text);
            text = text.replaceAll("<.*?>","");
            System.out.println("after: " + text);
        }
        this.sid = q.getAsString("sid");
        this.help = q.getAsString("help");
        this.type = q.getAsString("type");
        this.relevance = q.getAsString("relevance");
        this.code = q.getAsString("title");
        this.gorder = q.getAsString("group_order");
        this.language = q.getAsString("language");
        if(q.getAsString("mandatory") == null){

        }
        else if(q.getAsString("mandatory").equals("N")){
            this.mandatory = false;
        }
        else if(q.getAsString("mandatory").equals("Y")){
            this.mandatory = true;
        }
        this.language = q.getAsString("language");
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
                newAnswerOption.setLanguage(this.language);
                this.answerOptions.add(newAnswerOption);
            }
        }
        if (Integer.parseInt(this.parentqid)> 0){
            this.isSubquestion = true;
        }
    }

    public Survey getSurvey(){
        return SurveyHandlerService.getSurveyBySurveyID(this.sid);
    }

    public void initMobsosData(JSONObject q){
        String language = q.getAsString("language");

        this.text = q.getAsString("instructions");
        this.text = this.text.replaceAll("\"", "\\\\\"");
        this.sid = q.getAsString("sid");

        // the questions do not have a help text, relevance or are subquestions
        this.help = "";
        this.relevance = "1";
        this.mandatory = q.getAsString("required").equals("1");
        this.language = language;
        this.setParentQid("0");

        // the question groups are not defined
        this.gid = "1";
        this.gorder = "1";

        this.code = q.getAsString("order");
        this.qorder = q.getAsString("order");

        // check for type, since information text does not have qid
        this.qid = q.getAsString("type").equals("qu:InformationPageType") ? q.getAsString("order") : q.getAsString("qid");


        if(q.getAsString("type").equals("qu:InformationPageType")){
            this.type = "X";
        }
        else if(q.getAsString("type").equals("qu:OrdinalScaleQuestionPageType")){
            this.type = "SC";
            if(languageIsGerman()){
                this.text += SurveyHandlerService.texts.get("SCDE") + q.getAsString("minval") + " (" + q.getAsString("minlabel") + ") - " + q.getAsString("maxval") + " (" + q.getAsString("maxlabel") + ").";
            } else{
                this.text += SurveyHandlerService.texts.get("SC") + q.getAsString("minval") + " (" + q.getAsString("minlabel") + ") - " + q.getAsString("maxval") + " (" + q.getAsString("maxlabel") + ").";
            }
            for(int i = Integer.parseInt(q.getAsString("minval")); i <= Integer.parseInt(q.getAsString("maxval")); i++){
                setAnswerOption(this.qid, this.sid, String.valueOf(i), i, language, String.valueOf(i));
            }
        }
        else if(q.getAsString("type").equals("qu:DichotomousQuestionPageType")){
            this.type = "DI";

            // set answer option 1
            setAnswerOption(this.qid, this.sid, "0", 1, language, q.getAsString("minlabel"));

            // set answer option 2
            setAnswerOption(this.qid, this.sid, "1", 2, language, q.getAsString("maxlabel"));
        }
        else if(q.getAsString("type").equals("qu:FreeTextQuestionPageType")){
            this.type = "U";
        }
        else{
            System.out.println("ERROR: Type for mobsos question not recongized!");
        }


    }

    private void setAnswerOption(String qid, String sid, String code, int index, String language, String text){
        AnswerOption newAnswerOption2 = new AnswerOption();
        newAnswerOption2.setQid(qid);
        newAnswerOption2.setSid(sid);
        newAnswerOption2.setCode(code);
        newAnswerOption2.setIndexi(index);
        newAnswerOption2.setLanguage(language);
        newAnswerOption2.setText(text);
        this.answerOptions.add(newAnswerOption2);
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

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean languageIsGerman(){
        if(this.language.equals("de") || this.language.startsWith("de")){
            return true;
        }

        return false;
    }

    public void setParentQid(String parentqid) {
        System.out.println("thisq: " + this.qid);
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
        return this.getSurvey().getQuestionByQid(this.parentqid, this.language);
    }

    public String getHelp() {
        return this.help;
    }

    public String encodeJsonBodyAsString(Participant participant){
        return encodeJsonBodyAsString(false, false, "", participant);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, Participant participant, Integer arrayNumber){
        return encodeJsonBodyAsString(newQuestionGroup, false, "", participant, arrayNumber);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant){
        return encodeJsonBodyAsString(newQuestionGroup, edit, buttonToColor, participant, null);
    }

    public String encodeJsonBodyAsString(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, Integer arrayNumber){
        System.out.println("inside encodejsonbodyasstring. slack: " + SurveyHandlerService.messenger.equals(Messenger.SLACK));
        System.out.println("inside encodejsonbodyasstring. telegram: " + SurveyHandlerService.messenger.equals(Messenger.TELEGRAM));
        if(SurveyHandlerService.messenger.equals(Messenger.SLACK)){
            return parseQuestionForSlack(newQuestionGroup, edit, buttonToColor, participant, arrayNumber);
        }
        else if(SurveyHandlerService.messenger.equals(Messenger.TELEGRAM)){
            return parseQuestionForTelegram(newQuestionGroup, edit, buttonToColor, participant, arrayNumber);
        }
        else{
            return parseQuestionAsText(newQuestionGroup, participant, arrayNumber);
        }
    }

    public String parseQuestionForTelegram(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, Integer arrayNumber){
        String resString = "";
        String subString = "";
        int index = 1;

        // check mark for chosen button
        String check = SurveyHandlerService.telegramButtonCheck;

        String questionText = completedQuestionGroup(newQuestionGroup, participant);


        if(this.isSubquestion && !this.getParentQuestion().getType().equals(qType.ARRAY.toString())){
            // for every button that was answered check button
            // if answer with y exist add check
            Answer a = participant.getAnswer(this.qid);
            if(a != null)
                if(a.getText().equals("Y"))
                    questionText = check + questionText;

            subString += "[{\"text\":\"" + questionText + "\",\"callback_data\": \"" + questionText + "\"}],";
            return subString;
        } else if(this.isSubquestion && this.getParentQuestion().getType().equals(qType.ARRAY.toString())){
            subString += "\n" + questionText;
        }

        resString = handleTelegramMC(newQuestionGroup, edit, participant, arrayNumber, resString, questionText);

        if(resString.length() == 0){
            resString += questionText;
        }
        if(this.isSubquestion && !this.getParentQuestion().answerOptions.isEmpty()){
            resString = subString + "\",\"inline_keyboard\": [";
            for(int i = 1; i < this.getParentQuestion().answerOptions.size() + 1; i++){
                String text = this.getParentQuestion().getAnswerOptionByIndex(i).getText();
                if(buttonToColor.length() > 0){
                    if(buttonToColor.equals(text))
                        text = check + text;
                }
                String currAnswerOption = "[{\"text\":\"" + text + "\",\"callback_data\": \"" + text + "\"}],";

                resString += currAnswerOption;
                index++;

            }
            if(!isMandatory()){
                if(this.languageIsGerman()){
                    resString += "[{\"text\":\"Keine Antwort\",\"callback_data\": \"Keine Antwort\"}]";
                } else{
                    resString += "[{\"text\":\"No Answer\",\"callback_data\": \"No Answer\"}]";
                }
            }
            else{
                // remove last comma after the options
                resString = resString.substring(0, resString.length() - 1);
            }

        }

        String female = "Female";
        String male = "Male";
        String yes = "Yes";
        String no = "No";
        String noAnswer = "No Answer";
        if(languageIsGerman()){
            female = "Weiblich";
            male = "Maennlich";
            yes = "Ja";
            no = "Nein";
            noAnswer = "Keine Antwort";
        }
        if(buttonToColor.length() > 0){
            if(buttonToColor.equals(female)){
                female = check + female;
            }
            else if(buttonToColor.equals(male)){
                male = check + male;
            }
            else if(buttonToColor.equals(yes)){
                yes = check + yes;
            }
            else if(buttonToColor.equals(no)){
                no = check + no;
            }
            else if(buttonToColor.equals(noAnswer)){
                noAnswer = check + noAnswer;
            }

        }
        switch(this.type){
            case "G":
                System.out.println("Gender");
                resString = "{\"text\":\"" + questionText + "\",\"inline_keyboard\": [[{\"text\":\"" + female + "\",\"callback_data\": \"" + female + "\"},{\"text\":\"" + male + "\",\"callback_data\": \"" + male + "\"}]]}";

                if(!isMandatory()){
                    resString = resString.substring(0, resString.length() - 3);
                    resString += ",{\"text\":\"" + noAnswer + "\",\"callback_data\": \"No Answer\"}]]}";
                }

                break;
            case "Y":
                System.out.println("Yes/No");
                resString = "{\"text\":\"" + questionText + "\",\"inline_keyboard\": [[{\"text\":\"" + yes + "\",\"callback_data\": \"" + yes + "\"},{\"text\":\"" + no + "\",\"callback_data\": \"" + no + "\"}]]}";
                if(!isMandatory()){
                    resString = resString.substring(0, resString.length() - 3);
                    resString += ",{\"text\":\"" + noAnswer + "\",\"callback_data\": \"" + noAnswer + "\"}]]}";
                }
                break;
            case "5":
                System.out.println("5 point choice");
                resString = "{\"text\":\"" + questionText + "\",\"inline_keyboard\": [[";
                for(int i = 1; i <= 5; i++){
                    String add = String.valueOf(i);
                    if(buttonToColor.length() > 0) {
                        try {
                            if (Integer.parseInt(buttonToColor) == i) {
                                add = check + i;
                            }
                        } catch (Exception e) {
                            System.out.println("button not a five point number");
                        }
                    }
                    resString += "{\"text\":\"" + add + "\",\"callback_data\": \"" + add + "\"},";
                }
                if(!isMandatory()){
                    resString += "{\"text\":\"" + noAnswer + "\",\"callback_data\": \"" + noAnswer + "\"},";

                }
                // remove last comma
                resString = resString.substring(0, resString.length() - 1);
                resString += "]]}";
                break;

        }
        if(this.type.equals(qType.DICHOTOMOUS.toString()) ||
                (this.type.equals(qType.SCALE.toString())) ||
                (this.type.equals(qType.LISTRADIO.toString())) ||
                (this.type.equals(qType.SINGLECHOICECOMMENT.toString())) ||
                (this.type.equals(qType.LISTDROPDOWN.toString()))) {

            System.out.println("inside answeroptions with type dichotomous, scale or ! or o or l");

            String askForComment = "";
            if(this.type.equals(qType.SINGLECHOICECOMMENT.toString())){
                askForComment = " Please write a comment for your chosen option.";
            }

            resString = "{\"text\":\"" + questionText + askForComment + "\",\"inline_keyboard\": [";
            for(int i = 1; i < answerOptions.size() + 1; i++){
                String text = this.getAnswerOptionByIndex(i).getText();
                if(buttonToColor.length() > 0){
                    if(buttonToColor.equals(text))
                        text = check + text;
                }
                String currAnswerOption = "[{\"text\":\"" + text + "\",\"callback_data\": \"" + text + "\"}],";
                resString += currAnswerOption;
                index++;
            }

            if(!isMandatory()){
                if(this.languageIsGerman()){
                    resString += "[{\"text\":\"Keine Antwort\",\"callback_data\": \"Keine Antwort\"}]";
                } else{
                    resString += "[{\"text\":\"No Answer\",\"callback_data\": \"No Answer\"}]";
                }
            }
            else{
                // remove last comma after the options
                resString = resString.substring(0, resString.length() - 1);
            }

            resString += "]}";
            System.out.println("resstring: " + resString);
        }

        return resString;
    }

    private String handleTelegramMC(boolean newQuestionGroup, boolean edit, Participant participant, Integer arrayNumber, String resString, String questionText) {
        if (this.subquestionAl.size() > 0 && !this.type.equals(qType.ARRAY.toString())) {
            resString = "{\"text\":\"" + questionText + "\",\"inline_keyboard\": [";
            for (Question subq : this.subquestionAl) {
                resString += subq.encodeJsonBodyAsString(participant);
            }
            if(!edit){
                resString += "[{\"text\":\"" + SurveyHandlerService.texts.get("submitButton") + "\",\"callback_data\": \"" + SurveyHandlerService.texts.get("submitButton") + "\"}]";
            }
            resString += "]}";
        } else if(this.type.equals(qType.ARRAY.toString())){
            resString = "{\"text\":\"" + questionText;

            Question subq = this.getSubquestionByIndex(String.valueOf(arrayNumber));
            resString += subq.encodeJsonBodyAsString(newQuestionGroup, participant, arrayNumber);

            resString += "]}";
        }
        return resString;
    }

    private String completedQuestionGroup(boolean newQuestionGroup, Participant participant) {
        String questionText = this.text;
        int questionsLeft = this.questionsLeft(participant);
        String newQGroupText = "";
        if(questionsLeft > 1){
            if(this.languageIsGerman()){
                newQGroupText = "Du hast eine Fragegruppe abgeschlossen. Es gibt noch " + questionsLeft + " weitere Fragen.\n";
            } else{
                newQGroupText = "You completed a question group. There are " + questionsLeft + " questions left.\n";
            }
        }
        else{
            if(this.languageIsGerman()){
                newQGroupText = "Du hast eine Fragegruppe abgeschlossen. Es gibt noch " + questionsLeft + " weitere Frage.\n";
            } else{
                newQGroupText = "You completed a question group. There is " + questionsLeft + " question left.\n";
            }
        }

        if(newQuestionGroup){
            questionText = newQGroupText + questionText;
        }
        return questionText;
    }

    public String parseQuestionForSlack(boolean newQuestionGroup, boolean edit, String buttonToColor, Participant participant, Integer arrayNumber){
        StringBuilder resString = new StringBuilder();
        String subString = "";
        int index = 1;

        String questionText = completedQuestionGroup(newQuestionGroup, participant);


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
                resString = new StringBuilder("[\n" +
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
                        "\"options\": [");
                for (Question subq : this.subquestionAl) {
                    resString.append(subq.encodeJsonBodyAsString(participant));
                }
                // remove last comma after the options
                resString = new StringBuilder(resString.substring(0, resString.length() - 1));
                resString.append("],\n" + "\"action_id\": \"").append(this.qid).append("\"\n").append("}\n").append("]\n").append("}\n").append("]");

                return resString.toString();
            }


        } else{
            // Check if multiple choice question
            if (this.subquestionAl.size() > 0 && !this.type.equals(qType.ARRAY.toString())) {
                resString = new StringBuilder("[\n" +
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
                        "\"options\": [");
                for (Question subq : this.subquestionAl) {
                    resString.append(subq.encodeJsonBodyAsString(participant));
                }
                // remove last comma after the options
                resString = new StringBuilder(resString.substring(0, resString.length() - 1));
                resString.append("],\n" + "\"action_id\": \"").append(this.qid).append("\"\n").append("}\n").append("]\n").append("},\n").append("{\n").append("\"type\": \"actions\",\n").append("\"elements\": [\n").append("{\n").append("\"type\": \"button\",\n").append("\"text\": {\n").append("\"type\": \"plain_text\",\n").append("\"text\": \"").append(SurveyHandlerService.texts.get("submitButton")).append("\",\n").append("\"emoji\": true\n").append("},\n").append("\"value\": \"").append(SurveyHandlerService.texts.get("submitButton")).append("\",\n").append("\"action_id\": \"").append(this.qid).append("\"\n").append("}\n").append("]\n").append("}").append("]");
            } else if(this.type.equals(qType.ARRAY.toString())){
                resString = new StringBuilder("[\n" +
                        "{\n" +
                        "\"type\": \"section\",\n" +
                        "\"text\": {\n" +
                        "\"type\": \"plain_text\",\n" +
                        "\"text\": \"" + questionText + "\n");

                Question subq = this.getSubquestionByIndex(String.valueOf(arrayNumber));
                resString.append(subq.encodeJsonBodyAsString(newQuestionGroup, edit, buttonToColor, participant, arrayNumber));

                resString.append("\t\t\t]\n" + "\t\t}]}]");
            }

        }

        if(resString.length() == 0){
            resString.append(questionText);
        }

        if(this.isSubquestion && !this.getParentQuestion().answerOptions.isEmpty()){
            resString = new StringBuilder(subString + "\t\t{\n" +
                    "\t\t\t\"type\": \"actions\",\n" +
                    "\t\t\t\"elements\": [\n" +
                    "\t\t\t\t{\n" +
                    "\t\t\t\t\t\"type\": \"radio_buttons\",\n" +
                    "\t\t\t\t\t\"options\": [");
            for(int i = 1; i < this.getParentQuestion().answerOptions.size() + 1; i++){
                String currAnswerOption = "{\n" +
                        "\t\t\t\t\t\t\t\"text\": {\n" +
                        "\t\t\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\t\t\t\t\"text\": \"" + this.getParentQuestion().getAnswerOptionByIndex(i).getText() + "\",\n" +
                        "\t\t\t\t\t\t\t\t\"emoji\": true\n" +
                        "\t\t\t\t\t\t\t},\n" +
                        "\t\t\t\t\t\t\t\"value\": \"" + index + "\"\n" +
                        "\t\t\t\t\t\t},";

                resString.append(currAnswerOption);
                index++;

            }
            // remove last comma after the options
            resString = new StringBuilder(resString.substring(0, resString.length() - 1));
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
                case "1", "Female", "Yes", "Ja":
                    firstAdd = add;
                    break;
                case "2", "Male", "No", "Nein":
                    secondAdd = add;
                    break;
                case "3", "No Answer", "Keine Antwort":
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
                resString.append(" Please enter a date in the format dd.mm.jjjj.");
                break;
            case "|":
                System.out.println("File upload");
                resString.append(" Please send a file.");
                break;
            case "G":
                System.out.println("Gender");
                resString = new StringBuilder("[{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                        "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Female\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(firstAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Male\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(secondAdd).append("\t\t\t\t},\n");
                if(!isMandatory()){
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"No Answer\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(thirdAdd).append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}]");
                }
                if(languageIsGerman()){
                    resString = new StringBuilder("[{\n" +
                            "\t\t\t\"type\": \"section\",\n" +
                            "\t\t\t\"text\": {\n" +
                            "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                            "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                            "\t\t\t}\n" +
                            "\t\t},\n" +
                            "\t\t{\n" +
                            "\t\t\t\"type\": \"actions\",\n" +
                            "\t\t\t\"elements\": [\n");
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Weiblich\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(firstAdd).append("\t\t\t\t},\n");
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Maennlich\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(secondAdd).append("\t\t\t\t},\n");
                    if(!isMandatory()){
                        resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Keine Antwort\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(thirdAdd).append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}]");
                    }
                }
                break;
            case "N":
                System.out.println("Numerical input");
                resString.append(" Please respond with a number.");
                break;
            case "X":
                System.out.println("Text display");
                break;
            case "Y":
                System.out.println("Yes/No");
                resString = new StringBuilder("[{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                        "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Yes\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(firstAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"No\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(secondAdd).append("\t\t\t\t},\n");
                if(!isMandatory()){
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"No Answer\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(thirdAdd).append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}]");
                }
                if(this.languageIsGerman()){
                    resString = new StringBuilder("[{\n" +
                            "\t\t\t\"type\": \"section\",\n" +
                            "\t\t\t\"text\": {\n" +
                            "\t\t\t\t\"type\": \"mrkdwn\",\n" +
                            "\t\t\t\t\"text\": \"" + questionText + "\"\n" +
                            "\t\t\t}\n" +
                            "\t\t},\n" +
                            "\t\t{\n" +
                            "\t\t\t\"type\": \"actions\",\n" +
                            "\t\t\t\"elements\": [\n");
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Ja\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(firstAdd).append("\t\t\t\t},\n");
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Nein\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(secondAdd).append("\t\t\t\t},\n");
                    resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"Keine Antwort\",\n" + "\t\t\t\t\t\t\"emoji\": true\n" + "\t\t\t\t\t}\n").append(thirdAdd).append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}]");
                }
                break;
            case "5":
                System.out.println("5 point choice");
                resString = new StringBuilder("[\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"section\",\n" +
                        "\t\t\t\"text\": {\n" +
                        "\t\t\t\t\"type\": \"plain_text\",\n" +
                        "\t\t\t\t\"text\":\"" + questionText + "\"\n" +
                        "\t\t\t}\n" +
                        "\t\t},\n" +
                        "\t\t{\n" +
                        "\t\t\t\"type\": \"actions\",\n" +
                        "\t\t\t\"elements\": [\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"1\"\n" + "\t\t\t\t\t}\n").append(firstAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"2\"\n" + "\t\t\t\t\t}\n").append(secondAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"3\"\n" + "\t\t\t\t\t}\n").append(thirdAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"4\"\n" + "\t\t\t\t\t}\n").append(fourthAdd).append("\t\t\t\t},\n");
                resString.append("\t\t\t\t{\n" + "\t\t\t\t\t\"type\": \"button\",\n" + "\t\t\t\t\t\"text\": {\n" + "\t\t\t\t\t\t\"type\": \"plain_text\",\n" + "\t\t\t\t\t\t\"text\": \"5\"\n" + "\t\t\t\t\t}\n").append(fifthAdd).append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}\n").append("\t]");
                break;

        }
        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.DICHOTOMOUS.toString())) ||
                (!(this.answerOptions.isEmpty()) && this.type.equals(qType.SCALE.toString()))) {

            resString = new StringBuilder("[\n" +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"section\",\n" +
                    "\t\t\t\"text\": {\n" +
                    "\t\t\t\t\"type\": \"plain_text\",\n" +
                    "\t\t\t\t\"text\":\"" + questionText + "\"\n" +
                    "\t\t\t}\n" +
                    "\t\t},\n" +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"actions\",\n" +
                    "\t\t\t\"elements\": [\n");
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
                    resString.append(currAnswerOption);
                    index++;
                } else{
                    String currAnswerOption = "\t\t\t\t{\n" +
                            "\t\t\t\t\t\"type\": \"button\",\n" +
                            "\t\t\t\t\t\"text\": {\n" +
                            "\t\t\t\t\t\t\"type\": \"plain_text\",\n" +
                            "\t\t\t\t\t\t\"text\": \"" + getAnswerOptionByIndex(i).getText() + "\",\n" +
                            "\t\t\t\t\t}\n" +
                            "\t\t\t\t},\n";

                    resString.append(currAnswerOption);
                    index++;
                }

            }
            // remove last comma after the options
            resString = new StringBuilder(resString.substring(0, resString.length() - 1));
            resString.append("\t\t\t]\n" + "\t\t}\n" + "\t]");
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
            resString = new StringBuilder("[\n" +
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
                    "\t\t\t\t\t\"options\": [");
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
                    resString.append(currAnswerOption);
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

                    resString.append(currAnswerOption);
                    index++;
                }

            }
            // remove last comma after the options
            resString = new StringBuilder(resString.substring(0, resString.length() - 1));
            resString.append("\t\t\t\t\t]," + "\"action_id\": \"").append(this.qid).append("\"\n").append("\t\t\t\t}\n").append("\t\t\t]\n").append("\t\t}\n").append("\t]");
        }

        /*
        if(!this.isSubquestion){
            if(this.help.length() > 0){
                resString += "\n This is the help: " + this.help + "";
            }
        }

         */
        return resString.toString();
    }

    public String parseQuestionAsText(boolean newQuestionGroup, Participant participant, Integer arrayNumber){
        String resString = "";
        String subString = "";
        int index = 1;

        String exp = " Please choose one of the following options by sending the respective number as a response: \n";
        if(this.languageIsGerman()){
            exp = " Bitte waehle eine der folgenden Optionen, indem du die entsprechende Nummer als Antwort sendest: \n";
        }

        String questionText = this.text;
        int questionsLeft = this.questionsLeft(participant);

        String newQGroupText;
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
                resString = handleTextMc(resString,
                        " Bitte waehle eine der folgenden Optionen, indem du die entsprechende Nummer als Antwort sendest sowie einen Kommentar zu deiner ausgewaehlten Option. Bitte im Format \"Nummer der ausgewaehlten Option\":\"Dein Kommentar zur ausgewaehlten Option\". Bitte benutze kein : in deiner Antwort. Wenn du mehr als eine Anwort auswaehlst antworte bitte in folgendem Format \"Nummer der ausgewaehlten Option\":\"Dein Kommentar zur ausgewaehlten Option\";\"Nummer der zweiten ausgewaehlten Option\":\"Dein Kommentar zur zweiten ausgewaehlten Option\" und so weiter.",
                        " Please choose from the following options by sending the respective number as a response as well as a comment for your chosen option in the format \"number of your chosen option\":\"your comment\" and do not use : in your answer. If you choose more than one, please answer in the format \"number of your chosen option\":\"your comment\";\"number of your second chosen option\":\"your second comment\" and so on.");

            } else{
                // no comment required
                resString = handleTextMc(resString,
                        " Bitte waehle eine der folgenden Optionen, indem du die entsprechende Nummer als Antwort sendest. Wenn du mehr als eine Antwort auswaehlst separiere die Nummern bitte mit einem Komma und keinem Leerzeichen.",
                        " Please choose from the following options by sending the respective number as a response. If you choose more than one, please separate the numbers with a comma and no space.");
            }
            for (Question subq : this.subquestionAl) {
                resString += "\n" + index + ". " + subq.encodeJsonBodyAsString(participant);
                index++;
            }

        } else if(this.subquestionAl.size() > 0 && !this.answerOptions.isEmpty() && this.type.equals(qType.ARRAY.toString())){
            resString = handleTextArrayType(participant, arrayNumber, resString, exp);
        }


        switch(this.type){
            case "D":
                resString += " Please enter a date in the format dd.mm.jjjj.";
                break;
            case "|":
                resString += " Please send a file.";
                break;
            case "G":
                if(languageIsGerman()){
                    resString += exp;
                    resString += " 1. Weiblich ";
                    resString += " 2. Maennlich  ";
                    if(!isMandatory()){
                        resString += " 3. Keine Antwort ";
                    }
                }
                else{
                    resString += exp;
                    resString += " 1. Female ";
                    resString += " 2. Male ";
                    if(!isMandatory()){
                        resString += " 3. No Answer ";
                    }
                }
                break;
            case "N":
                resString += " Please respond with a number.";
                break;
            case "X":
                break;
            case "Y":
                if(languageIsGerman()){
                    resString += exp;
                    resString += " 1. Ja ";
                    resString += " 2. Nein ";
                    if(!isMandatory()){
                        resString += " 3. Keine Antwort ";
                    }
                }
                else{
                    resString += exp;
                    resString += " 1. Yes ";
                    resString += " 2. No ";
                    if(!isMandatory()){
                        resString += " 3. No Answer ";
                    }
                }
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

            resString += exp;
            for(int i = 1; i < answerOptions.size() + 1; i++){
                resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
                index++;
            }
        }

        if((!(this.answerOptions.isEmpty()) && this.type.equals(qType.SINGLECHOICECOMMENT.toString()))){
            resString = handleTextSingleChoice(resString, index);
        }

        if(!this.isSubquestion && this.help != null){
            if(this.help.length() > 0){
                resString += "\n This is the help: " + this.help + "";
            }
        }
        return resString;
    }

    @NotNull
    private String handleTextSingleChoice(String resString, int index) {
        if(this.languageIsGerman()){
            resString += " Bitte waehle eine der folgenden Optionen, indem du die entsprechende Nummer als Antwort sendest sowie einen Kommentar zu deiner ausgewaehlten Option. Bitte im Format \"Nummer der ausgewaehlten Option\":\"Dein Kommentar zur ausgewaehlten Option\". Bitte benutze kein : in deiner Antwort.";
        }
        resString += " Please choose one of the following options by sending the respective number as a response as well as a comment for your chosen option in the format \"number of your chosen answer option\":\"your comment\": \n";
        for(int i = 1; i < answerOptions.size() + 1; i++){
            resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
            index++;
        }
        return resString;
    }

    @NotNull
    private String handleTextMc(String resString, String german, String english) {
        if(this.languageIsGerman()){
            resString += german;
            if(!mandatory){
                resString += " Wenn du keine Antwort auswaehlen willst, sende bitte \"-\".";
            }
        } else{
            resString += english;
            if(!mandatory){
                resString += " If you want to choose no option, please enter \"-\".";
            }
        }
        return resString;
    }

    @NotNull
    private String handleTextArrayType(Participant participant, Integer arrayNumber, String resString, String exp) {
        Integer one = 1;
        Question subq = this.subquestionAl.get(arrayNumber - one);
        resString += exp + subq.encodeJsonBodyAsString(participant) + "\n";

        for(int i = 1; i < answerOptions.size() + 1; i++){
            resString += " " + i + ". " + getAnswerOptionByIndex(i).getText() + "\n";
        }
        return resString;
    }

    public Question getSubquestionByIndex(String index){
        return this.subquestionAl.get(Integer.parseInt(index) -1);
    }

    // gets answer object text and creates string of format ""sidXgidXqidXsqid":"answertext"". This format can be used for limesurvey communication directly
    public String createLimeAnswerString(Answer answer){
        boolean hasComment = false;
        if(answer.getComment().length() > 0){
            hasComment = true;
        }
        String answerKey = this.createAnswerKey(this.isSubquestion, this.code, false);
        String answerText = answer.getText();
        if(answerText.contains("\"") || answerText.contains("\n")){
            answerText = answerText.replaceAll("\"", "'");
            answerText = answerText.replaceAll("\n", " ");
        }
        String returnValue = "\"" + answerKey + "\":\"" + answerText + "\",";
        if(hasComment){
            // if the answer has a comment, get the comment answer
            answerKey = this.createAnswerKey(this.isSubquestion, this.code, hasComment);
            String commentText = answer.getComment();
            if(commentText.contains("\"") || commentText.contains("\n")){
                commentText = commentText.replaceAll("\"", "'");
                commentText = commentText.replaceAll("\n", " ");
            }
            returnValue += "\"" + answerKey + "\":\"" + commentText + "\",";
        }

        return returnValue;
    }

    public String createMobsosAnswerString(Answer answer){
        String answerKey = this.qid;
        String answerText = answer.getText();
        if(answerText.contains("\"") || answerText.contains("\n")){
            answerText = answerText.replaceAll("\"", "'");
            answerText = answerText.replaceAll("\n", " ");
        }
        String returnValue = "\"" + answerKey + "\":\"" + answerText + "\",";
        return returnValue;
    }

    private String createAnswerKey (boolean isSubquestion, String code, boolean comment){
        String separator = "X";
        String returnValue;
        if(isSubquestion){
            returnValue = this.sid + separator + this.gid + separator + this.parentqid + code;
        }
        else{
            returnValue = this.sid + separator + this.gid + separator + this.qid;
        }

        if(comment){
            returnValue += "comment";
        }
        return returnValue;
    }

    public boolean answerIsPlausible(String textAnswer, String check){

        if(SurveyHandlerService.messenger.equals(Messenger.SLACK) ||
                SurveyHandlerService.messenger.equals(Messenger.TELEGRAM)){
            if (isSinglechoiceSlackPlausible(textAnswer)) return true;

            if(!this.subquestionAl.isEmpty()){
                // If it a mulitple choice question, check if textAnswer equals one answer option (which is saves as text from subquestion)
                for(Question q : this.subquestionAl){
                    if(q.answerIsPlausible(textAnswer, check)){
                        return true;
                    }
                }
            }

            if(this.isSubquestion){
                // if it is an answer to a mulitple choice question answer option, it is exactly that subquestion text
                return isSubquestionSlackPlausible(textAnswer, check);
            }

            if(this.type.equals(qType.GENDER.toString())){
                if (isGenderSlackPlausible(textAnswer)) return true;
            }

            if(this.type.equals(qType.YESNO.toString())){
                // yes no question has only these three answers
                if (isYesNoSlackPlausible(textAnswer)) return true;
            }
        }
        else{
            // rocket chat
            if(this.type.equals(qType.LISTRADIO.toString()) || this.type.equals(qType.LISTDROPDOWN.toString()) ||
                    this.type.equals(qType.DICHOTOMOUS.toString()) || this.type.equals(qType.SCALE.toString())){
                // for these types, a answeroptionslist is available, only answers equal to one of these options is ok
                int size = this.answerOptions.size();
                try{
                    if(0 < Integer.parseInt(textAnswer) && Integer.parseInt(textAnswer) < size + 1){
                        return true;
                    }
                } catch(Exception e){
                    return false;
                }

            }

            if(this.type.equals(qType.GENDER.toString()) || this.type.equals(qType.YESNO.toString())){
                try{
                    if(0 < Integer.parseInt(textAnswer) && Integer.parseInt(textAnswer) < 4){
                        return true;
                    }
                } catch(Exception e){
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
                    return false;
                }

            }

            if(this.type.equals(qType.SINGLECHOICECOMMENT.toString())){
                try{
                    String chosen = textAnswer.split(":")[0];
                    int size = this.answerOptions.size();
                    if(0 < Integer.parseInt(chosen) && Integer.parseInt(chosen) < size + 1){
                        return true;
                    }
                } catch(Exception e){
                    return false;
                }

            }

            if(this.type.equals(qType.MULTIPLECHOICENOCOMMENT.toString())){
                try{
                    if (!textAnswer.equals("-")) {
                        String[] chosen = textAnswer.split(",");
                        int size = this.subquestionAl.size();
                        for (String s : chosen) {
                            if (!(0 < Integer.parseInt(s) && Integer.parseInt(s) < size + 1)) {
                                return false;
                            }
                        }
                    }
                    return true;
                } catch(Exception e){
                    return false;
                }


            }

            if(this.type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                try{
                    return isTextMCCommentPlausible(textAnswer);
                } catch(Exception e){
                    return false;
                }
            }
        }


        if(this.type.equals(qType.SHORTFREETEXT.toString())){
            if(textAnswer.length() < qType.SHORTFREETEXT.getMaxLength()){
                return true;
            }
        }

        if(this.type.equals(qType.LONGFREETEXT.toString())){
            if(textAnswer.length() < qType.LONGFREETEXT.getMaxLength()){
                return true;
            }
        }

        if(this.type.equals(qType.HUGEFREETEXT.toString())){
            if(textAnswer.length() < qType.HUGEFREETEXT.getMaxLength()){
                return true;
            }
        }

        if(this.type.equals(qType.FIVESCALE.toString())){
            try{
                if(!this.mandatory && (textAnswer.equals("Keine Antwort") || textAnswer.equals("No Answer"))){
                    return true;
                }
                int var = Integer.parseInt(textAnswer);
                if(var < 6 && 0 < var){
                    return true;
                }
            } catch(Exception e){
                return false;
            }
        }

        if(this.type.equals(qType.NUMERICALINPUT.toString())){
            try{
                int var = Integer.parseInt(textAnswer);
                return true;
            } catch(Exception e){
                return false;
            }
        }

        if(this.type.equals(qType.DATETIME.toString())){
            try{
                DateFormat sourceFormat = new SimpleDateFormat("dd.MM.yyyy");
                sourceFormat.parse(textAnswer);
                return true;
            } catch(Exception e){
                return false;
            }
        }

        boolean ok = false;
        if(this.getSurvey().hasMoreThanOneLanguage()){
            String otherLanguage = getSurvey().getOtherLanguage(this.language);
            ok = this.getSurvey().getQuestionByQid(this.qid, otherLanguage).answerIsPlausible(textAnswer, check);
        }
        return ok;
    }

    private boolean isTextMCCommentPlausible(String textAnswer) {
        if (!textAnswer.equals("-")) {
            String[] all = textAnswer.split(";");
            ArrayList<String> chosen = new ArrayList<>();
            ArrayList<String> comments = new ArrayList<>();
            int size = this.subquestionAl.size();
            for (String s : all) {
                chosen.add(s.split(":")[0]);
                comments.add(s.split(":")[1]);
            }
            for (String s : chosen) {
                if (!(0 < Integer.parseInt(s) && Integer.parseInt(s) < size + 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isYesNoSlackPlausible(String textAnswer) {
        if(textAnswer.equals("Yes") || textAnswer.equals("No") || (!this.mandatory && textAnswer.equals("No Answer")) ||
                textAnswer.equals("Ja") || textAnswer.equals("Nein") || (!this.mandatory && textAnswer.equals("Keine Antwort"))){
            return true;
        }
        return false;
    }

    private boolean isGenderSlackPlausible(String textAnswer) {
        // a gender question only has these three options
        if(textAnswer.equals("Female") || textAnswer.equals("Male") || (!this.mandatory && textAnswer.equals("No Answer"))
        || textAnswer.equals("Weiblich") || textAnswer.equals("Maennlich") || (!this.mandatory && textAnswer.equals("Keine Antwort"))){
            return true;
        }
        return false;
    }

    private boolean isSubquestionSlackPlausible(String textAnswer, String check) {
        if(this.text.equals(textAnswer) || textAnswer.equals(check + this.text)){
            return true;
        }
        return false;
    }

    private boolean isSinglechoiceSlackPlausible(String textAnswer) {
        if(this.type.equals(qType.SINGLECHOICECOMMENT.toString()) || this.type.equals(qType.LISTRADIO.toString()) || this.type.equals(qType.LISTDROPDOWN.toString()) ||
                this.type.equals(qType.DICHOTOMOUS.toString()) || this.type.equals(qType.SCALE.toString()) ||
                this.type.equals(qType.ARRAY.toString())){
            if(!this.mandatory && (textAnswer.equals("Keine Antwort") || textAnswer.equals("No Answer"))){
                return true;
            }
            for(AnswerOption ao : answerOptions){
                if(ao.getText().equals(textAnswer)){
                    return true;
                }
            }
        }
        return false;
    }

    public String reasonAnswerNotPlausible(){

        String type = this.type;
        String reason = "";

        if(SurveyHandlerService.messenger.equals(Messenger.SLACK) ||
                SurveyHandlerService.messenger.equals(Messenger.TELEGRAM)){
            if(type.equals(qType.LISTDROPDOWN.toString()) ||
                    type.equals(qType.LISTRADIO.toString()) ||
                    type.equals(qType.DICHOTOMOUS.toString()) ||
                    type.equals(qType.SCALE.toString()) ||
                    type.equals(qType.GENDER.toString()) ||
                    type.equals(qType.YESNO.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonButtonDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonButton");
                }
            }

            if(type.equals(qType.ARRAY.toString()) ||
                    type.equals(qType.MULTIPLECHOICENOCOMMENT.toString()) ||
                    type.equals(qType.SINGLECHOICECOMMENT.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonButtonDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonButton");
                }
            }

            if(type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesCommentDE").replaceAll("\\{submitButton\\}", SurveyHandlerService.texts.get("submitButton"));
                } else{
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesComment").replaceAll("\\{submitButton\\}", SurveyHandlerService.texts.get("submitButton"));
                }
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
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonButtonDefaultDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonButtonDefault");
                }
            }

            if(type.equals(qType.SINGLECHOICECOMMENT.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonListCommentDefaultDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonListCommentDefault");
                }

            }

            if(type.equals(qType.MULTIPLECHOICENOCOMMENT.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesNoCommentDefaultDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesNoCommentDefault");
                }

            }

            if(type.equals(qType.MULTIPLECHOICEWITHCOMMENT.toString())){
                if(this.languageIsGerman()){
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesCommentDefaultDE");
                } else{
                    reason = SurveyHandlerService.texts.get("reasonCheckboxesCommentDefault");

                }
            }

        }

        if(type.equals(qType.SHORTFREETEXT.toString()) ||
           type.equals(qType.HUGEFREETEXT.toString()) ||
           type.equals(qType.LONGFREETEXT.toString())){
            if(this.languageIsGerman()){
                reason = SurveyHandlerService.texts.get("reasonTextDE");
            } else{
                reason = SurveyHandlerService.texts.get("reasonText");

            }
        }

        if(type.equals(qType.DATETIME.toString())){
            if(this.languageIsGerman()){
                reason = SurveyHandlerService.texts.get("reasonDateDE");
            } else{
                reason = SurveyHandlerService.texts.get("reasonDate");
            }
        }

        if(type.equals(qType.FIVESCALE.toString())){
            if(this.languageIsGerman()){
                reason = SurveyHandlerService.texts.get("reasonFiveScaleDE");
            } else{
                reason = SurveyHandlerService.texts.get("reasonFiveScale");
            }
        }

        if(type.equals(qType.NUMERICALINPUT.toString())){
            if(this.languageIsGerman()){
                reason = SurveyHandlerService.texts.get("reasonNumber");
            } else{
                reason = SurveyHandlerService.texts.get("reasonNumberDE");
            }
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
            return false;
        } else{
            return true;
        }
    }

    public boolean isTextQuestion(){
        if(this.type.equals(qType.SHORTFREETEXT.toString()) ||
                this.type.equals(qType.LONGFREETEXT.toString()) ||
                this.type.equals(qType.HUGEFREETEXT.toString())){
            return true;
        } else{
            return false;
        }
    }

    public ArrayList<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public void setAnswerOption(AnswerOption answerOption){
        this.answerOptions.add(answerOption);
    }

    public AnswerOption getAnswerOptionByIndex(Integer index){
        for(AnswerOption ao : answerOptions){
            System.out.println("index: " + ao.getIndexi() + "..." + index);
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

    public boolean isRelevant(Participant p){
        boolean reqMet = true;
        if(this.relevance.length() > 1){
            String[] checks = this.relevance.split("or");

            for(String toCheck : checks){
                toCheck = toCheck.replaceAll(" ", "");
                toCheck = toCheck.replaceAll("\"","");

                ArrayList<String> relCode = getRelevanceReqAndCode(toCheck, p);

                Question q = getQuestionForRelevanceCode(relCode.get(0));

                if(q == null){
                    return true;
                }

                reqMet = reqMet(relCode.get(1), q, p.getGivenAnswersAl());

                if(!reqMet){
                    return reqMet;
                }
            }

        }
        return reqMet;

    }

    private boolean reqMet(String req, Question q, ArrayList<Answer> answers){
        for(Answer a : answers){
            if(a.getQid().equals(q.getQid())){
                if(a.getText().equals(req)){
                    return true;
                }
            }
        }

        return false;
    }

    private Question getQuestionForRelevanceCode(String code){
        if(code.contains("NAOK")){
            // in form '((227314X480X4801.NAOK == "A2"))'
            String codeQid = code.split("X")[2];
            codeQid = codeQid.replaceAll(".NAOK", "");
            //codeQid = codeQid.split(".")[0];
            return this.getSurvey().getQuestionByQid(codeQid, this.language);
        }
        else{
            //check if answer for code matches requirement
            return this.getSurvey().getQuestionByCode(code, this.language);

        }
    }

    private ArrayList<String> getRelevanceReqAndCode(String string, Participant p){
        // separate into parts
        String code = string.split("==")[0];

        String requirement = string.split("==")[1];

        ArrayList<String> ret = new ArrayList<>();
        ret.add(code);
        ret.add(requirement);

        return ret;
    }

    public int questionsLeft(Participant participant){
        // +1 because the question that is about to be sent is already removed from the list
        int questionsLeft = participant.getUnaskedQuestions().size() + 1;
        // array question should not count as one, but count of subquestion is number of questions
        for(String questionQIDString : participant.getUnaskedQuestions()){
            Question q = this.getSurvey().getQuestionByQid(questionQIDString, this.language);
            if(q.getType().equals(qType.ARRAY.toString())){
                // -1 since parent question does not count as singular question
                questionsLeft += q.getSubquestionAl().size() - 1;
            }
        }

        // if first question is array question, it is on the list even when part has been asked
        if(!participant.getUnaskedQuestions().isEmpty()){
            if(this.qid.equals(participant.getUnaskedQuestions().get(0))){
                questionsLeft--;
            }
        }

        return questionsLeft;
    }
}
