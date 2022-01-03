/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DbPosInventoryTransaction {

    public static ArrayList<InventoryTransaction> getInventoryTrans(int branchId ,String startDate, String endDate )
    {
        ArrayList<InventoryTransaction> inventoryTransactions = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"transId\", \"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\"\n" +
                    "\tFROM public.\"InventoryTransactions_"+branchId+"\" where \"time\" between '"+startDate+"' And '"+endDate+"'";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                InventoryTransaction inventoryTransaction = new InventoryTransaction(rs.getInt(1),rs.getInt(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getInt(6),rs.getInt(7),rs.getString(8),rs.getTimestamp(9),rs.getInt(10));
                inventoryTransactions.add(inventoryTransaction);



                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return inventoryTransactions;


        }catch (Exception e)
        {
            System.out.println("err in get invtrans : "+e.getMessage());

        }
        return null;

    }

    static public String AddTransactionToInv(int productId, String userName, int supplierId, String transactionType , int NumItems, int transTotal , String payType, Timestamp time , int remainingAmount, int branchId )
    {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt=conn.prepareStatement("INSERT INTO public.\"InventoryTransactions_"+branchId+"\"(\n" +
                    "\t \"productId\", \"userName\", \"supplierId\", \"transactionType\", \"NumItems\", \"transTotal\", \"payType\", \"time\", \"RemainingAmount\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?);");



            stmt.setInt(1,productId);
            stmt.setString(2,userName);
            stmt.setInt(3,supplierId);
            stmt.setString(4,transactionType);
            stmt.setInt(5,NumItems);
            stmt.setInt(6,transTotal);
            stmt.setString(7,payType);
            stmt.setTimestamp(8,time);
            stmt.setInt(9,remainingAmount);


            int i=stmt.executeUpdate();
            System.out.println(i+" supplier added records inserted");

            stmt.close();
            conn.close();

        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return "the supplier not added bs error!";

        }

        return "the supplier added! ok 200";
    }

}
