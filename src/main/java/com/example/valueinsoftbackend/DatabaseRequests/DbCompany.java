package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;

public class DbCompany {


    public static Company getCompanyByOwnerId(String id) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            ArrayList<Branch> bsList = new ArrayList<>();
            Company company = null;
            String query = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\"\n" +
                    "\tFROM public.\"Company\" where \"ownerId\" = " + id + ";";

            String qu1 = "SELECT * FROM public.users\n" +
                    "ORDER BY id ASC ";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));


                company = new Company(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(5), rs.getInt(6), null);

                // print the results

            }


            bsList = DbBranch.getBranchByCompanyId(company.getCompanyId());
            company.setBranchList(bsList);
            rs.close();
            st.close();
            conn.close();
            return company;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    public static Company getCompanyById(String id) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            ArrayList<Branch> bsList = new ArrayList<>();
            Company company = null;
            String query = "SELECT id, \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\"\n" +
                    "\tFROM public.\"Company\" where \"id\" = " + id + ";";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));


                company = new Company(rs.getInt(1), rs.getString(2), rs.getTimestamp(3), rs.getString(5), rs.getInt(6), null);

                // print the results
            }



            bsList = DbBranch.getBranchByCompanyId(company.getCompanyId());
            company.setBranchList(bsList);
            rs.close();
            st.close();
            conn.close();
            return company;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }

    static public String AddCompany(String companyName, String branchName, String plan, int price, String username) {
        try {

            int ownerId = 0;
            User u1 = DbUsers.getUser(username);
            ownerId = u1.getUserId();


            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"Company\"(\n" +
                    " \"companyName\", \"establishedTime\", \"ownerId\", \"planName\", \"planPrice\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?);");

            stmt.setString(1, companyName);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3, ownerId);
            stmt.setString(4, plan);
            stmt.setInt(5, price);

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

}
