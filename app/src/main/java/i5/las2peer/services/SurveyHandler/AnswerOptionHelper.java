package i5.las2peer.services.SurveyHandler;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.ArrayList;

public class AnswerOptionHelper {

    public static JSONArray buildAnswerOption(String type, ArrayList<AnswerOption> options) {
        JSONArray interactiveElements = new JSONArray();
        switch (Question.qType.fromName(type)){
            case DICHOTOMOUS:
            case LISTRADIO:
            case SCALE:
            case LISTDROPDOWN:
            case ARRAY:
                interactiveElements = buildMC(options);
                break;
            case GENDER:
                interactiveElements = buildGender();
                break;
            case YESNO:
                interactiveElements.add(buildButton("Survey Answer Option 1", "Ja"));
                interactiveElements.add(buildButton("Survey Answer Option 2", "Nein"));
                break;
            case FIVESCALE:
                interactiveElements.add(buildButton("Survey Answer Option 1", "1"));
                interactiveElements.add(buildButton("Survey Answer Option 2", "2"));
                interactiveElements.add(buildButton("Survey Answer Option 3", "3"));
                interactiveElements.add(buildButton("Survey Answer Option 4", "4"));
                interactiveElements.add(buildButton("Survey Answer Option 5", "5"));
                break;
        }
        return interactiveElements;
    }

    private static JSONArray buildGender() {
        JSONArray array = new JSONArray();
        array.add(buildButton("Survey Answer Option 1", "Weiblich"));
        array.add(buildButton("Survey Answer Option 2", "Maennlich"));
        array.add(buildButton("Survey Answer Option 3", "Keine Angabe"));
        return array;
    }

    private static JSONArray buildMC(ArrayList<AnswerOption> options){
        JSONArray array = new JSONArray();
        for(int i = 1; i < options.size() + 1; i++){
            array.add(buildButton("Survey Answer Option " + i, getAnswerOptionByIndex(i, options).getText()));
        }
        return array;
    }

    public static AnswerOption getAnswerOptionByIndex(Integer index, ArrayList<AnswerOption> options){
        for(AnswerOption ao : options){
            if(ao.getIndexi().equals(index)){
                return ao;
            }
        }
        return null;
    }

    private static JSONObject buildButton(String intent, String label){
        JSONObject button = new JSONObject();
        button.put("intent", intent);
        button.put("label", label);
        button.put("description", label);
        button.put("isFile", false);
        return button;
    }
}
