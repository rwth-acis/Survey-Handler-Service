package i5.las2peer.services.SurveyHandler;

public class Answer {
    // Database model identifier
    public String pid;
    public String sid;
    public String qid;
    public String gid;
    public String text;
    public String comment;
    public boolean skipped;
    // end Database model identifier

    public Answer(){
        this.comment = "";
    }

    // TODO
    public String generateLSAnswerKey(){
        return "";
    }

    public boolean isSkipped() {
        return skipped;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getGid() {
        return gid;
    }

    public void setGid(String gid) {
        this.gid = gid;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
