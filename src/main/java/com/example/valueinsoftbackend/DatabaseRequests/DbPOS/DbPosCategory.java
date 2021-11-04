package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Category;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.SubCategory;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DbPosCategory {

    static public String AddCategory(ArrayList<Category> cateList) {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            String query = "INSERT INTO public.\"PosCategory\"(\n" +
                    " \"categoryName\", \"branchId\")\n" +
                    "\tVALUES   ";


            StringBuilder qy = new StringBuilder(query);
            for (int i = 0; i < cateList.size(); i++) {
                qy.append("('" + cateList.get(i).getName().trim() + "' , " + cateList.get(i).getBranchId() + " )");
                if (i < cateList.size() - 1) {
                    qy.append(" , ");

                }

            }
            qy.append(";");

            System.out.println(qy.toString());
            PreparedStatement stmt = conn.prepareStatement(qy.toString());
            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted in cteg");
            conn.close();


        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the Branch added!";
    }

    public static int getCategoryIdByBranchIdAndCateName(int branchId, String categoryName) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT \"categoryId\"" +
                    "\tFROM public.\"PosCategory\" where \"branchId\" = " + branchId + " And  \"categoryName\" = '" + categoryName + "';";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                System.out.println(rs.getInt(1));

                return rs.getInt(1);
                // print the results
            }


            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return -1;

    }

    static public String AddSubCategory(ArrayList<Category> cateList) {
        try {


            Connection conn = ConnectionPostgres.getConnection();

            String query = "INSERT INTO public.\"PosSubCatigories\"(\n" +
                    "\t \"Name\", \"categoryId\")\n" +
                    "\tVALUES  ";


            StringBuilder qy = new StringBuilder(query);
            for (int i = 0; i < cateList.size(); i++) {
                for (int j = 0; j < cateList.get(i).getSubCategories().size(); j++) {
                    String[] arr = cateList.get(i).getSubCategories().get(j).getNames().get(j).trim().replace("[", "").split(",");

                    System.out.println("bid: " + cateList.get(i).getBranchId() + " catename: " + cateList.get(i).getName());
                    for (int k = 0; k < arr.length; k++) {
                        System.out.println("-> " + getCategoryIdByBranchIdAndCateName(cateList.get(i).getBranchId(), cateList.get(i).getName()));
                        qy.append("('" + arr[k].replace(']', ' ').trim() + "' , " + getCategoryIdByBranchIdAndCateName(cateList.get(i).getBranchId(), cateList.get(i).getName()) + " )");
                        if (k < arr.length - 1) {
                            qy.append(" , ");

                        }
                    }
                    if (i < cateList.size() - 1) {
                        qy.append(" , ");

                    }
                }


            }
            qy.append(";");

            System.out.println(qy.toString());
            PreparedStatement stmt = conn.prepareStatement(qy.toString());
            int i = stmt.executeUpdate();
            //System.out.println(i + " records inserted in cteg");
            conn.close();


        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

        return "the Branch added!";
    }

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
            st.close();
            conn.close();
            return categoryArrayList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return null;
    }

    static public SubCategory getSubCategoriesByCategoryId(int cateId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<SubCategory> subcategoryArrayList = new ArrayList<>();
            String query = "SELECT \"sCId\", \"Name\", \"categoryId\"\n" +
                    "\tFROM public.\"PosSubCatigories\" where \"categoryId\" = " + cateId + " ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList subs = new ArrayList<String>();

            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                subs.add(rs.getString(2));
            }
            SubCategory subcategory = new SubCategory(0, subs, cateId);
            st.close();
            conn.close();
            return subcategory;


        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return null;
    }



    //------------------------Delete------------------------------
    static public boolean DeleteCategoryByBranchId(int branchId)
    {
        ArrayList<Category>categoryArrayList = getCategoriesByBranchId(branchId);


        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "DELETE FROM public.\"PosCategory\"\n" +
                    "\tWHERE \"branchId\" = "+branchId+" ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            st.close();
            conn.close();


        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return false;
    }
    static public boolean DeleteSubCategoryByCateId(int cateId)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "Delete from public.\"PosSubCatigories\" where \"categoryId\" = "+cateId;
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            st.close();
            conn.close();


        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
        }
        return false;
    }

}
