package i5.las2peer.services.SurveyHandler;
import i5.las2peer.services.SurveyHandler.database.SQLDatabase;
import i5.las2peer.services.SurveyHandler.database.SurveyHandlerServiceQueries;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;


public class Survey {

    // Database model identifier
    private String sid;
    private String adminmail;
    private String expires;
    private String startDT;
    private String title;
    private String adminLanguage;
    private String welcomeText;

    private String titleOtherLanguage;
    private String welcomeTextOtherLanguage;
    SQLDatabase database;
    // end Database model identifier

    // sorted by language, hashmap contains ALL questions (sub and parent questions)
    private HashMap<String, HashMap<String, Question>> questionsHMLanguage = new HashMap<>();
    // sorted by language, questionAL only contains parent questions, should be sorted
    private HashMap<String, ArrayList<Question>> questionALLanguage = new HashMap<>();

    private ArrayList<Participant> participants = new ArrayList<>();

    private ArrayList<Admin> admins = new ArrayList<>();

    public String getAdminmail() {
        return adminmail;
    }

    public void setAdminmail(String adminmail) {
        this.adminmail = adminmail;
    }

    public SQLDatabase getDatabase() {
        return database;
    }

    public void setDatabase(SQLDatabase database) {
        this.database = database;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWelcomeText() {
        return welcomeText;
    }

    public void setWelcomeText(String welcomeText) {
        this.welcomeText = welcomeText;
    }

    public String getTitleOtherLanguage() {
        return titleOtherLanguage;
    }

    public void setTitleOtherLanguage(String titleOtherLanguage) {
        this.titleOtherLanguage = titleOtherLanguage;
    }

    public String getWelcomeTextOtherLanguage() {
        return welcomeTextOtherLanguage;
    }

    public void setWelcomeTextOtherLanguage(String welcomeTextOtherLanguage) {
        this.welcomeTextOtherLanguage = welcomeTextOtherLanguage;
    }

    public Survey(String sid) {
        this.sid = sid;
    }

    public HashMap<String, HashMap<String, Question>> getQuestionsHMLanguage() {
        return questionsHMLanguage;
    }

    public ArrayList<Question> getQuestionAL(String language) {
        return questionALLanguage.get(language);
    }

    public HashMap<String, ArrayList<Question>> getQuestionALLanguage() {
        return questionALLanguage;
    }

    public void setQuestionHMLanguage(HashMap<String, HashMap<String, Question>> questionsHMLanguage) {
        this.questionsHMLanguage = questionsHMLanguage;
    }

    public void setParticipants(ArrayList<Participant> participants) {
        this.participants = participants;
    }

    // Initialize data structues when given a JSONArray from limesurvey
    public void initLimeSurveyData(JSONArray allQuestions){
        ArrayList<Question> tempQuestionAl = new ArrayList<>();
        ArrayList<String> languages = new ArrayList<>();
        HashMap<String, Question> qHM = new HashMap<>();
        ArrayList<Question> qAL = new ArrayList<>();

        // Add questions to survey
        for (Object jo : allQuestions) {
            JSONObject j = (JSONObject) jo;
            try {
                Question newQuestion = new Question();
                newQuestion.initLimeSurveyData(j);

                if(this.questionsHMLanguage.get(newQuestion.getLanguage()) == null){
                    this.questionsHMLanguage.put(newQuestion.getLanguage(), new HashMap<>());
                }

                qHM = this.questionsHMLanguage.get(newQuestion.getLanguage());
                qHM.put(newQuestion.getQid(), newQuestion);

                if(!languages.contains(newQuestion.getLanguage())){
                    System.out.println("adding lang: " + newQuestion.getLanguage());
                    languages.add(newQuestion.getLanguage());
                }
                if (newQuestion.isSubquestion()) {
                    // put subquestions into temp list to handle later
                    tempQuestionAl.add(newQuestion);
                } else {
                    // put non-subquestions into arraylist
                    if(this.questionALLanguage.get(newQuestion.getLanguage()) == null){
                        this.questionALLanguage.put(newQuestion.getLanguage(), new ArrayList<>());
                    }
                    qAL = this.getQuestionAL(newQuestion.getLanguage());
                    qAL.add(newQuestion);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to parse a question from json.");
            }
        }

        // add questions in corresponding language storage
        for(String currLanguage : languages){

            ArrayList<Question> allQs = new ArrayList<>();
            for(Question cq : this.questionALLanguage.get(currLanguage)){
                allQs.add(cq);
            }
            this.questionALLanguage.put(currLanguage, allQs);
            //System.out.println("v2: " + this.questionALLanguage.get(this.questionAL.get(0).getLanguage()).size());

            // handle all subquestions and assign them to their parent question, no sub-sub questions possible, so ignore the possibility
            for(Question q : tempQuestionAl){
                //System.out.println("sizi: " + tempQuestionAl.size());
                if(q.getLanguage().equals(currLanguage)){
                    String questionParent = q.getParentQid();
                    Question parentQuestion = this.questionsHMLanguage.get(currLanguage).get(questionParent);
                    //System.out.println("pq: " + parentQuestion.getQid() + " . " + parentQuestion.getLanguage() + "." + q.getLanguage() + "." +  currLanguage);
                    parentQuestion.addSubquestion(q);
                }
            }

            for(Question q : this.questionALLanguage.get(currLanguage)){
                this.sortQuestionsAl(q.getSubquestionAl());
            }

            System.out.println("before sorting...");
            System.out.println(this.getSortedQuestionIds(currLanguage).toString());
            // Create correct question order
            this.sortQuestionsAl(this.questionALLanguage.get(currLanguage));
            System.out.println("after sorting...");
            System.out.println(this.getSortedQuestionIds(currLanguage).toString());

        }
    }

    // Initialize data structues when given a JSONArray from mobsos surveys
    public void initMobsosData(JSONArray allQuestions){
        ArrayList<Question> tempQuestionAl = new ArrayList<>();
        HashMap<String, Question> qHM = new HashMap<>();
        ArrayList<Question> qAL = new ArrayList<>();
        // Add questions to survey
        for (Object jo : allQuestions) {
            JSONObject j = (JSONObject) jo;
            try {
                Question newQuestion = new Question();
                newQuestion.initMobsosData(j);
                // put all questions into hashmap
                if(this.questionsHMLanguage.get(newQuestion.getLanguage()) == null){
                    this.questionsHMLanguage.put(newQuestion.getLanguage(), new HashMap<>());
                }
                if(this.questionALLanguage.get(newQuestion.getLanguage()) == null){
                    this.questionALLanguage.put(newQuestion.getLanguage(), new ArrayList<>());
                }
                qHM = this.questionsHMLanguage.get(newQuestion.getLanguage());
                qHM.put(newQuestion.getQid(), newQuestion);

                // put non-subquestions into arraylist (they are all non subquestions)
                qAL = this.questionALLanguage.get(newQuestion.getLanguage());
                qAL.add(newQuestion);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to parse a question from json.");
            }
        }

    }

    // Initialize data structues when given an ArrayList from database
    public void initQuestionsFromDB(ArrayList<Question> QuestionAl){
        ArrayList<Question> noSubQuestionAl = new ArrayList<>();
        HashMap<String, ArrayList<Question>> hashNoSubQAL = new HashMap<>();
        ArrayList<Question> subQuestionAl = new ArrayList<>();
        HashMap<String, Question> questionsHM = new HashMap<>();
        ArrayList<String> languages = new ArrayList<>();

        for (Question teQ : QuestionAl) {
            if(this.questionsHMLanguage.get(teQ.getLanguage()) == null){
                this.questionsHMLanguage.put(teQ.getLanguage(), new HashMap<>());
            }

            questionsHM = this.questionsHMLanguage.get(teQ.getLanguage());
            questionsHM.put(teQ.getQid(), teQ);

            if(!languages.contains(teQ.getLanguage())){
                System.out.println("adding lang: " + teQ.getLanguage());
                languages.add(teQ.getLanguage());
            }

            if(teQ.isSubquestion()){
                subQuestionAl.add(teQ);
                continue;
            }

            if(this.questionALLanguage.get(teQ.getLanguage()) == null){
                this.questionALLanguage.put(teQ.getLanguage(), new ArrayList<>());
            }

            noSubQuestionAl = this.questionALLanguage.get(teQ.getLanguage());
            noSubQuestionAl.add(teQ);

            // init answeroptions
            ArrayList<AnswerOption> answerOptions = SurveyHandlerServiceQueries.getAnswerOptionsFromDB(this.sid, teQ.getQid(), teQ.getLanguage(), database);
            initAnswerOptionsFromDB(teQ, answerOptions);
        }

        // iterate through subquestions and add to correct question object in subquestion list
        for(Question tempQ : subQuestionAl) {
            // get parent question, this should exist because we parsed all (parent) questions before
            Question parentQ = this.getQuestionByQid(tempQ.getParentQid(), tempQ.getLanguage());
            parentQ.addSubquestion(tempQ);
        }

        // sort all questions
        for(String s : languages){
            this.sortQuestionsAl(this.getQuestionAL(s));
            this.sortSubquestions(s);
        }
    }

    public void initParticipantsFromDB(ArrayList<Participant> ParticipantAl){
        for (Participant p : ParticipantAl){
            p.setCurrentSurvey(this);
            this.participants.add(p);
        }
    }


    public void initAdminsFromDB(ArrayList<Admin> AdminAL){
        for(Admin a : AdminAL){
            this.admins.add(a);
        }
    }

    public void initAnswerOptionsFromDB(Question q, ArrayList<AnswerOption> answerOptionsFromDB){
        ArrayList<AnswerOption> answerOptions = new ArrayList<>();

        for(AnswerOption ao: answerOptionsFromDB){
            q.setAnswerOption(ao);
        }
    }

    public void initAnswersForParticipantFromDB(Participant p, ArrayList<Answer> answerAlFromDB){
        // get all possible questions ( these should be ordered already)
        ArrayList<Question> possibleQs = this.getQuestionAL(this.getLanguages().get(0));
        if(p.getLanguage() != null){
            if(p.getLanguage().length() > 0){
                possibleQs = this.getQuestionAL(p.getLanguage());
            }
        }
        // calculate which questions are still left unanswered
        ArrayList<Question> unaskedQuestions = new ArrayList<>();


        // Add answers directly, then compute unasked questions
        for (Answer a: answerAlFromDB){
            System.out.println("Adding answer "+ a + " to participant "+ p);
            p.addAnswerFromDb(a);
        }
        // Compare to be asked questions for this survey to given answers, to find unanswered questions
        for (Question tempQ : possibleQs){
            boolean answered = false;
            for (Answer tempA : answerAlFromDB){
                Question questionToAnswer = this.getQuestionsHMLanguage().get(tempQ.getLanguage()).get(tempA.getQid());
                // check if the question is a subquestion
                String correspondingQid;
                if (questionToAnswer.isSubquestion()){
                    // we always compare with parentQids
                    correspondingQid = questionToAnswer.getParentQid();
                } else {
                    correspondingQid = questionToAnswer.getQid();
                }
                // check if corresponding answer qid equals qid of current question, if yes question was already answered or skipped
                if (tempQ.getQid().equals(correspondingQid)){
                    // already answered or skipped, continue loop
                    answered = true;
                    break;
                }
            }
            if (!answered){
                // No answer for this question, so add it to unasked questions. Order is correct, if the ArrayList was sorted beforehand
                // exclude question with qid of lastquestion, because it was already asked
                System.out.println("No answer found for question "+ tempQ.getQid());
                if(p.getLastquestion() == null) {
                    p.addUnaskedQuestion(tempQ.getQid(), false);
                } else if(!p.getLastquestion().equals(tempQ.getQid())){
                    p.addUnaskedQuestion(tempQ.getQid(), false);
                }
                else {
                    System.out.println("Not adding lastquestion to unasked questions.");
                }
            }
        }


    }

    public void safeQuestionsToDB(SQLDatabase database){
        for(String language : this.getLanguages()){
            for(Question q : questionsHMLanguage.get(language).values()){
                SurveyHandlerServiceQueries.addQuestionToDB(q, q.getAnswerOptions(), database);
            }
        }
    }

    // sorts subquestions of all known questions to this survey
    public void sortSubquestions(String language){
        // QuestionAL contains all parent question, each might have subquestions
        for (Question unsortedQ : this.getQuestionAL(language)){
            this.sortQuestionsAl(unsortedQ.getSubquestionAl());
        }
    }

    public void sortQuestionsAl(ArrayList<Question> questions) {

        // sort arraylist
        Comparator<Question> questionComp = (q1, q2) -> {
            int q1gid = Integer.parseInt(q1.getGorder());
            int q1qo = Integer.parseInt(q1.getQorder());
            int q2gid = Integer.parseInt(q2.getGorder());
            int q2qo = Integer.parseInt(q2.getQorder());

            if (q1gid > q2gid) {
                //System.out.println(q1.getQid() + " question1 > question2 " + q2.getQid());
                return 1;
            } else if (q2gid > q1gid){
                //System.out.println(q2.getQid() + " question2 > question1 " + q1.getQid());
                return -1;
            } else {
                // group ids are equal
                //System.out.println("group ids are equal");
                if (q1qo > q2qo) {
                    //System.out.println(q1.getQuestionOrder() + " qo1 > qo2 " + q2.getQuestionOrder());
                    return 1;
                } else if (q2qo > q1qo) {
                    //System.out.println(q2.getQuestionOrder() + " qo2 > qo1 " + q1.getQuestionOrder());
                    return -1;
                } else {
                    //System.out.println("ERROR: question order and group id are equal");
                    return 0;
                }
            }
            // q1 greater than q2
            //return 1;
            //q2 greater than q1
            //return -1;
            // are equal
            //return 0;
        };

        questions.sort(questionComp);
    }

    public void addTitle(String t) {
        this.title = t;
    }

    public String getTitle() {
        return this.title;
    }

    public void setExpires(String expires) {
        this.expires = expires;
    }

    public String getExpires() {
        return this.expires;
    }

    public String getStartDT() {
        return startDT;
    }

    public void setStartDT(String startDT) {
        this.startDT = startDT;
    }

    public ArrayList<String> getLanguages() {
        //System.out.println("inside getlaguages");
        ArrayList<String> languages = new ArrayList<>();
        for(String s : questionALLanguage.keySet()){
            //System.out.println("currlan: " + s);
            languages.add(s);
        }
        return languages;
    }

    public String getOtherLanguage(String currLanguage){
        for(String s : questionALLanguage.keySet()){
            //System.out.println("currlan: " + s);
            if(!s.equals(currLanguage)){
                return s;
            }
        }


        return null;
    }

    public boolean hasMoreThanOneLanguage(){
        return getLanguages().size() > 1;
    }

    public String getAdminLanguage() {
        return adminLanguage;
    }

    public void setAdminLanguage(String adminLanguage) {
        this.adminLanguage = adminLanguage;
    }

    public ArrayList<Participant> getParticipants() {
        return this.participants;
    }

    public ArrayList<Admin> getAdmins() {
        return this.admins;
    }

    public String getParticipantsEmails(){
        String response = "[";
        for(Participant p : this.participants){
            response += p.getEmail()+",";
        }
        if(response.length() > 1){
            response = response.substring(0,response.length()-1);
        }
        response+= "]";
        return response;
    }

    public boolean addParticipant(Participant p) {
        System.out.println(p.getEmail());
        for (Participant pa : this.participants) {
            if(pa.getEmail().equals(p.getEmail())){
                System.out.println(pa);
                return false;
            }
        }
        System.out.println(this.participants);
        this.participants.add(p);
        System.out.println(this.participants.size());
        // first add questions in defualt language, if language is chosen, adjust
        p.setUnaskedQuestions(this.getSortedQuestionIds(this.getLanguages().get(0)));
        // Only one survey per participant exists
        p.setCurrentSurvey(this);
        return true;
    }

    public boolean addAmin(Admin a){
        System.out.println(a.getAid());
        for (Admin ad : this.admins) {
            if(ad.getAid().equals(ad.getAid())){
                System.out.println(ad);
                return false;
            }
        }
        System.out.println(this.admins);
        this.admins.add(a);
        System.out.println(this.admins.size());
        return true;
    }

    public Admin findAdmin(String aid){
        for(Admin a : this.admins){
            if(a.getAid().equals(aid)){
                return a;
            }
        }
        return null;
    }


    public Participant findParticipant(String email){
        for(Participant p : this.participants){
            if(p.getEmail().equals(email)){
                return p;
            }
        }
        return null;
    }

    public void deleteParticipant(Participant p){
        try{
            System.out.println("Removing participant " + p.getEmail() + "...");
            this.participants.remove(p);
            System.out.println("Participant removed.");
        } catch(Exception e){
            System.out.println("Removing participant failed.");
        }

    }

    public void deleteAdmin(Admin a){
        try{
            System.out.println("Removing Admin " + a.getAid() + "...");
            this.admins.remove(a);
            System.out.println("Admin removed");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public Participant findParticipantByChannel(String channel){
        for(Participant p : this.participants){
            if(p.getChannel().equals(channel)){
                return p;
            }
        }
        return new Participant(channel);
    }

    public String getAnswersStringFromAllParticipants(){
        String returnValue = "";
        /*
        for(Question q : this.questionAL){
            returnValue += q.getText();
            returnValue += "\n";
            if(q.getSubquestionAl().size() > 0){
                for(Question sq : q.getSubquestionAl()){
                    returnValue += sq.getText();
                    returnValue += "\n";
                    returnValue += this.getAnswers(sq);
                    returnValue += "\n";
                }
            }
            /*
            else if(q.getAnswerOptions().size() > 0){
                for(AnswerOption ao : q.getAnswerOptions()){
                    returnValue += ao.getText();
                    returnValue += "\n";
                    returnValue += this.getAnswers(q);
                    returnValue += "\n";
                }
            }

             */
        /*
            else{
                returnValue += this.getAnswers(q);
                returnValue += "\n";
            }
        }
         */
        for(String language : getLanguages()){
            for(Question q : this.questionALLanguage.get(language)){
                returnValue += q.getText();
                returnValue += "\n";
                if(q.getSubquestionAl().size() > 0){
                    for(Question sq : q.getSubquestionAl()){
                        returnValue += sq.getText();
                        returnValue += "\n";
                        returnValue += this.getAnswers(sq);
                        returnValue += "\n";
                    }
                }
                else{
                    returnValue += this.getAnswers(q);
                    returnValue += "\n";
                }
            }
        }
        return returnValue;
    }

    public ArrayList<String> getAnswers(Question q){
        ArrayList<Answer> allAnswers = new ArrayList<>();
        for(Participant p : this.participants){
            if(p.getAnswer(q.getQid()) != null){
                allAnswers.add(p.getAnswer(q.getQid()));
            }
        }
        return getAnswersText(allAnswers);
    }

    public ArrayList<String> getAnswersText(ArrayList<Answer> answers){
        ArrayList<String> all = new ArrayList<>();
        for(Answer a : answers){
            if(this.getQuestionByQid(a.getQid(), getParticipantByPID(a.getPid()).getLanguage()).isSubquestion()){
                // question is subquestion, so display how many poeple chose option
                all.add(a.getText());
            } else if(this.getQuestionByQid(a.getQid(), getParticipantByPID(a.getPid()).getLanguage()).getAnswerOptions().size() > 0){
                // question has answer options, parse text to chosen answer option
                all.add(this.getQuestionByQid(a.getQid(), getParticipantByPID(a.getPid()).getLanguage()).getAnswerOptionByCode(a.getText()).getText());
            } else{
                all.add(a.getText());
            }

            if(a.getComment().length() > 0){
                all.add(" comment: " + a.getComment());
            }
        }
        return all;
    }

    public Participant getParticipantByPID(String pid){
        for(Participant p : getParticipants()){
            if(p.getPid().equals(pid)){
                return p;
            }
        }

        return null;
    }

    public ArrayList<String> getSortedQuestionIds(String language) {
        ArrayList<String> tempAl = new ArrayList<>();
        for(Question q : questionALLanguage.get(language)){
            tempAl.add(q.getQid());
        }
        return tempAl;
    }

    public int numberOfQuestions(){
        int size = 0;
        //size = getSortedQuestions(this.getLanguages().get(0)).size();

        for(Question q : this.getSortedQuestions(this.getLanguages().get(0))){
            size++;
            if(q.getType().equals(Question.qType.ARRAY.toString())){
                // -1 since parent question does not count as singular question
                size += q.getSubquestionAl().size() - 1;
            }
        }

        return size;
    }

    public ArrayList<Question> getSortedQuestions(String language) {
        return this.questionALLanguage.get(language);
    }

    public Question getQuestionByQid(String qid, String language){
        return questionsHMLanguage.get(language).get(qid);
    }

    ArrayList<Question> getQuestionByGid(String gid, String language){
        ArrayList<Question> questionsByGid = new ArrayList<>();
        for(Question q : questionALLanguage.get(language)){
            if (q.getGid().equals(gid)) {
                questionsByGid.add(q);
            }
        }
        return questionsByGid;
    }

    Question getQuestionByCode(String code, String language){
        for(Question q : questionALLanguage.get(language)){
            System.out.println("code: " + q.getCode() + " codee: " + code);
            if (q.getCode().equals(code.toString())) {
                return q;
            }
        }

        return null;
    }

    public Question getParentQuestionBySQQid(String qid, String language){
        for(Question q : this.questionALLanguage.get(language)){
            if(q.checkIfQidInSubQs(qid)){
                return q;
            }
        }
        return null;
    }

}
