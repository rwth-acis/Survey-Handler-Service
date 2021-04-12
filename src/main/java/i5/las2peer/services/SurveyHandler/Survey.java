package i5.las2peer.services.SurveyHandler;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;


public class Survey {

    private String id;
    private HashMap<String, Question> questionsHM = new HashMap<>();
    private ArrayList<Question> questionAL = new ArrayList<>();
    private String title;
    private ArrayList<Participant> participants = new ArrayList<>();

    public Survey(String id, JSONArray allQuestions) {
        
        ArrayList<Question> tempQuestionAl = new ArrayList<>();

        // Add questions to survey
        for (Object jo : allQuestions) {
            JSONObject j = (JSONObject) jo;
            try {
                Question newQuestion = new Question(j);
                if (newQuestion.isSubquestion()) {
                    // put subquestions into temp list to handle later
                    tempQuestionAl.add(newQuestion);
                } else {
                    // put non-subquestions into hashmap
                    this.questionsHM.put(newQuestion.getQid(), newQuestion);
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


        System.out.println("before sorting...");
        System.out.println(this.getSortedQuestionIds().toString());
        // Create correct question order
        this.sortQuestionsAl();
        System.out.println("after sorting...");
        System.out.println(this.getSortedQuestionIds().toString());

        this.id = id;

    }

    private void addQuestion(Question q) {

        this.questionsHM.put(q.getQid(), q);
    }

    private void sortQuestionsAl() {

        // sort arraylist
        Comparator<Question> questionComp = (q1, q2) -> {
            int q1gid = Integer.parseInt(q1.getGid());
            int q1qo = Integer.parseInt(q1.getQuestionOrder());
            int q2gid = Integer.parseInt(q2.getGid());
            int q2qo = Integer.parseInt(q2.getQuestionOrder());

            if (q1gid > q2gid) {
                System.out.println(q1.getQid() + " question1 > question2 " + q2.getQid());
                return 1;
            } else if (q2gid > q1gid){
                System.out.println(q2.getQid() + " question2 > question1 " + q1.getQid());
                return -1;
            } else {
                // group ids are equal
                System.out.println("group ids are equal");
                if (q1qo > q2qo) {
                    System.out.println(q1.getQuestionOrder() + " qo1 > qo2 " + q2.getQuestionOrder());
                    return 1;
                } else if (q2qo > q1qo) {
                    System.out.println(q2.getQuestionOrder() + " qo2 > qo1 " + q1.getQuestionOrder());
                    return -1;
                } else {
                    System.out.println("ERROR: question order and group id are equal");
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

        this.questionAL.sort(questionComp);
    }

    public void addTitle(String t) {
        this.title = t;
    }

    public String getTitle() {
        return this.title;
    }

    public ArrayList<Participant> getParticipants() {
        return this.participants;
    }

    public String getParticipantsEmails(){
        String response = "[";
        for(Participant p : this.participants){
            response += p.getEmail()+",";
        }
        response.substring(0,response.length()-1);
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


    public HashMap<String, HashMap<String, String>> getAnswers(){
        // return Participant email : [question1 : answer1, ...]
        HashMap<String, HashMap<String, String>> results = new HashMap<>();
        for(Participant p : this.participants){
            String participantEmail = p.getEmail();
            //get answers
            HashMap<String, String> questionAnswerTupel = new HashMap<>();
            for(String questionKey : questionsHM.keySet()){
                questionAnswerTupel.put(questionKey, p.getAnswer(questionKey));
            }
            results.put(participantEmail, questionAnswerTupel);
        }

        return results;
    }


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

    Question getQuestionByQid(String qid){
        return questionsHM.get(qid);
    }

}
