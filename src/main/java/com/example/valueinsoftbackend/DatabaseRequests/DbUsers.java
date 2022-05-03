package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;

public class DbUsers {


    public static boolean checkExistUsername(String userName) {


        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"userName\"\n" +
                    "\tFROM public.users where \"userName\" = '" + userName + "';";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                rs.close();
                st.close();
                conn.close();
                return true;
            }

        } catch (Exception e) {
            System.out.println(" no user exist");
            return true;

        }
        return false;

    }

    public static boolean checkExistingEmail(String email) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"userEmail\"\n" +
                    "\tFROM public.users where \"userEmail\" = '" + email + "';";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                rs.close();
                st.close();
                conn.close();
                return true;
            }

        } catch (Exception e) {
            System.out.println(" no user exist");
            return true;

        }
        return false;
    }

    public static User getUser(String userName) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT id, \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\", \"imgFile\"" +
                    "\tFROM public.users where \"userName\" = '" + userName + "';";

            String qu1 = "SELECT * FROM public.users\n" +
                    "ORDER BY id ASC ";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("Get user By Name  " + rs.getString(2));
                System.out.println("Get user By Name  " + rs.getString(3));
                System.out.println("Get user By Name  " + rs.getString(5));


                User user = new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(8), rs.getString(9), rs.getString(6), rs.getString(5), rs.getInt(10), rs.getInt(7), rs.getTimestamp(11));
                rs.close();
                st.close();
                conn.close();
                return user;

                // print the results
            }


        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());

        }
        return null;

    }

    public static User getUserDetails(String userName) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT id, \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\"" +
                    "\tFROM public.users where \"userName\" = '" + userName + "';";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                User user = new User(rs.getInt(1), rs.getString(2), "", rs.getString(4), rs.getString(8), rs.getString(9), rs.getString(6), rs.getString(5), rs.getInt(10), rs.getInt(7), rs.getTimestamp(11));
                rs.close();
                st.close();
                conn.close();
                return user;
            }
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
        }
        return null;

    }

    public static ArrayList<User> getAllUsers(int branchId, int companyId) {
        String condition = "";
        StringBuilder stringBuilder = new StringBuilder("");
        if (branchId == 0) {
            condition = "";
        } else {
            condition = " where  \"branchId\" = " + branchId;
        }

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT id, \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\"\n" +
                    "\tFROM public.users " + condition + " ORDER BY id ASC ;  ";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<User> users = new ArrayList<>();
            System.out.println(query);
            while (rs.next()) {
                User user = new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(8), rs.getString(9), rs.getString(6), rs.getString(5), rs.getInt(10), rs.getInt(7), rs.getTimestamp(11));
                users.add(user);
                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return users;

        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());

        }
        return null;

    }


    public static String getUserImg(String userName) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"imgFile\"" +
                    "\tFROM public.users where \"userName\" = '" + userName + "';";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {

                return rs.getString(1);

                // print the results
            }
            rs.close();
            st.close();


        } catch (Exception e) {
            System.out.println("err in get user img : " + e.getMessage());

        }
        return "";
    }

    static public String AddUser(String username, String password, String email, String role, String fName, String lName, int gender, String userPhone, int branchId, String imgFile) {
        try {
            if (checkExistUsername(username)) {
                return "The user exist! in the db";
            }
            if (checkExistingEmail(email)) {
                return "The email exist! in the db";
            }
            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.users(\n" +
                    "\t \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\",\"imgFile\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?);");


            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.setString(4, role);
            stmt.setString(5, userPhone);
            stmt.setInt(6, branchId);
            stmt.setString(7, fName);
            stmt.setString(8, lName);
            stmt.setInt(9, gender); //0 for female anf 1 for male
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            stmt.setString(11, imgFile);

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");

            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());

            return "the user not added bs error!";


        }

        return "the user added!";
    }

    static public String UpdateRole(String schemaName, int userId, String newRole) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE " + schemaName + ".users\n" +
                    "\tSET  \"userRole\"=?\n" +
                    "\tWHERE id=?;");
            stmt.setString(1, newRole);
            stmt.setInt(2, userId);
            int i = stmt.executeUpdate();
            System.out.println(i + " records Role Updated");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not Updated bs error!";
        }
        return "the user Role Updated!";
    }

    static public ResponseEntity<String> UpdateUserPassword(String userName, String oldPassword, String userPassword) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE public.users\n" +
                    "\tSET \"userPassword\"= '"+userPassword+"'\n" +
                    "\tWHERE \"userName\"='"+userName+"' And \"userPassword\"='"+oldPassword+"' ;");


            int i = stmt.executeUpdate();
            stmt.close();
            conn.close();
            System.out.println(stmt);

            if (i == 1) {
                System.out.println(i+ " records userName Updated");

                return ResponseEntity.status(HttpStatus.ACCEPTED).body("The Password Changed!");

            }else
            {
                System.out.println(i+ " Not records userName Updated");

                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("The Old Password incorrect! ");

            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("Server Error! ");
        }
    }
    static public ResponseEntity<String> UpdateUserImg(String userName, String img) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE public.users\n" +
                    "\tSET \"imgFile\"= '"+img+"'\n" +
                    "\tWHERE \"userName\" = '"+userName+"';");


            int i = stmt.executeUpdate();
            stmt.close();
            conn.close();
            System.out.println(stmt);

            if (i == 1) {
                System.out.println(i+ " records userName Updated");

                return ResponseEntity.status(HttpStatus.ACCEPTED).body("The Password Changed!");

            }else
            {
                System.out.println(i+ " Not records userName Updated");

                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("The Old Password incorrect! ");

            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("Server Error! ");
        }
    }

}
