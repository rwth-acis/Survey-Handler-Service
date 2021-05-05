package i5.las2peer.services.SurveyHandler;

public class Answer {
    // Database model identifier
    public String pid;
    public String sid;
    public String qid;
    public String gid;
    public String text;
    public String comment;
    public String dtanswered;
    public String prevMessageTs;
    public boolean finalized;

    //used for unique identification of messages (unique per channel, provided by slack)
    public String messageTs;
    public String commentTs;

    public boolean skipped;
    // end Database model identifier

    public Answer(){
        this.comment = "";
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

    public String getDtanswered() {
        return dtanswered;
    }

    public void setDtanswered(String dtanswered) {
        this.dtanswered = dtanswered;
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

    public String getMessageTs() {
        return messageTs;
    }

    public void setMessageTs(String messageTs) {
        this.messageTs = messageTs;
    }

    public String getCommentTs() {
        return commentTs;
    }

    public void setCommentTs(String commentTs) {
        this.commentTs = commentTs;
    }

    public String getPrevMessageTs() {
        return prevMessageTs;
    }

    public void setPrevMessageTs(String prevMessageTs) {
        this.prevMessageTs = prevMessageTs;
    }

    public boolean isFinalized() {
        return this.finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }
}
