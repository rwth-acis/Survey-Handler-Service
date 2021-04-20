package i5.las2peer.services.SurveyHandler.database;

import com.google.common.collect.Lists;
import i5.las2peer.services.SurveyHandler.Answer;
import i5.las2peer.services.SurveyHandler.Participant;
import i5.las2peer.services.SurveyHandler.Question;
import i5.las2peer.services.SurveyHandler.Survey;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import net.minidev.json.JSONArray;

public class SurveyHandlerServiceQueries {

    public static final ArrayList<String> requiredTables = new ArrayList<String>(Arrays.asList("surveys",
                                                                                        "questions",
                                                                                        "participants",
                                                                                        "answers"));

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
                    query += "adminmail VARCHAR(50) NOT NULL,";
                    query += "botname VARCHAR(256) NOT NULL,";
                    query += "expires VARCHAR(50),";
                    query += "title VARCHAR(50) NOT NULL";
                    break;
                case "questions":
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(50) NOT NULL,";
                    query += "type VARCHAR(50) NOT NULL,";
                    query += "parentqid VARCHAR(50),";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "qorder VARCHAR(50) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "help VARCHAR(50),";
                    query += "relevance VARCHAR(50) NOT NULL";
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
                    query += "completedsurvey BOOL";
                    break;
                case "answers":
                    query += "pid VARCHAR(256) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(500),"; // Huge free text is answer option by limesurvey
                    query += "isskipped BOOL";
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
    public static String getParticipantModel(){
        return "select ";
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

    // getting objects from database
    public static Survey castToSurvey(ResultSet rs){
        try{
            String sid = rs.getString("sid");
            String adminmail = rs.getString("adminmail");
            String botname = rs.getString("botname");
            String expires = rs.getString("expires");
            String title = rs.getString("title");


            Survey res = new Survey(sid);
            res.setAdminmail(adminmail);
            res.setBotname(botname);
            res.setExpires(expires);
            res.setTitle(title);

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
            String sid = rs.getString("sid");
            String help = rs.getString("help");
            String relevance = rs.getString("relevance");

            Question res = new Question();
            res.setQid(qid);
            res.setGid(gid);
            res.setParentqid(parentqid);
            res.setText(text);
            res.setType(type);
            res.setQorder(qorder);
            res.setSid(sid);
            res.setHelp(help);
            res.setRelevance(relevance);
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

            Participant res = new Participant(email);
            res.setPid(pid);
            res.setSid(sid);
            res.setChannel(channel);
            res.setLastquestion(lastquestion);
            res.setLasttimeactive(lasttimeactive);
            res.setSurveyresponseid(surveyresponseid);
            res.setParticipantcontacted(participantcontacted);
            res.setCompletedsurvey(completedsurvey);

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
            boolean isskipped = rs.getBoolean("isskipped");

            Answer res = new Answer();
            res.setPid(pid);
            res.setSid(sid);
            res.setQid(qid);
            res.setGid(gid);
            res.setText(text);
            res.setSkipped(isskipped);
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

            String query = "INSERT INTO surveys(sid, adminmail, botname, expires, title) VALUES (?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, s.getSid());
            ps.setString(2, s.getAdminmail());
            ps.setString(3,s.getBotname());
            ps.setString(4,s.getExpires());
            ps.setString(5,s.getTitle());
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

    public static boolean addQuestionToDB(Question q, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO questions(qid, text, type, parentqid, gid, qorder, sid, help, relevance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, q.getQid());
            ps.setString(2, q.getText());
            ps.setString(3, q.getType());
            ps.setString(4, q.getParentqid());
            ps.setString(5, q.getGid());
            ps.setString(6, q.getQorder());
            ps.setString(7, q.getSid());
            ps.setString(8, q.getHelp());
            ps.setString(9, q.getRelevance());
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

    public static boolean addParticipantToDB(Participant p, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO participants(channel, email, pid, sid, lastquestion, lasttimeactive, surveyresponseid, participantcontacted, completedsurvey) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
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

    public static boolean addAnswerToDB(Answer answer, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO answers(pid, sid, qid, gid, text, isskipped) VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, answer.getPid());
            ps.setString(2, answer.getSid());
            ps.setString(3, answer.getQid());
            ps.setString(4, answer.getGid());
            ps.setString(5, answer.getText());
            ps.setBoolean(6, answer.isSkipped());
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
                ps = con.prepareStatement("UPDATE participants SET channel = ?, email = ?, pid = ?, sid = ?, lastquestion = ?, lasttimeactive = ?, surveyresponseid = ?, participantcontacted = ?, completedsurvey = ? WHERE sid = ? AND pid = ?");
                ps.setString(1, p.getChannel());
                ps.setString(2, p.getEmail());
                ps.setString(3, p.getPid());
                ps.setString(4, p.getSid());
                ps.setString(5, p.getLastquestion());
                ps.setString(6, p.getLasttimeactive());
                ps.setString(7, p.getSurveyResponseID());
                ps.setBoolean(8, p.isParticipantcontacted());
                ps.setBoolean(9, p.isCompletedsurvey());
                // where clause
                ps.setString(10, p.getSid());
                ps.setString(11,p.getPid());
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
            System.out.println("Could not add question.");
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
}
