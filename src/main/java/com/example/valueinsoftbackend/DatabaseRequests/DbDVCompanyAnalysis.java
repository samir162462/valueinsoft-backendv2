/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class DbDVCompanyAnalysis {

    //todo Get
    public static ArrayList<CompanyAnalysis> getCompanyAnalysis(int branchId, int companyId, String byWhat , String date) { // byWhat = month
        ArrayList<DamagedItem> damagedItems = new ArrayList<>();
        String byWhatIn = "";
        if (byWhat == null || byWhat == "") {
            byWhatIn ="month";
        }else {
            byWhatIn= byWhat;
        }
        String withDate = "";
        if (date != null) withDate = " And DATE_TRUNC('"+byWhatIn+"',date)::date = '"+date+"' ";
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT  Sum(sales) as sales, Sum(\"Income\") as income, Sum(\"clientsIn\")as clientsIn , Sum(\"invShortage\")as invShortage, Sum(\"discountByUsers\") as discountByUsers, Sum(\"damagedProducts\") as damagedProducts , Sum(\"returnPurchases\") as returnPurchases, Sum(\"shiftEndsEarly\") as shiftEndsEarly \n" +
                    "\t, DATE_TRUNC('"+byWhatIn+"',date)::date as dateM\n" +
                    "\t,Count(\"branchId\") as numOfDays\n" +
                    "\tFROM C_"+companyId+".\"CompanyAnalysis\"\n" +
                    "\twhere \"branchId\" = "+branchId+"\n" +
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
