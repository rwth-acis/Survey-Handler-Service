package i5.las2peer.services.SurveyHandler.database;

import com.google.common.collect.Lists;
import i5.las2peer.services.SurveyHandler.*;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import net.minidev.json.JSONArray;

public class SurveyHandlerServiceQueries {

    public static final ArrayList<String> requiredTables = new ArrayList<String>(Arrays.asList("surveys",
                                                                                        "questions",
                                                                                        "participants",
                                                                                        "answers",
                                                                                        "answeroptions",
                                                                                        "admins"));

    // utility functions
    public static boolean tablesExist(String tableName, SQLDatabase database){
        try {
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;
            String query =  "SHOW TABLES LIKE \"" + tableName + "\"";


            ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();
            if(!rs.first()){
                ps.close();
                con.close();
                return false;
            }else{
                ps.close();
                con.close();
                return true;
            }
        } catch (Exception e){
            System.out.println("Table " + tableName + " not found. Exception: ");
            e.printStackTrace();
            return false;
        }

    }
    public static boolean dropAll(SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "DROP TABLE ";
            for (String t : requiredTables){
                query += t+",";
            }
            query = query.substring(0,query.length()-1);

            System.out.println(query);
            ps = con.prepareStatement(query);
            int rs = ps.executeUpdate();

            boolean noneRemaining = false;
            if (rs == 0){
                noneRemaining = true;
            }

            ps.close();
            con.close();
            return noneRemaining;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add survey.");
            return false;
        }
    }
    public static boolean createTable(String tableName, SQLDatabase database){
        try {
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;


            String query = "CREATE TABLE " + tableName + " (";
            switch (tableName) {
                case "surveys":
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "adminmail VARCHAR(256) NOT NULL,";
                    query += "expires VARCHAR(50),";
                    query += "startdt VARCHAR(50),";
                    query += "title VARCHAR(150) NOT NULL,";
                    query += "welcometext VARCHAR(1000),";
                    query += "titleotherlanguage VARCHAR(150),";
                    query += "welcometextotherlanguage VARCHAR(1000),";
                    query += "adminlanguage VARCHAR(50)";
                    break;
                case "questions":
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(500) NOT NULL,";
                    query += "type VARCHAR(50) NOT NULL,";
                    query += "parentqid VARCHAR(50),";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "qorder VARCHAR(50) NOT NULL,";
                    query += "gorder VARCHAR(50) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "help VARCHAR(500),";
                    query += "code VARCHAR(50),";
                    query += "relevance VARCHAR(150) NOT NULL,";
                    query += "mandatory BOOL,";
                    query += "language VARCHAR(50)";
                    break;
                case "participants":
                    query += "pid VARCHAR(256) NOT NULL,";
                    query += "email VARCHAR(256) NOT NULL,";
                    query += "channel VARCHAR(50),";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "lastquestion VARCHAR(50),";
                    query += "lasttimeactive VARCHAR(50),";
                    query += "surveyresponseid VARCHAR(50),";
                    query += "participantcontacted BOOL,";
                    query += "completedsurvey BOOL,";
                    query += "language VARCHAR(50),";
                    query += "languagetimestamp VARCHAR(50),";
                    query += "lastchosensurveyid VARCHAR(50),";
                    query += "lastchosensurveytimestamp VARCHAR(50)";
                    break;
                case "answers":
                    query += "pid VARCHAR(256) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(1200),"; // Huge free text is answer option by limesurvey
                    query += "comment VARCHAR(900),";
                    query += "dtanswered VARCHAR(50),";
                    query += "messagets VARCHAR(50),";
                    query += "messageid VARCHAR(50),";
                    query += "prevmessagets VARCHAR(50),";
                    query += "commentts VARCHAR(50),";
                    query += "finalized BOOL,";
                    query += "isskipped BOOL";
                    break;
                case "answeroptions":
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "indexi INTEGER NOT NULL,";
                    query += "code VARCHAR(50),";
                    query += "text VARCHAR(700) NOT NULL,";
                    query += "language VARCHAR(50)";
                    break;
                case "admins":
                    query += "aid VARCHAR(50) NOT NULL,";
                    query += "currAdministrating VARCHAR(50),";
                    query += "language VARCHAR(50)";
                    break;

                default:
                    // There exists no pattern for given tablename
                    System.out.println("ERROR: No sql statement found for " + tableName);
                    return false;
            }
            query += ")";

            ps = con.prepareStatement(query);
            int rs = ps.executeUpdate();
            boolean created;
            if (rs > 0) {
                System.out.println("Failed to create table " + tableName + ".");
                System.out.println(rs);
                created = false;
            } else {
                System.out.println("Created table " + tableName + " succesfully");
                created = true;
            }

            ps.close();
            con.close();
            return created;
        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add table.");
            return false;
        }
    }


    // database collection access
    public static ArrayList<Question> getSurveyQuestionsFromDB(String sid, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM questions WHERE sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No questions found for survey " + sid);
                ps.close();
                con.close();
                // a survey without questions can not be the case, so return null
                return null;
            } else{
                System.out.println("Found questions in database.");
            }

            ArrayList<Question> allQ = new ArrayList<>();
            while(!rs.isAfterLast()){
                Question tempQ = SurveyHandlerServiceQueries.castToQuestion(rs);

                allQ.add(tempQ);
                System.out.println("Found and added question with id: " + tempQ.getQid());
                rs.next();
            }

            ps.close();
            con.close();
            return allQ;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static ArrayList<AnswerOption> getAnswerOptionsFromDB(String sid, String qid, String language, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM answeroptions WHERE sid = ? AND qid = ? AND language = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);
            ps.setString(2, qid);
            ps.setString(3, language);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No answeroptions found for survey " + sid + "and qid " + qid);
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found answerOptions in database.");
            }

            ArrayList<AnswerOption> allAO = new ArrayList<>();
            while(!rs.isAfterLast()){
                AnswerOption tempAO = SurveyHandlerServiceQueries.castToAnswerOption(rs);

                allAO.add(tempAO);
                System.out.println("Found and added question with id: " + tempAO.getQid());
                rs.next();
            }

            ps.close();
            con.close();
            return allAO;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }
    public static ArrayList<Survey> getSurveysFromDB(SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM surveys";
            ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No survey found in database.");
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found surveys in database.");
            }

            ArrayList<Survey> allS = new ArrayList<>();
            while(!rs.isAfterLast()){
                Survey tempS = SurveyHandlerServiceQueries.castToSurvey(rs);
                allS.add(tempS);
                System.out.println("Found and added survey " + tempS.getSid());
                rs.next();
            }

            ps.close();
            con.close();
            return allS;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static ArrayList<Participant> getSurveyParticipantsFromDB(String sid, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM participants WHERE sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No participants found for survey " + sid);
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found participants in database.");
            }

            ArrayList<Participant> allP = new ArrayList<>();
            while(!rs.isAfterLast()){
                Participant tempP = SurveyHandlerServiceQueries.castToParticipant(rs);
                allP.add(tempP);
                System.out.println("Found and added participant with id: " + tempP.getPid());
                rs.next();
            }

            ps.close();
            con.close();
            return allP;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static Survey getSurveyFromDB(String sid, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM surveys WHERE sid =?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);
            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("Survey not found in database.");
                ps.close();
                con.close();
                return null;
            } else{
                System.out.println("Found survey with id " + sid);
                Survey res = SurveyHandlerServiceQueries.castToSurvey(rs);
                ps.close();
                con.close();
                return res;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }
    public static ArrayList<Admin> getAdminsFromDB(SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM admins";
            ps = con.prepareStatement(query);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No admins found in database.");
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found admins in database.");
            }

            ArrayList<Admin> allA = new ArrayList<>();
            while(!rs.isAfterLast()){
                Admin tempA = SurveyHandlerServiceQueries.castToAdmin(rs);
                allA.add(tempA);
                System.out.println("Found and added admin with id: " + tempA.getAid());
                rs.next();
            }

            ps.close();
            con.close();
            return allA;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }
    public static ArrayList<Answer> getAnswersForParticipantFromDB(String sid, String pid, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM answers WHERE sid = ? AND pid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);
            ps.setString(2,pid);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No answers found for participant " + pid + " and survey " + sid);
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found answers in database.");
            }

            ArrayList<Answer> allA = new ArrayList<>();
            while(!rs.isAfterLast()){
                Answer tempA = SurveyHandlerServiceQueries.castToAnswer(rs);
                allA.add(tempA);
                System.out.println("Found and added answer for participant with id: " + tempA.getPid());
                rs.next();
            }

            ps.close();
            con.close();
            return allA;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static void getAnswerOptionsForQuestionFromDB(Question q, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String sid = q.getSid();
            String qid = q.getQid();
            String language = q.getLanguage();

            String query = "SELECT * FROM answeroptions WHERE sid = ? AND qid = ? AND language = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, sid);
            ps.setString(2, qid);
            ps.setString(3, language);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No answeroptions found for question " + qid + " and survey " + sid);
                ps.close();
                con.close();
                return;
            } else{
                System.out.println("Found answeroptions in database.");
            }

            while(!rs.isAfterLast()){
                String sidDB = rs.getString("sid");
                String qidDB = rs.getString("qid");
                Integer indexDB = rs.getInt("indexi");
                String code = rs.getString("code");
                String textDB = rs.getString("text");
                String languageDB = rs.getString("language");

                AnswerOption ao = new AnswerOption();
                ao.setSid(sidDB);
                ao.setQid(qidDB);
                ao.setIndexi(indexDB);
                ao.setCode(code);
                ao.setText(textDB);
                ao.setLanguage(languageDB);
                q.setAnswerOption(ao);

                System.out.println("Found and added answeroptions for question with code: " + ao.getCode());
                rs.next();
            }

            ps.close();
            con.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // getting objects from database
    public static Survey castToSurvey(ResultSet rs){
        try{
            String sid = rs.getString("sid");
            String adminmail = rs.getString("adminmail");
            String expires = rs.getString("expires");
            String startdt = rs.getString("startdt");
            String title = rs.getString("title");
            String welcomeText = rs.getString("welcometext");
            String titleOtherLanguage = rs.getString("titleotherlanguage");
            String welcomeTextOtherLanguage = rs.getString("welcometextotherlanguage");
            String adminLanguage = rs.getString("adminlanguage");


            Survey res = new Survey(sid);
            res.setAdminmail(adminmail);
            res.setExpires(expires);
            res.setStartDT(startdt);
            res.setTitle(title);
            res.setWelcomeText(welcomeText);
            res.setTitleOtherLanguage(titleOtherLanguage);
            res.setWelcomeTextOtherLanguage(welcomeTextOtherLanguage);
            res.setAdminLanguage(adminLanguage);

            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static Question castToQuestion(ResultSet rs){
        try{
            String qid = rs.getString("qid");
            String gid = rs.getString("gid");
            String parentqid = rs.getString("parentqid");
            String text = rs.getString("text");
            String type = rs.getString("type");
            String qorder = rs.getString("qorder");
            String gorder = rs.getString("gorder");
            String sid = rs.getString("sid");
            String help = rs.getString("help");
            String code = rs.getString("code");
            String relevance = rs.getString("relevance");
            boolean mandatory = rs.getBoolean("mandatory");
            String language = rs.getString("language");

            Question res = new Question();
            res.setQid(qid);
            res.setGid(gid);
            res.setParentQid(parentqid);
            res.setText(text);
            res.setType(type);
            res.setQorder(qorder);
            res.setGorder(gorder);
            res.setSid(sid);
            res.setHelp(help);
            res.setCode(code);
            res.setRelevance(relevance);
            res.setMandatory(mandatory);
            res.setLanguage(language);
            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static AnswerOption castToAnswerOption(ResultSet rs){
        try{
            String sid = rs.getString("sid");
            String qid = rs.getString("qid");
            Integer indexi = rs.getInt("indexi");
            String code = rs.getString("code");
            String text = rs.getString("text");
            String language = rs.getString("language");


            AnswerOption res = new AnswerOption();
            res.setSid(sid);
            res.setQid(qid);
            res.setIndexi(indexi);
            res.setText(text);
            res.setCode(code);
            res.setLanguage(language);
            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static Participant castToParticipant(ResultSet rs){
        try{
            String pid = rs.getString("pid");
            String sid = rs.getString("sid");
            String email = rs.getString("email");
            String channel = rs.getString("channel");
            String lastquestion = rs.getString("lastquestion");
            String lasttimeactive = rs.getString("lasttimeactive");
            String surveyresponseid = rs.getString("surveyresponseid");
            boolean participantcontacted = rs.getBoolean("participantcontacted");
            boolean completedsurvey = rs.getBoolean("completedsurvey");
            String language = rs.getString("language");
            String languagetimestamp = rs.getString("languagetimestamp");
            String lastchosensurveyid = rs.getString("lastchosensurveyid");
            String lastchosensurveytimestamp = rs.getString("lastchosensurveytimestamp");

            Participant res = new Participant(email);
            res.setPid(pid);
            res.setSid(sid);
            res.setChannel(channel);
            res.setLastquestion(lastquestion);
            res.setLasttimeactive(lasttimeactive);
            res.setSurveyresponseid(surveyresponseid);
            res.setParticipantcontacted(participantcontacted);
            res.setCompletedsurvey(completedsurvey);
            res.setLanguage(language);
            res.setLanguageTimestamp(languagetimestamp);
            res.setLastChosenSurveyID(lastchosensurveyid);
            res.setLastChosenSurveyTimestamp(lastchosensurveytimestamp);
            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static Answer castToAnswer(ResultSet rs){
        try{
            String pid = rs.getString("pid");
            String sid = rs.getString("sid");
            String qid = rs.getString("qid");
            String gid = rs.getString("gid");
            String text = rs.getString("text");
            String comment = rs.getString("comment");
            String dtanswered = rs.getString("dtanswered");
            String messagets = rs.getString("messagets");
            String messageid = rs.getString("messageid");
            String prevmessagets = rs.getString("prevmessagets");
            String commentts = rs.getString("commentts");
            boolean finalized = rs.getBoolean("finalized");
            boolean isskipped = rs.getBoolean("isskipped");

            Answer res = new Answer();
            res.setPid(pid);
            res.setSid(sid);
            res.setQid(qid);
            res.setGid(gid);
            res.setText(text);
            res.setComment(comment);
            res.setDtanswered(dtanswered);
            res.setMessageTs(messagets);
            res.setMessageId(messageid);
            res.setPrevMessageTs(prevmessagets);
            res.setCommentTs(commentts);
            res.setFinalized(finalized);
            res.setSkipped(isskipped);
            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public static Admin castToAdmin(ResultSet rs){
        try{
            String aid = rs.getString("aid");
            String language = rs.getString("language");
            String currAdministrating = rs.getString("currAdministrating");

            Admin res = new Admin(aid);
            res.setLanguage(language);
            res.setCurrAdministrating(currAdministrating);
            return res;

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    // adding objects to database
    public static boolean addSurveyToDB(Survey s,SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO surveys(sid, adminmail, expires, startdt, title, welcometext, titleotherlanguage, welcometextotherlanguage, adminlanguage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, s.getSid());
            ps.setString(2, s.getAdminmail());
            ps.setString(3,s.getExpires());
            ps.setString(4,s.getStartDT());
            ps.setString(5,s.getTitle());
            ps.setString(6, s.getWelcomeText());
            ps.setString(7,s.getTitleOtherLanguage());
            ps.setString(8, s.getWelcomeTextOtherLanguage());
            ps.setString(9, s.getAdminLanguage());
            int rs = ps.executeUpdate();

            boolean inserted = false;
            if (rs > 0){
                inserted = true;
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add survey.");
            return false;
        }
    }

    public static boolean addQuestionToDB(Question q, ArrayList<AnswerOption> answerOptions, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO questions(qid, text, type, parentqid, gid, qorder, gorder, sid, help, code, relevance, mandatory, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, q.getQid());
            ps.setString(2, q.getText());
            ps.setString(3, q.getType());
            ps.setString(4, q.getParentQid());
            ps.setString(5, q.getGid());
            ps.setString(6, q.getQorder());
            ps.setString(7, q.getGorder());
            ps.setString(8, q.getSid());
            ps.setString(9, q.getHelp());
            ps.setString(10, q.getCode());
            ps.setString(11, q.getRelevance());
            ps.setBoolean(12, q.isMandatory());
            ps.setString(13, q.getLanguage());
            int rs = ps.executeUpdate();

            boolean inserted = false;
            if (rs > 0){
                inserted = true;
            }

            if(!answerOptions.isEmpty()){
                //System.out.println("empty " + answerOptions.isEmpty() + ", size " + answerOptions.size());
                addAnsweroptionsToDB(q, database);
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add question.");
            return false;
        }
    }

    public static boolean addParticipantToDB(Participant p, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO participants(channel, email, pid, sid, lastquestion, lasttimeactive, surveyresponseid, participantcontacted, completedsurvey, language, languagetimestamp, lastchosensurveyid, lastchosensurveytimestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, p.getChannel());
            ps.setString(2, p.getEmail());
            ps.setString(3, p.getPid());
            ps.setString(4, p.getSid());
            ps.setString(5, p.getLastquestion());
            ps.setString(6, p.getLasttimeactive());
            ps.setString(7, p.getSurveyResponseID());
            ps.setBoolean(8, p.isParticipantcontacted());
            ps.setBoolean(9, p.isCompletedsurvey());
            ps.setString(10, p.getLanguage());
            ps.setString(11, p.getLanguageTimestamp());
            ps.setString(12, p.getLastChosenSurveyID());
            ps.setString(13, p.getLastChosenSurveyTimestamp());

            int rs = ps.executeUpdate();

            boolean inserted = false;
            if (rs > 0){
                inserted = true;
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add participant.");
            return false;
        }
    }

    public static boolean addAdminToDB(Admin admin, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO admins(aid, currAdministrating, language) VALUES (?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, admin.getAid());
            ps.setString(2, admin.getCurrAdministrating());
            ps.setString(3, admin.getLanguage());

            int rs = ps.executeUpdate();

            boolean inserted = false;
            if (rs > 0){
                inserted = true;
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add admin.");
            return false;
        }
    }

    public static boolean addAnswerToDB(Answer answer, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO answers(pid, sid, qid, gid, text, comment, dtanswered, messagets, messageid, prevmessagets, commentts, finalized, isskipped) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, answer.getPid());
            ps.setString(2, answer.getSid());
            ps.setString(3, answer.getQid());
            ps.setString(4, answer.getGid());
            ps.setString(5, answer.getText());
            ps.setString(6, answer.getComment());
            ps.setString(7, answer.getDtanswered());
            ps.setString(8, answer.getMessageTs());
            ps.setString(9, answer.getMessageId());
            ps.setString(10, answer.getPrevMessageTs());
            ps.setString(11, answer.getCommentTs());
            ps.setBoolean(12, answer.isFinalized());
            ps.setBoolean(13, answer.isSkipped());
            int rs = ps.executeUpdate();

            boolean inserted = false;
            if (rs > 0){
                inserted = true;
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add question.");
            return false;
        }
    }

    public static boolean addAnsweroptionsToDB(Question q, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;
            boolean inserted = false;

            // Integer in answeroptionsstringal starts at 1
            for(int i = 1; i < q.getAnswerOptions().size() + 1; i++){
                AnswerOption answerOption  = q.getAnswerOptionByIndex(i);
                String query = "INSERT INTO answeroptions(sid, qid, indexi, code, text, language) VALUES (?, ?, ?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                ps.setString(1, answerOption.getSid());
                ps.setString(2, answerOption.getQid());
                ps.setInt(3, answerOption.getIndexi());
                ps.setString(4, answerOption.getCode());
                ps.setString(5, answerOption.getText());
                ps.setString(6, answerOption.getLanguage());

                int rs = ps.executeUpdate();

                if (rs > 0){
                    inserted = true;
                }
            }

            ps.close();
            con.close();
            return inserted;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not add answeroptions for question " + q.getQid() + " with type " + q.getType() + ".");
            return false;
        }
    }

    public static boolean updateParticipantsSIDInDB(Participant p, String oldSID, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM participants WHERE pid = ? AND sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, p.getPid());
            ps.setString(2, oldSID);
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the participant, so update the entry
                ps.close();
                ps = con.prepareStatement("UPDATE participants SET sid = ? WHERE sid = ? AND pid = ?");
                ps.setString(1, p.getSid());
                // where clause
                ps.setString(2, oldSID);
                ps.setString(3, p.getPid());
                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("UPDATE SID - Did not find participant in database. Could not update.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not update participant.");
            return false;
        }
    }

    public static boolean updateParticipantInDB(Participant p, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM participants WHERE pid = ? AND sid = ?";
             ps = con.prepareStatement(query);
            ps.setString(1, p.getPid());
            ps.setString(2, p.getSid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the participant, so update the entry
                ps.close();
                ps = con.prepareStatement("UPDATE participants SET channel = ?, email = ?, pid = ?, sid = ?, lastquestion = ?, lasttimeactive = ?, surveyresponseid = ?, participantcontacted = ?, completedsurvey = ?, language = ?, languagetimestamp = ?,  lastchosensurveyid = ?, lastchosensurveytimestamp = ? WHERE sid = ? AND pid = ?");
                ps.setString(1, p.getChannel());
                ps.setString(2, p.getEmail());
                ps.setString(3, p.getPid());
                ps.setString(4, p.getSid());
                ps.setString(5, p.getLastquestion());
                ps.setString(6, p.getLasttimeactive());
                ps.setString(7, p.getSurveyResponseID());
                ps.setBoolean(8, p.isParticipantcontacted());
                ps.setBoolean(9, p.isCompletedsurvey());
                ps.setString(10, p.getLanguage());
                ps.setString(11, p.getLanguageTimestamp());
                ps.setString(12, p.getLastChosenSurveyID());
                ps.setString(13, p.getLastChosenSurveyTimestamp());
                // where clause
                ps.setString(14, p.getSid());
                ps.setString(15,p.getPid());
                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find participant in database. Could not update.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not update participant.");
            return false;
        }
    }

    public static boolean updateParticipantsPidInDB(Participant p, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM participants WHERE channel = ? AND email = ? AND sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, p.getChannel());
            ps.setString(2, p.getEmail());
            ps.setString(3, p.getSid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the participant, so update the entry
                ps.close();
                ps = con.prepareStatement("UPDATE participants SET pid = ? WHERE channel = ? AND email = ? AND sid = ?");
                ps.setString(1, p.getPid());
                // where clause
                ps.setString(2, p.getChannel());
                ps.setString(3, p.getEmail());
                ps.setString(4, p.getSid());
                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("UPDATE PID - Did not find participant in database. Could not update.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not update participant.");
            return false;
        }
    }

    public static boolean updateAdminInDB(Admin a, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM admins WHERE aid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, a.getAid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so update the entry
                ps.close();
                ps = con.prepareStatement("UPDATE admins SET currAdministrating = ?, language = ? WHERE aid = ?");
                ps.setString(1, a.getCurrAdministrating());
                ps.setString(2, a.getLanguage());
                // where clause
                ps.setString(3, a.getAid());
                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find admin in database. Could not update.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not update admin.");
            return false;
        }
    }

    public static boolean updateAnswerInDB(Answer a, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;
            System.out.println("querie: " + a.getMessageTs() + " prev: " + a.getPrevMessageTs() + " " + a.getSid() + " " + a.getQid() + " " + a.getPid());

            String query = "SELECT * FROM answers WHERE messagets = ? AND sid = ? AND qid = ? AND pid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, a.getPrevMessageTs());
            ps.setString(2, a.getSid());
            ps.setString(3, a.getQid());
            ps.setString(4, a.getPid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so update the entry
                ps.close();
                ps = con.prepareStatement("UPDATE answers SET messagets = ?, text = ?, commentts = ?, comment = ?, finalized = ?, isskipped = ? WHERE messagets = ? AND sid = ? AND qid = ? AND pid = ?");
                ps.setString(1, a.getMessageTs());
                ps.setString(2, a.getText());
                ps.setString(3, a.getCommentTs());
                ps.setString(4, a.getComment());
                ps.setBoolean(5, a.isFinalized());
                ps.setBoolean(6, a.isSkipped());
                // where clause
                ps.setString(7, a.getPrevMessageTs());
                ps.setString(8, a.getSid());
                ps.setString(9, a.getQid());
                ps.setString(10, a.getPid());
                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find answer in database. Could not update.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not update answer.");
            return false;
        }
    }

    // helper functions to initialize objects

    public static ArrayList<String> getSubquestionIds(String qid, String sid, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM questions WHERE parentqid = ? AND sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, qid);
            ps.setString(2,sid);

            ResultSet rs = ps.executeQuery();

            if (!rs.first()){
                System.out.println("No subquestions found for survey " + sid + "and qid "+ qid);
                ps.close();
                con.close();
                return new ArrayList<>();
            } else{
                System.out.println("Found questions in database.");
            }

            ArrayList<String> allQ = new ArrayList<>();
            while(!rs.isAfterLast()){
                String tempRes = rs.getString("qid");
                allQ.add(tempRes);
                System.out.println("Found and added subquestion with qid: " + tempRes);
                rs.next();
            }

            ps.close();
            con.close();
            return allQ;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    // delete functions
    public static boolean deleteParticipantFromDB(Participant p, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            ArrayList<Answer> answersToDelete = p.getGivenAnswersAl();

            String query = "SELECT * FROM participants WHERE pid = ? AND sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, p.getPid());
            ps.setString(2, p.getSid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the participant, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM participants WHERE sid = ? AND pid = ?");

                ps.setString(1, p.getSid());
                ps.setString(2,p.getPid());

                ps.executeUpdate();
                updated = true;

                // delete all answers as well
                for(Answer a : answersToDelete){
                    deleteAnswerFromDB(a, database);
                }

            } else {
                System.out.println("Did not find participant in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete participant.");
            return false;
        }
    }

    public static boolean deleteAdminFromDB(Admin a, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM admins WHERE aid = ? AND currAdministrating = ? AND language = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, a.getAid());
            ps.setString(2, a.getCurrAdministrating());
            ps.setString(3, a.getLanguage());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the admin, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM admins WHERE aid = ? AND currAdministrating = ? AND language = ?");

                ps.setString(1, a.getAid());
                ps.setString(2, a.getCurrAdministrating());
                ps.setString(3, a.getLanguage());

                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find admin in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete admin.");
            return false;
        }
    }

    public static boolean deleteAnswerFromDB(Answer a, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM answers WHERE sid = ? AND pid = ? AND qid = ? AND finalized = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, a.getSid());
            ps.setString(2, a.getPid());
            ps.setString(3, a.getQid());
            ps.setBoolean(4, a.isFinalized());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM answers WHERE sid = ? AND pid = ? AND qid = ? AND finalized = ?");

                ps.setString(1, a.getSid());
                ps.setString(2, a.getPid());
                ps.setString(3, a.getQid());
                ps.setBoolean(4, a.isFinalized());

                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find answer in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete answer.");
            return false;
        }
    }

    public static boolean deleteQuestionFromDB(Question q, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            ArrayList<AnswerOption> answeroptionsToDelete = q.getAnswerOptions();

            String query = "SELECT * FROM questions WHERE sid = ? AND qid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, q.getSid());
            ps.setString(2, q.getQid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM questions WHERE sid = ? AND qid = ? AND language = ?");

                ps.setString(1, q.getSid());
                ps.setString(2, q.getQid());
                ps.setString(3, q.getLanguage());

                ps.executeUpdate();
                updated = true;

                // delete all answers as well
                for(AnswerOption ao : answeroptionsToDelete){
                    deleteAnswerOptionFromDB(ao, database);
                }

            } else {
                System.out.println("Did not find answer in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete answer.");
            return false;
        }
    }

    public static boolean deleteAnswerOptionFromDB(AnswerOption ao, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "SELECT * FROM answeroptions WHERE sid = ? AND qid = ? AND code = ? AND language = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, ao.getSid());
            ps.setString(2, ao.getQid());
            ps.setString(3, ao.getCode());
            ps.setString(4, ao.getLanguage());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM answeroptions WHERE sid = ? AND qid = ? AND code = ? AND language = ?");

                ps.setString(1, ao.getSid());
                ps.setString(2, ao.getQid());
                ps.setString(3, ao.getCode());
                ps.setString(4, ao.getLanguage());

                ps.executeUpdate();
                updated = true;
            } else {
                System.out.println("Did not find answeroption in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete answeroption.");
            return false;
        }
    }

    public static boolean deleteSurveyFromDB(Survey s, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            // retrieve all Questions and Participants to delete as well
            ArrayList<Question> questionsToDelete = new ArrayList<>();
            for(String lang : s.getLanguages()){
                questionsToDelete = s.getQuestionAL(lang);
            }
            ArrayList<Participant> participantsToDelete = s.getParticipants();

            String query = "SELECT * FROM surveys WHERE sid = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, s.getSid());
            boolean updated = false;
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                // Found the answer, so deleting the entry
                ps.close();
                ps = con.prepareStatement("DELETE FROM surveys WHERE sid = ?");

                ps.setString(1, s.getSid());

                ps.executeUpdate();
                updated = true;

                // delete all questions and participants as well
                for(Participant p : participantsToDelete){
                    deleteParticipantFromDB(p, database);
                }
                for(Question q : questionsToDelete){
                    deleteQuestionFromDB(q, database);
                }
            } else {
                System.out.println("Did not find survey in database. Could not delete.");
                updated = false;
            }


            ps.close();
            con.close();
            return updated;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Could not delete survey.");
            return false;
        }
    }
}
