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
    SQLDatabase database;
    // end Database model identifier


    // hashmap contains ALL questions (sub and parent questions)
    private HashMap<String, Question> questionsHM = new HashMap<>();
    // questionAL only contains parent questions, should be sorted
    private ArrayList<Question> questionAL = new ArrayList<>();

    private ArrayList<Participant> participants = new ArrayList<>();

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


    public Survey(String sid) {
        this.sid = sid;
    }

    public HashMap<String, Question> getQuestionsHM() {
        return questionsHM;
    }

    public ArrayList<Question> getQuestionAL() {
        return questionAL;
    }

    public void setQuestionsHM(HashMap<String, Question> questionsHM) {
        this.questionsHM = questionsHM;
    }

    public void setQuestionAL(ArrayList<Question> questionAL) {
        this.questionAL = questionAL;
    }

    public void setParticipants(ArrayList<Participant> participants) {
        this.participants = participants;
    }

    // Initialize data structues when given a JSONArray from limesurvey
    public void initLimeSurveyData(JSONArray allQuestions){
        ArrayList<Question> tempQuestionAl = new ArrayList<>();
        // Add questions to survey
        for (Object jo : allQuestions) {
            JSONObject j = (JSONObject) jo;
            try {
                Question newQuestion = new Question();
                newQuestion.initLimeSurveyData(j);
                // put all questions into hashmap
                this.questionsHM.put(newQuestion.getQid(), newQuestion);
                if (newQuestion.isSubquestion()) {
                    // put subquestions into temp list to handle later
                    tempQuestionAl.add(newQuestion);
                } else {

                    // put non-subquestions into arraylist
                    this.questionAL.add(newQuestion);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to parse a question from json.");
            }
        }

        // handle all subquestions and assign them to their parent question, no sub-sub questions possible, so ignore the possibility
        for(Question q : tempQuestionAl){
            String questionParent = q.getParentQid();

            Question parentQuestion = this.questionsHM.get(questionParent);
            parentQuestion.addSubquestion(q);
        }

        for(Question q : this.questionAL){
            q.getSubquestionAl();
            this.sortQuestionsAl(q.getSubquestionAl());
        }

        System.out.println("before sorting...");
        System.out.println(this.getSortedQuestionIds().toString());
        // Create correct question order
        this.sortQuestionsAl(this.questionAL);
        System.out.println("after sorting...");
        System.out.println(this.getSortedQuestionIds().toString());
    }

    // Initialize data structues when given a JSONArray from mobsos surveys
    public void initMobsosData(JSONArray allQuestions){
        ArrayList<Question> tempQuestionAl = new ArrayList<>();
        // Add questions to survey
        int index = 0;
        for (Object jo : allQuestions) {
            JSONObject j = (JSONObject) jo;
            try {
                Question newQuestion = new Question();
                newQuestion.initMobsosData(j, index);
                // put all questions into hashmap
                this.questionsHM.put(newQuestion.getQid(), newQuestion);

                // put non-subquestions into arraylist (they are all non subquestions)
                this.questionAL.add(newQuestion);
                index++;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to parse a question from json.");
            }
        }

    }

    // Initialize data structues when given an ArrayList from database
    public void initQuestionsFromDB(ArrayList<Question> QuestionAl){


        //System.out.println(sur.getTitle());
        // this is the question list for the survey, all questions in here ( also subquestions)
        //ArrayList<Question> QuestionAl = SurveyHandlerServiceQueries.getSurveyQuestionsFromDB(sur.getSid(), database);

        //System.out.println(QuestionAl.toString());
        ArrayList<Question> noSubQuestionAl = new ArrayList<>();
        ArrayList<Question> subQuestionAl = new ArrayList<>();
        HashMap<String, Question> questionsHM = new HashMap<>();
        for (Question teQ : QuestionAl) {
            questionsHM.put(teQ.getQid(), teQ);

            if(teQ.isSubquestion()){
                subQuestionAl.add(teQ);
                continue;
            }
            noSubQuestionAl.add(teQ);
            // init answeroptions
            ArrayList<AnswerOption> answerOptions = SurveyHandlerServiceQueries.getAnswerOptionsFromDB(this.sid, teQ.getQid(), database);
            initAnswerOptionsFromDB(teQ, answerOptions);
        }

        // set the datastructures, handle subquestions afterwards
        this.setQuestionsHM(questionsHM);
        this.setQuestionAL(noSubQuestionAl);

        // iterate through subquestions and add to correct question object in subquestion list
        for (Question tempQ : subQuestionAl) {
            // get parent question, this should exist because we parsed all (parent) questions before
            Question parentQ = this.getQuestionByQid(tempQ.getParentQid());
            parentQ.addSubquestion(tempQ);
        }

        // sort all questions
        this.sortQuestionsAl(this.getQuestionAL());
        this.sortSubquestions();
    }

    public void initParticipantsFromDB(ArrayList<Participant> ParticipantAl){
        for (Participant p : ParticipantAl){
            p.setCurrentSurvey(this);
            this.participants.add(p);
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
        ArrayList<Question> possibleQs = this.getQuestionAL();
        // calculate which questions are still left unanswered
        ArrayList<Question> unaskedQuestions = new ArrayList<>();


        // Add answers directly, then compute unasked questions
        for (Answer a: answerAlFromDB){
            System.out.println("Adding answer "+ a + " to participant "+ p);
            p.addAnswerFromDb(a);
        }
        // Compare to be asked questions for this survey to given answers, to find unanswered questions
        for (Question tempQ : this.getQuestionAL()){
            boolean answered = false;
            for (Answer tempA : answerAlFromDB){
                Question questionToAnswer = this.getQuestionsHM().get(tempA.getQid());
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
        for(Question q : questionsHM.values()){
            SurveyHandlerServiceQueries.addQuestionToDB(q, q.getAnswerOptions(), database);
        }
    }


    private void addQuestion(Question q) {

        this.questionsHM.put(q.getQid(), q);
    }

    // sorts subquestions of all known questions to this survey
    public void sortSubquestions(){
        // QuestionAL contains all parent question, each might have subquestions
        for (Question unsortedQ : this.getQuestionAL()){
            this.sortQuestionsAl(unsortedQ.getSubquestionAl());
        }
    }

    public void sortQuestionsAl(ArrayList<Question> questions) {

        // sort arraylist
        Comparator<Question> questionComp = (q1, q2) -> {
            int q1gid = Integer.parseInt(q1.getGid());
            int q1qo = Integer.parseInt(q1.getQorder());
            int q2gid = Integer.parseInt(q2.getGid());
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

    public ArrayList<Participant> getParticipants() {
        return this.participants;
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
        p.setUnaskedQuestions(this.getSortedQuestionIds());
        // Only one survey per participant exists
        p.setCurrentSurvey(this);
        return true;
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
            else{
                returnValue += this.getAnswers(q);
                returnValue += "\n";
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
            if(this.getQuestionByQid(a.getQid()).isSubquestion()){
                // question is subquestion, so display how many poeple chose option
                all.add(a.getText());
            } else if(this.getQuestionByQid(a.getQid()).getAnswerOptions().size() > 0){
                // question has answer options, parse text to chosen answer option
                all.add(this.getQuestionByQid(a.getQid()).getAnswerOptionByCode(a.getText()).getText());
            } else{
                all.add(a.getText());
            }

            if(a.getComment().length() > 0){
                all.add(" comment: " + a.getComment());
            }
        }
        return all;
    }

    /*
    public HashMap<String, HashMap<String, String>> getAnswers(){
        // return Participant email : [question1 : answer1, ...]
        HashMap<String, HashMap<String, String>> results = new HashMap<>();
        for(Participant p : this.participants){
            String participantEmail = p.getEmail();
            //get answers
            HashMap<String, String> questionAnswerTupel = new HashMap<>();
            for(String questionKey : questionsHM.keySet()){
                questionAnswerTupel.put(questionKey, p.getAnswer(questionKey).getText());
            }
            results.put(participantEmail, questionAnswerTupel);
        }

        return results;
    }
     */


    public ArrayList<String> getSortedQuestionIds() {
        ArrayList<String> tempAl = new ArrayList<>();
        for(Question q : questionAL){
            tempAl.add(q.getQid());
        }
        return tempAl;
    }

    public ArrayList<Question> getSortedQuestions() {
        return this.questionAL;
    }

    public Question getQuestionByQid(String qid){
        return questionsHM.get(qid);
    }

    ArrayList<Question> getQuestionByGid(String gid){
        ArrayList<Question> questionsByGid = new ArrayList<>();
        for(Question q : questionAL){
            if (q.getGid().equals(gid)) {
                questionsByGid.add(q);
            }
        }
        return questionsByGid;
    }

    public Question getParentQuestionBySQQid(String qid){
        for(Question q : this.questionAL){
            if(q.checkIfQidInSubQs(qid)){
                return q;
            }
        }
        return null;
    }

}
