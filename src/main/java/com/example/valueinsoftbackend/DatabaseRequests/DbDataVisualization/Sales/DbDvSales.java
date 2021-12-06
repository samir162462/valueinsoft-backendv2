/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class DbDvSales {

     static public ArrayList<DvSales> getMonthlySales(String currentMonth , int branchId)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \n" +
                    "\tDATE(\"orderTime\") salesDate,\n" +
                    "\tcast(date_trunc('month', '"+currentMonth+"'::date) as date)as firstDay,\n" +
                    "\t(date_trunc('month', '"+currentMonth+"'::date) + interval '1 month' - interval '1 day')::date AS lastDay,\n" +
                    "\n" +
                    "\tSUM(\"orderTotal\") sum\n" +
                    "FROM public.\"PosOrder_"+branchId+"\"\n" +
                    "where  \"orderTime\" <= now()::date+1 And \"orderTime\" >= cast(date_trunc('month', current_date) as date)\n" +
                    "GROUP BY\n" +
                    "\tDATE(\"orderTime\")\n" +
                    "ORDER BY DATE(\"orderTime\") ASC \n" +
                    "\t;";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<DvSales> dvSalesArrayList = new ArrayList<>();
            while (rs.next())
            {
                DvSales sales = new DvSales(rs.getDate(2),rs.getDate(3),rs.getDate(1),rs.getInt(4));
                dvSalesArrayList.add(sales);
            }
            rs.close();
            st.close();
            conn.close();
            return dvSalesArrayList;

        }catch (Exception e)
        {
            System.out.println(" no user exist");
            return null;

        }

    }
}
