package i5.las2peer.services.SurveyHandler;

import java.util.ArrayList;
import java.util.HashMap;

public class SurveyHelper {

    private int currentQuestion;
    private ArrayList<Integer> skippedQuestions;
    private String answerIntent;
    private HashMap<Integer, String> answers;

    public int getCurrentQuestionNumber() {
        return this.currentQuestion;
    }

    public void incrementCurrentQuestionNumber(){
        this.currentQuestion++;
    }

    public void getSkipIntent(){
        this.skippedQuestions.add(this.getCurrentQuestionNumber());
        this.currentQuestion++;
    }

    public void getAnswerIntent(){
        answers.put(this.getCurrentQuestionNumber(), this.answerIntent);
    }

}
