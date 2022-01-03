/*
 * Copyright (c) Samir Filifl 
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.google.gson.JsonObject;

import java.sql.*;
import java.util.ArrayList;

public class DbSupplier {

    public static ArrayList<Supplier> getSuppliers(int branchId )
    {
        ArrayList<Supplier> supList = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"supplierId\", \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\"\n" +
                    "\tFROM public.\"supplier_"+branchId+"\";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                Supplier sup = new Supplier(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5));
                supList.add(sup);



                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return supList;


        }catch (Exception e)
        {
            System.out.println("err in get user : "+e.getMessage());

        }
        return null;

    }

    static public String AddSupplier(String name,String phone1, String phone2, String loaction ,int branchId )
    {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt=conn.prepareStatement("INSERT INTO public.supplier_"+branchId+"(\n" +
                    "\t \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\")\n" +
                    "\tVALUES ( ?, ?, ?, ?);");



            stmt.setString(1,name);
            stmt.setString(2,phone1);
            stmt.setString(3,phone2);
            stmt.setString(4,loaction);


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


    //Supplier service -------------
    public static JsonObject getRemainingSupplierAmountByProductId(int id, int branchId )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT public.\"PosProduct_"+branchId+"\".\"productId\",public.\"InventoryTransactions_"+branchId+"\".\"time\",public.\"InventoryTransactions_"+branchId+"\".\"payType\" as payType , public.\"InventoryTransactions_"+branchId+"\".\"RemainingAmount\" as remainingAmount\n" +
                    "FROM public.\"PosProduct_"+branchId+"\" \n" +
                    "INNER JOIN\n" +
                    "    public.\"InventoryTransactions_"+branchId+"\" \n" +
                    "ON\n" +
                    "   public.\"PosProduct_"+branchId+"\".\"productId\" = public.\"InventoryTransactions_"+branchId+"\".\"productId\" where public.\"PosProduct_"+branchId+"\".\"productId\" = "+id+" ORDER BY public.\"InventoryTransactions_"+branchId+"\".\"time\" DESC LIMIT 1 ; ";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            JsonObject json = new JsonObject();

            while (rs.next())
            {
                // print the results
                json.addProperty("productId",rs.getInt(1));
                json.addProperty("time",rs.getString(2));
                json.addProperty("payType",rs.getString(3));
                json.addProperty("remainingAmount",rs.getInt(4));

            }

            System.out.println(json.toString());
            rs.close();
            st.close();
            conn.close();
            return  json;

        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }

    }
}
