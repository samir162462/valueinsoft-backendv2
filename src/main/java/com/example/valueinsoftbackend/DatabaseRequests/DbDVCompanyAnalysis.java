/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class DbDVCompanyAnalysis {

    //TODO CHECK time in table


    public static boolean isTableTimeRecordBranchDVCA(int branchId,int companyId ) {
        try {
            System.out.println("isTableTimeRecordBranchDVCA inside");
            Connection conn = ConnectionPostgres.getConnection();

            String query = "select exists(SELECT  DATE_TRUNC('day',date)::date\n" +
                    "\tFROM C_"+companyId+".\"CompanyAnalysis\"\n" +
                    "\twhere \"branchId\" = "+branchId+"\n" +
                    " And DATE_TRUNC('day',date)::date = CURRENT_DATE )";

            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next())
            {
                if (rs.getBoolean(1) == true) { return true; }else { return false; }
            }
            rs.close();
            st.close();
            conn.close();
        }catch (Exception e)
        {
            System.out.println("isTableTimeRecordBranchDVCA error");
            return true;
        }
        return false;
    }


    static public String AddRecordBranchDVCA(int branchId,int companyId )
    {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt=conn.prepareStatement("INSERT INTO c_"+companyId+".\"CompanyAnalysis\"(\n" +
                    "\t sales, \"Income\", \"clientsIn\", \"invShortage\", \"discountByUsers\", \"damagedProducts\", \"returnPurchases\"," +
                    " \"shiftEndsEarly\", date, \"branchId\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?);");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            LocalDateTime now = LocalDateTime.now();
            System.out.println(dtf.format(now));
            stmt.setInt(1,0);
            stmt.setInt(2,0);
            stmt.setInt(3,0);
            stmt.setInt(4,0);
            stmt.setInt(5,0);
            stmt.setInt(6,0);
            stmt.setInt(7,0);
            stmt.setInt(8,0);
            stmt.setInt(9, branchId);
            int i=stmt.executeUpdate();
            System.out.println(i+" AddRecordBranchDVCA");
            stmt.close();
            conn.close();
        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return "the AddRecordBranchDVCA not added bs error!";
        }
        return "the AddRecordBranchDVCA added!";
    }

    // todo Update
    static public String UpdateRecordBranchDVCAIncrement(int branchId,int companyId,int sales, int income, int clientIn, int invShortage,
                                                           int discountByUser,int damagedProducts, int returnPurchases ,int shiftEndsEarly )
    {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            PreparedStatement stmt=conn.prepareStatement("UPDATE c_"+companyId+".\"CompanyAnalysis\"\n" +
                    "\tSET  sales=sales+?, \"Income\"=\"Income\"+?, \"clientsIn\"=\"clientsIn\"+?, \"invShortage\"=\"invShortage\"+?, \"discountByUsers\"=\"discountByUsers\"+?,\n" +
                    "\t\"damagedProducts\"=\"damagedProducts\"+?, \"returnPurchases\"=\"returnPurchases\"+?, \"shiftEndsEarly\"=\"shiftEndsEarly\"+?\n" +
                    "\tWHERE DATE_TRUNC('day',date)::date = CURRENT_DATE AND \"branchId\"="+branchId+" ;");



            stmt.setInt(1,sales);//sales
            stmt.setInt(2,income);//income
            stmt.setInt(3,clientIn);//clientIn
            stmt.setInt(4,invShortage);//invShortage
            stmt.setInt(5,discountByUser);//discountByUser
            stmt.setInt(6,damagedProducts);//damagedProducts
            stmt.setInt(7,returnPurchases);//returnPurchases
            stmt.setInt(8,shiftEndsEarly);//shiftEndsEarly

            int i=stmt.executeUpdate();
            System.out.println(i+" records Role Updated");

            stmt.close();
            conn.close();

        }catch (Exception e )
        {
            System.out.println(e.getMessage());
            return "the user not Updated bs error!";

        }

        return "the user Role Updated!";
    }




    //todo Get
    public static ArrayList<CompanyAnalysis> getCompanyAnalysis(int branchId, int companyId, String byWhat , String date) { // byWhat = month
        ArrayList<DamagedItem> damagedItems = new ArrayList<>();
        String byWhatIn = "";
        if (byWhat == null || byWhat == "") {
            byWhatIn ="month";
        }else {
            byWhatIn= byWhat;
        }
        String branchIn = "";
        if (branchId!=0)
        {
            branchIn = " \"branchId\" = "+branchId+"\n And ";
        }
        String withDate = "";
        if (date != null) withDate = "  DATE_TRUNC('"+byWhatIn+"',date)::date = '"+date+"' ";
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT  Sum(sales) as sales, Sum(\"Income\") as income, Sum(\"clientsIn\")as clientsIn , Sum(\"invShortage\")as invShortage, Sum(\"discountByUsers\") as discountByUsers, Sum(\"damagedProducts\") as damagedProducts , Sum(\"returnPurchases\") as returnPurchases, Sum(\"shiftEndsEarly\") as shiftEndsEarly \n" +
                    "\t, DATE_TRUNC('"+byWhatIn+"',date)::date as dateM\n" +
                    "\t,Count(\"branchId\") as numOfDays\n" +
                    "\tFROM C_"+companyId+".\"CompanyAnalysis\"\n" +
                    "\twhere "+branchIn +
                    withDate+
                    "\tGROUP BY dateM\n" +
                    "\t;";
            System.out.println(query);

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<CompanyAnalysis> companyAnalysisArrayList = new ArrayList<>();
            while (rs.next()) {
                CompanyAnalysis companyAnalysis = new CompanyAnalysis(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getInt(7),
                        rs.getInt(8),
                        rs.getDate(9),
                        rs.getInt(10)
                );
                companyAnalysisArrayList.add(companyAnalysis);
            }
            rs.close();
            st.close();
            conn.close();
            return companyAnalysisArrayList;
        } catch (Exception e) {
            System.out.println("err in get companyAnalysisArrayList : " + e.getMessage());
        }
        return null;
    }




}
