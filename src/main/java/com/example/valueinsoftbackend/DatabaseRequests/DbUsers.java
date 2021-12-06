package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;

public class DbUsers {


    private static boolean checkExistUsername(String userName)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"userName\"\n" +
                    "\tFROM public.users where \"userName\" = '"+userName+"';";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                rs.close();
                st.close();
                conn.close();
                return true;
            }

        }catch (Exception e)
        {
            System.out.println(" no user exist");
            return true;

        }
        return false;

    }

    public static User getUser(String userName )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT id, \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\"" +
                    "\tFROM public.users where \"userName\" = '"+userName+"';";

            String qu1 = "SELECT * FROM public.users\n" +
                    "ORDER BY id ASC ";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                System.out.println("Get user By Name  "+rs.getString(2));
                System.out.println("Get user By Name  "+rs.getString(3));
                System.out.println("Get user By Name  "+rs.getString(5));


                User user = new User(rs.getInt(1), rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(8),rs.getString(9),rs.getString(6),rs.getString(5),rs.getInt(10),rs.getInt(7),rs.getTimestamp(11) );
                rs.close();
                st.close();
                conn.close();
                return user;

                // print the results
            }


        }catch (Exception e)
        {
            System.out.println("err in get user : "+e.getMessage());

        }
        return null;

    }

    static public String AddUser(String username,String password, String email, String role,String fName,String  lName, int gender,String userPhone,int branchId )
    {
        try {
            if (checkExistUsername(username))
            {
                return "The user exist! in the db";
            }

            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt=conn.prepareStatement("INSERT INTO public.users(\n" +
                    "\t \"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");



            stmt.setString(1,username);
            stmt.setString(2,password);
            stmt.setString(3,email);
            stmt.setString(4,role);
            stmt.setString(5,userPhone);
            stmt.setInt(6,branchId);
            stmt.setString(7,fName);
            stmt.setString(8,lName);
            stmt.setInt(9,gender);
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));

            int i=stmt.executeUpdate();
            System.out.println(i+" records inserted");

            stmt.close();
            conn.close();

        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the user added!";
    }


}
