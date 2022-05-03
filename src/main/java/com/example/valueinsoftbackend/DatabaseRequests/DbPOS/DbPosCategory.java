package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.DatabaseRequests.DbSQL.DbSqlCloseIdles;
import com.example.valueinsoftbackend.Model.*;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.postgresql.util.PGobject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;
import java.util.ArrayList;

public class DbPosCategory {





    static public ArrayList<Category> getCategoriesByBranchId(int branchId ,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Category> categoryArrayList = new ArrayList<>();
            String query = "SELECT \"categoryId\", \"categoryName\", \"branchId\"\n" +
                    "\tFROM C_"+companyId+".\"PosCategory\" where \"branchId\" = " + branchId + " ;";
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
    static public boolean DeleteCategoryByBranchId(int branchId ,int companyId) {
        ArrayList<Category> categoryArrayList = getCategoriesByBranchId(branchId, companyId);


        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "DELETE FROM C_"+companyId+".\"PosCateJson\"" +
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

    public static ResponseEntity<String> AddCategoryJson(int branchId, String s , int companyId) {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            String query = "INSERT INTO C_"+companyId+".\"PosCateJson\"(\n" +
                    "\t \"CategoryData\", \"BranchId\")\n" +
                    "\tVALUES ( ?, ?);";

            PreparedStatement stmt = conn.prepareStatement(query);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(s);
            stmt.setObject(1, jsonObject);

            stmt.setInt(2, branchId);
            System.out.println(stmt);
            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted in cteg");
            stmt.close();
            conn.close();


        } catch (Exception e) {
            System.out.println(e.getMessage());
            return  ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("the Category not added by error!");
        }
        return  ResponseEntity.status(HttpStatus.CREATED).body("the Category added ");

    }

    static public String getCategoryJson(int branchId,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Category> categoryArrayList = new ArrayList<>();
            String query = "SELECT \"CategoryJID\", \"CategoryData\", \"BranchId\"\n" +
                    "\tFROM C_"+companyId+".\"PosCateJson\" where \"BranchId\" = " + branchId + " " +
                    "ORDER BY  \"CategoryJID\" DESC \n" +
                    "\tLIMIT 1" +
                    ";";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            String payload = "";
            while (rs.next()) {
                payload = rs.getString(2);
            }
            rs.close();
            st.close();
            conn.close();
            System.out.println(query);
            return payload;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return null;
    }


    static public ArrayList<MainMajor> getMainMajors(int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Category> categoryArrayList = new ArrayList<>();
            String query = "SELECT \"MId\", \"CateName\", \"AppType\"" +
                    "\tFROM c_"+companyId+".\"MainMajor\" ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            String payload = "";
            ArrayList<MainMajor> MainMajors = new ArrayList<>();
            try {

                while (rs.next()) {
                    MainMajor mainMajor = new MainMajor(rs.getInt(1),rs.getString(2),rs.getString(3));
                    MainMajors.add(mainMajor);
                    System.out.println(rs.getString(2));
                }
            }catch(Exception e)
            {
                System.out.println("Inside operation"+ e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return MainMajors;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;
    }
}
