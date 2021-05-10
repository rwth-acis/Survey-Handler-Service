package i5.las2peer.services.SurveyHandler;

public class AnswerOption {

    // Database model identifier
    public String sid;
    public String qid;
    public Integer indexi;
    public String code;
    public String text;
    // end Database model identifier


    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getQid() {
        return qid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    public Integer getIndexi() {
        return indexi;
    }

    public void setIndexi(Integer indexi) {
        this.indexi = indexi;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
