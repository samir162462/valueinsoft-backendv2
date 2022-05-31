/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales;

import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class DbDvSales {

     static public ArrayList<DvSales> getMonthlySales(int companyId,String currentMonth , int branchId)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \n" +
                    "\tDATE(\"orderTime\") salesDate,\n" +
                    "\tcast(date_trunc('month', '"+currentMonth+"'::date) as date)as firstDay,\n" +
                    "\t(date_trunc('month', '"+currentMonth+"'::date) + interval '1 month' - interval '1 day')::date AS lastDay,\n" +
                    "\n" +
                    "\t(SUM(\"orderTotal\") - SUM(\"orderBouncedBack\")) sum   \n" +
                    "FROM C_"+companyId+".\"PosOrder_"+branchId+"\"\n" +
                    "where  \"orderTime\" <= now()::date+1 And \"orderTime\" >= cast(date_trunc('month', current_date) as date)\n" +
                    "GROUP BY\n" +
                    "\tDATE(\"orderTime\")\n" +
                    "ORDER BY DATE(\"orderTime\") ASC \n" +
                    "\t;";
            // create the java statement
            System.out.println(query);
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
    static public  ResponseEntity<Object> getYearlySales(int companyId,String currentYear , int branchId)
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT (SUM(\"orderTotal\") - SUM(\"orderBouncedBack\")) sum  ,to_char(date_trunc('month', \"orderTime\"), 'Mon ') AS Month\n" +
                    " , EXTRACT(MONTH FROM date_trunc('month', \"orderTime\"))::integer as num, SUM(\"orderIncome\")as income " +
                    "FROM  C_"+companyId+".\"PosOrder_"+branchId+"\" \n" +
                    "WHERE extract(year from \"orderTime\")="+currentYear+"\n" +
                    "GROUP BY Month ,num ; ";
            // create the java statement
            System.out.println(query);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<DVSalesYearly> dvSalesArrayList = new ArrayList<>();
            while (rs.next())
            {
                DVSalesYearly sales = new DVSalesYearly(rs.getString(2),rs.getInt(1),rs.getInt(3),rs.getInt(4));
                dvSalesArrayList.add(sales);
            }
            rs.close();
            st.close();
            conn.close();
            return  ResponseEntity.status(HttpStatus.OK).body(dvSalesArrayList) ;

        }catch (Exception e)
        {
            System.out.println("ERRRRROR "+ e.getMessage());
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null) ;


        }

    }

    static public ArrayList<SalesProduct> getSalesProductsByPeriod(int companyId,int branchId , String startTime , String endTime)
    {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "WITH salesPeriod AS (\n" +
                    "((SELECT \"orderId\" from C_"+companyId+".\"PosOrder_"+branchId+"\" where  \"orderTime\"::date  >= '"+startTime+"'::date  Order BY \"orderId\" asc  LIMIT 1)\n" +
                    "UNION ALL \n" +
                    "(SELECT \"orderId\" from  C_"+companyId+".\"PosOrder_"+branchId+"\" where  \"orderTime\"::date  <= '"+endTime+"'::date   Order BY \"orderId\" DESC  LIMIT 1)  )\n" +
                    ")\n" +
                    "SELECT \"itemName\", count(\"itemId\")::integer NumberOfOrders ,  sum(quantity)::integer SumQuantity ,SUM(\"total\") sumTotal " +
                    "\tFROM C_"+companyId+".\"PosOrderDetail_"+branchId+"\"  where \"bouncedBack\"  < 1 and \"orderId\" between (select  \"orderId\" from salesPeriod limit 1 ) and (select  \"orderId\" from salesPeriod where \"orderId\"> (select  \"orderId\" from salesPeriod limit 1 ) )\n" +
                    "\tGROUP BY \"itemName\"  Order by SumQuantity DESC,NumberOfOrders ;"
                    ;
            System.out.println(query);
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<SalesProduct> salesProducts = new ArrayList<>();
            while (rs.next())
            {
                SalesProduct salesProduct = new SalesProduct(rs.getString(1),rs.getInt(2),rs.getInt(3),rs.getInt(4));
                salesProducts.add(salesProduct);
            }
            rs.close();
            st.close();
            conn.close();
            return salesProducts;

        }catch (Exception e)
        {
            System.out.println(" no SalesProduct exist"+e);
            return null;

        }

    }
}
