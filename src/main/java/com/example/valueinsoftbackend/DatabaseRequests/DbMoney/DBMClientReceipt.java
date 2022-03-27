/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbMoney;

import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DBMClientReceipt {

    static public ArrayList<ClientReceipt> getClientReceipts(int companyId,int  clientId )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"crId\", type, amount::money::numeric::float8, \"time\", \"userName\", \"clientId\" , \"branchId\"\n" +
                    "\tFROM c_"+companyId+".\"ClientReceipts\" where \"clientId\" =  "+clientId+" ;";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<ClientReceipt> clientReceipts = new ArrayList<>();
            System.out.println(query);
            while (rs.next())
            {
                ClientReceipt clientReceiptIn = new ClientReceipt(rs.getInt(1),rs.getString(2),rs.getBigDecimal(3),rs.getTimestamp(4),rs.getString(5),rs.getInt(6),rs.getInt(7));
                clientReceipts.add(clientReceiptIn);
            }
            rs.close();
            st.close();
            conn.close();
            return clientReceipts;

        }catch (Exception e)
        {
            System.out.println(" no user exist"+e.getMessage());
            return null;

        }

    }
    public static ArrayList<ClientReceipt> getClientReceiptsByTime(int companyId,int branchId, String startTime, String endTime) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"crId\", type, amount::money::numeric::float8, \"time\", \"userName\", \"clientId\" , \"branchId\"\n" +
                    "\tFROM c_"+companyId+".\"ClientReceipts\" where \"branchId\" = "+branchId+" AND \"time\" between '"+startTime+"' And '"+endTime+"'  ;";
            // create the java statement
            System.out.println(query);

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<ClientReceipt> clientReceipts = new ArrayList<>();
            while (rs.next())
            {
                ClientReceipt clientReceiptIn = new ClientReceipt(rs.getInt(1),rs.getString(2),rs.getBigDecimal(3),rs.getTimestamp(4),rs.getString(5),rs.getInt(6),rs.getInt(7));
                clientReceipts.add(clientReceiptIn);
            }
            rs.close();
            st.close();
            conn.close();
            return clientReceipts;

        }catch (Exception e)
        {
            System.out.println(" no user exist"+e.getMessage());
            return null;

        }
    }

    static public String AddClientReceipt(int companyId,ClientReceipt clientReceipt) {
        try {

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO c_"+companyId+".\"ClientReceipts\"(\n" +
                    "\t type, amount, \"time\", \"userName\", \"clientId\", \"branchId\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?,?);");

            stmt.setString(1, clientReceipt.getType());
            stmt.setBigDecimal(2, clientReceipt.getAmount());
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.setString(4, clientReceipt.getUserName());
            stmt.setInt(5, clientReceipt.getClientId());
            stmt.setInt(6, clientReceipt.getBranchId());

            int i = stmt.executeUpdate();
            System.out.println(i + " record inserted to AddClientReceipt");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the ReceiptUser not added -> error in server!";

        }

        return "the Client Receipt Added Successfully : " + clientReceipt.getClientId();
    }


}
