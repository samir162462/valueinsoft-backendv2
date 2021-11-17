package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Category;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.SubCategory;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;

public class DbPosCategory {





    static public ArrayList<Category> getCategoriesByBranchId(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Category> categoryArrayList = new ArrayList<>();
            String query = "SELECT \"categoryId\", \"categoryName\", \"branchId\"\n" +
                    "\tFROM public.\"PosCategory\" where \"branchId\" = " + branchId + " ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Category category = new Category(rs.getInt(1), rs.getString(2), rs.getInt(3), null);
                categoryArrayList.add(category);
            }
            rs.close();
            st.close();
            conn.close();
            return categoryArrayList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return null;
    }



    //------------------------Delete------------------------------
    static public boolean DeleteCategoryByBranchId(int branchId) {
        ArrayList<Category> categoryArrayList = getCategoriesByBranchId(branchId);


        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "DELETE FROM public.\"PosCateJson\"" +
                    "\tWHERE \"BranchId\" = " + branchId + " ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            rs.close();
            st.close();
            conn.close();


        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return false;
    }


    //json--------

    public static String AddCategoryJson(int branchId, String s) {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            String query = "INSERT INTO public.\"PosCateJson\"(\n" +
                    "\t \"CategoryData\", \"BranchId\")\n" +
                    "\tVALUES ( ?, ?);";

            PreparedStatement stmt = conn.prepareStatement(query);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(s);
            stmt.setObject(1, jsonObject);

            stmt.setInt(2, branchId);

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted in cteg");
            stmt.close();
            conn.close();


        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the Branch added!";
    }

    static public String getCategoryJson(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Category> categoryArrayList = new ArrayList<>();
            String query = "SELECT \"CategoryJID\", \"CategoryData\", \"BranchId\"\n" +
                    "\tFROM public.\"PosCateJson\" where \"BranchId\" = " + branchId + " ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            String payload = "";
            while (rs.next()) {
                payload = rs.getString(2);
            }
            rs.close();
            st.close();
            conn.close();
            return payload;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return null;
    }
}
