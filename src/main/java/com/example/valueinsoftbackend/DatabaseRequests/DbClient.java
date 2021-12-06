package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DbClient {

    private static String checkExistClientName(String cName,String phone)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"clientName\" , \"clientPhone\"\n" +
                    "\tFROM public.\"Clinet\" where  \"clientPhone\" = '"+phone+"' ;";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                return rs.getString(1) +" , Phone:"+rs.getString(2)+" ";


                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        }catch (Exception e)
        {
            System.out.println(" no user exist");
            return null;

        }
        return null;

    }


    public static ArrayList<Client> getClientByPhoneNumberOrName(String phone,String name,Timestamp date,String shiftStartTime ,int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query ="";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            if (name == null) {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM public.\"Clinet\" where \"branchId\" = "+branchId+" And \"clientPhone\" = '"+phone+"' ;";
            }else
            {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM public.\"Clinet\" where \"branchId\" ="+branchId+"  And \"clientName\" LIKE '%"+name+"%' ;";
            }


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)
                );
                cList.add(cl);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return cList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    public static ArrayList<Client> getLatestClients(int max ,int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query ="";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);


                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM public.\"Clinet\" where \"branchId\" ="+branchId+"  ORDER BY c_id DESC LIMIT  "+max+";";



            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5)
                );
                cList.add(cl);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return cList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    static public String AddClient(String clientName, String phoneNumber, int branchId ,String gender,String desc) {
        try {

            String checkExist = checkExistClientName(clientName,phoneNumber);
            if (checkExist!=null)
            {
                String [] parts = checkExist.split(" ,");
                System.out.println(parts[1]);
                if (parts[1].length()>10) {
                    return "The "+parts[1]+" is taken by: "+parts[0] ;

                }
                return "The Client Name exist: "+checkExist ;
            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"Clinet\"(\n" +
                    "\t \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?);");

            stmt.setString(1, clientName);
            stmt.setString(2, phoneNumber);
            stmt.setString(3, gender);
            stmt.setString(4, desc);
            stmt.setInt(5, branchId);
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added -> error in server!";

        }

        return "the Client added! "+clientName;
    }
}
