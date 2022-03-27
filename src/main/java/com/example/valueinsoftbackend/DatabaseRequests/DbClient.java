package com.example.valueinsoftbackend.DatabaseRequests;


import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DbClient {

    private static String checkExistClientName(int comId, String cName, String phone) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"clientName\" , \"clientPhone\"\n" +
                    "\tFROM C_"+comId+".\"Client\" where  \"clientPhone\" = '" + phone + "' ;";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                return rs.getString(1) + " , Phone:" + rs.getString(2) + " ";


                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(" no user exist");
            return null;

        }
        return null;

    }


    public static ArrayList<Client> getClientByPhoneNumberOrName(int comId ,String phone, String name, Timestamp date, String shiftStartTime, int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (branchId == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" \"branchId\" =" + branchId + " And ");
            }

            if (name == null) {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM C_"+comId+".\"Client\" where "+stringBuilder+" \"clientPhone\" = '" + phone + "' ;";
            } else {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM C_"+comId+".\"Client\" where "+stringBuilder+" \"clientName\" LIKE '%" + name + "%' ;";
            }


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
               // System.out.println("add  connected to company " + rs.getTimestamp(7));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        null
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

    public static ArrayList<Client> getLatestClients(int comId , int max, int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (branchId == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" where \"branchId\" =" + branchId + "");
            }
            query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                    "\tFROM C_"+comId+".\"Client\" "+stringBuilder.toString()+" ORDER BY c_id DESC LIMIT  " + max + ";";


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
                        rs.getString(5),
                        //rs.getTimestamp(6)
                        null
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
    public static Client getClientById(int companyId, int bid, int clientId) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (bid == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" \"branchId\" =" + bid + " And ");
            }

            query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                    "\tFROM C_"+companyId+".\"Client\" where  "+stringBuilder+"  c_id = " + clientId + ";";


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
                        rs.getString(5),
                        //rs.getTimestamp(6)
                        null
                );

                System.out.println(cl);
                return cl;
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;
    }

    static public String AddClient(int comId,String clientName, String phoneNumber, int branchId, String gender, String desc) {
        try {

            String checkExist = checkExistClientName(comId ,clientName, phoneNumber);
            if (checkExist != null) {
                String[] parts = checkExist.split(" ,");
                System.out.println(parts[1]);
                if (parts[1].length() > 10) {
                    return "The " + parts[1] + " is taken by: " + parts[0];

                }
                return "The Client Name exist: " + checkExist;
            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO C_"+comId+".\"Client\"(\n" +
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

        return "the Client added! " + clientName;
    }

    //todo Update
    static public String updateClient(int comId, Client client, int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE C_"+comId+".\"Client\"\n" +
                    "\tSET  \"clientName\"=?, \"clientPhone\"=?, gender=?, description=?, \"branchId\"=?\n" +
                    "\tWHERE c_id =?;");

            stmt.setString(1, client.getClientName());
            stmt.setString(2, client.getClientPhone());
            stmt.setString(3, client.getGender());
            stmt.setString(4, client.getDescription());
            stmt.setInt(5, branchId);
            stmt.setInt(6, client.getClientId());

            int i = stmt.executeUpdate();
            System.out.println(i + " client update record ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the client not updates by error!";
        }
        return "the client updates with (ok 200)";
    }

    //todo -- delete
    public static boolean deleteClient(int comId, int clId, int branchId) {
        System.out.println("text -> " + clId + " " + branchId);
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "DELETE FROM C_"+comId+".\"Client\" \n" +
                    "\tWHERE c_id = " + clId + ";";

            PreparedStatement pstmt = null;
            pstmt = conn.prepareStatement(query);
            pstmt.executeUpdate();
            // create the java statement
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
            return false;
        }
        return true;
    }



}
