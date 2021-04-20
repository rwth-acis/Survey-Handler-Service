package i5.las2peer.services.SurveyHandler.database;

import com.google.common.collect.Lists;
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
                    query += "botname VARCHAR(50) NOT NULL,";
                    query += "expires VARCHAR(50) NOT NULL,";
                    query += "title VARCHAR(50) NOT NULL";
                    break;
                case "questions":
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(50) NOT NULL,";
                    query += "type VARCHAR(50) NOT NULL,";
                    query += "parentqid VARCHAR(50),";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "qorder VARCHAR(50) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL";
                    break;
                case "participants":
                    query += "pid VARCHAR(50) NOT NULL,";
                    query += "email VARCHAR(50) NOT NULL,";
                    query += "channel VARCHAR(50),";
                    query += "sid VARCHAR(50) NOT NULL";
                    break;
                case "answers":
                    query += "pid VARCHAR(50) NOT NULL,";
                    query += "sid VARCHAR(50) NOT NULL,";
                    query += "qid VARCHAR(50) NOT NULL,";
                    query += "gid VARCHAR(50) NOT NULL,";
                    query += "text VARCHAR(500),"; // Huge free text is answer option by limesurvey
                    query += "isAnswered BOOL";
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
/*
    public static ArrayList<Participant> getParticipantsOfSurvey(String sid, SQLDatabase database){

    }



    public static void initAllDataFromDB(SQLDatabase database){

    }
 */

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
                return null;
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

    // getting objects from database
    public static Survey castToSurvey(ResultSet rs){
        try{
            String sid = rs.getString("sid");
            String adminmail = rs.getString("adminmail");
            String botname = rs.getString("botname");
            String expires = rs.getString("expires");
            String title = rs.getString("title");


            Survey res = new Survey(sid);
            res.setAdminMail(adminmail);
            res.setBotName(botname);
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

            Question res = new Question();
            res.setQid(qid);
            res.setGid(gid);
            res.setParentqid(parentqid);
            res.setText(text);
            res.setType(type);
            res.setQorder(qorder);
            res.setSid(sid);
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

            Participant res = new Participant(email);
            res.setPid(pid);
            res.setSid(sid);
            res.setChannel(channel);

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
            ps.setString(1, s.getID());
            ps.setString(2, s.getAdminMail());
            ps.setString(3,s.getBotName());
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

            String query = "INSERT INTO questions(qid, text, type, parentqid, gid, qorder, sid) VALUES (?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, q.getQid());
            ps.setString(2, q.getText());
            ps.setString(3, q.getType());
            ps.setString(4, q.getParentqid());
            ps.setString(5, q.getGid());
            ps.setString(6, q.getQorder());
            ps.setString(7, q.getSid());
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

            String query = "INSERT INTO participants(channel, email, pid, sid) VALUES (?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, p.getChannel());
            ps.setString(2, p.getEmail());
            ps.setString(3, p.getPid());
            ps.setString(4, p.getSid());
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

    public static boolean addAnswerToDB(String pid, String sid, String qid, String gid, String text, SQLDatabase database){
        try{
            Connection con = database.getDataSource().getConnection();
            PreparedStatement ps = null;

            String query = "INSERT INTO answers(pid, sid, qid, gid, text) VALUES (?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setString(1, pid);
            ps.setString(2, sid);
            ps.setString(3, qid);
            ps.setString(4, gid);
            ps.setString(5, text);
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
