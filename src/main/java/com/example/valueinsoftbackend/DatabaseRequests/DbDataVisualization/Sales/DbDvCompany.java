/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

public class DbDvCompany {


    static public JsonArray getShiftTotalAndIncomeOfAllBranches(int compId,ArrayList<Branch>branchArrayList,String hours)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            StringBuilder stringBuilder = new StringBuilder("");

            for (int i = 0; i < branchArrayList.size(); i++) {
                Branch branch = branchArrayList.get(i);
                int branchId = +branch.getBranchID();
                stringBuilder.append("SELECT * from (select  sum(C_"+compId+".\"PosOrder_"+branchId+"\".\"orderTotal\") as sumTotal ,  sum(C_"+compId+".\"PosOrder_"+branchId+"\".\"orderIncome\") as sumincome , count(C_"+compId+".\"PosOrder_"+branchId+"\".\"orderId\") as countProducts, (now()::date + interval '"+hours+"') as Time  from C_"+compId+".\"PosOrder_"+branchId+"\" where  C_"+compId+".\"PosOrder_"+branchId+"\".\"orderTime\">= now()::date + interval '"+hours+"') a\n");
                if (i < branchArrayList.size()-1) {
                    stringBuilder.append("union All ");
                }
            }
            stringBuilder.append(" ;");
            System.out.println(stringBuilder);



            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(stringBuilder.toString());

            JsonArray jsonArray =new JsonArray();
            int index = 0;
            while (rs.next())
            {
                JsonObject json = new JsonObject();
                json.addProperty("sumTotal",rs.getInt(1));
                json.addProperty("sumIncome",rs.getInt(2));
                json.addProperty("countOrders",rs.getInt(3));
                json.addProperty("fromTime",rs.getString(4));
                json.addProperty("branchLocation",branchArrayList.get(index).getBranchLocation());
                JsonObject jsonObject= new JsonObject();
                jsonObject.add(branchArrayList.get(index).getBranchName(),json);
                jsonArray.add(jsonObject);
                index++;
            }
            rs.close();
            st.close();
            conn.close();

            //todo
            System.out.println(jsonArray.toString());

            return jsonArray;

        }catch (Exception e)
        {
            System.out.println(" Dv-> "+e.getMessage());
            return null;

        }

    }




    static public ArrayList<DvCompanyChartSalesIncome> getShiftTotalAndIncomeOfAllBranchesPerDay(int compId,ArrayList<Branch>branchArrayList,String hours)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            StringBuilder stringBuilder = new StringBuilder("");

            for (int i = 0; i < branchArrayList.size(); i++) {
                Branch branch = branchArrayList.get(i);
                int branchId = +branch.getBranchID();
                stringBuilder.append("SELECT ("+branchId+") as branchId, CONCAT(Date_Part('month',\"orderTime\"),'-',Date_Part('day',\"orderTime\")) as daym ,sum(\"orderTotal\") as orderTotal,sum(\"orderIncome\") as orderIncome \n" +
                        "\tFROM C_"+compId+".\"PosOrder_"+branchId+"\" where C_"+compId+".\"PosOrder_"+branchId+"\".\"orderTime\">= now()::date + interval '"+hours+"'  GROUP BY daym  ");
                if (i < branchArrayList.size()-1) {
                    stringBuilder.append("union All ");
                }
            }
            stringBuilder.append(" ;");
            System.out.println(stringBuilder);



            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(stringBuilder.toString());

            JsonArray jsonArray =new JsonArray();
            int prev_id = 0;
            int localID = 0;

            ArrayList<DvCompanyChartSalesIncome>dvCompanyChartSalesIncomeArrayList = new ArrayList<>();
            ArrayList<Integer> sumTotal =new ArrayList<>();
            ArrayList<Integer> sumIncome = new ArrayList<>();
            ArrayList<String> Dates=new ArrayList<>();
            if (prev_id != 0)
            {
                Dates.clear();
                sumTotal.clear();
                sumIncome.clear();
            }
            while (rs.next())
            {
                int id = rs.getInt(1);


                System.out.println("prev_id " + prev_id + "->"+id) ;
                    if (id!=prev_id && prev_id!=0) {
                        System.out.println("prev_id " + prev_id);
                        System.out.println("Dates " + Dates);
                        DvCompanyChartSalesIncome dvCompanyChartSalesIncome = new DvCompanyChartSalesIncome(prev_id, sumTotal, sumIncome, Dates);
                        System.out.println(dvCompanyChartSalesIncomeArrayList.size());
                        dvCompanyChartSalesIncomeArrayList.add(dvCompanyChartSalesIncome);
                        System.out.println("Clear");
                        Dates =new ArrayList<>();
                        sumTotal =new ArrayList<>();
                        sumIncome=new ArrayList<>();
                    }
                    Dates.add(rs.getString(2));
                    sumTotal.add(rs.getInt(3));
                    sumIncome.add(rs.getInt(4));
                System.out.println(sumTotal.toString());

            prev_id = id;
                if (rs.isLast()) {
                    DvCompanyChartSalesIncome dvCompanyChartSalesIncome = new DvCompanyChartSalesIncome(id, sumTotal, sumIncome, Dates);
                    System.out.println(dvCompanyChartSalesIncomeArrayList.size());
                    dvCompanyChartSalesIncomeArrayList.add(dvCompanyChartSalesIncome);

                }


            }
            rs.close();
            st.close();
            conn.close();
            //todo
            System.out.println(dvCompanyChartSalesIncomeArrayList.size());

            return dvCompanyChartSalesIncomeArrayList;

        }catch (Exception e)
        {
            System.out.println(" Dv-> "+e.getMessage());
            return null;

        }

    }
}
