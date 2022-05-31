/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Expenses;
import com.example.valueinsoftbackend.Model.Sales.ExpensesSum;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;
import java.util.ArrayList;

public class DbExpenses {

    //-----------GET----------
    static boolean iSExistExpensesStatic(int branchId, int companyId,String name) {
        try {
            String query = "select EXISTS (SELECT name FROM c_"+companyId+".\"ExpensesStatic\"\n" +
                    "where \"branchId\" = "+branchId+" and name = '"+name+"' )";
            Connection conn = ConnectionPostgres.getConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            System.out.println(query);
            try {
                while (rs.next()) {
                    System.out.println(rs.getBoolean(1));
                    return rs.getBoolean(1);
                }
            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
                return true;
            }
            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return true;
        }
        return true;
    }


    public static ResponseEntity<Object> getAllExpensesItems(int branchId, int companyId, boolean isStatic) {
        try {
            String query = " ";
            if (isStatic) {
                query = "select \"eId\", type, amount::money::numeric::float8, \"time\", \"branchId\", \"user\", name \n" +
                        "from  c_" + companyId + ".\"ExpensesStatic\"\n" +
                        "where \"branchId\" = " + branchId + " ;";
            } else {
                query = "select \"eId\", type, amount::money::numeric::float8, \"time\", \"branchId\", \"user\", name \n" +
                        "from  c_" + companyId + ".\"Expenses\"\n" +
                        "where \"branchId\" = " + branchId + " ;";
            }

            Connection conn = ConnectionPostgres.getConnection();
            System.out.println(query);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            ArrayList<Expenses> expensesArrayList = new ArrayList<>();

            try {
                while (rs.next()) {
                    Expenses expenses = new Expenses(rs.getInt(1), rs.getString(2), rs.getBigDecimal(3), rs.getTimestamp(4), rs.getInt(5), rs.getString(6), rs.getString(7));
                    expensesArrayList.add(expenses);
                }

            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
                return ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(200).body(expensesArrayList);

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return ResponseEntity.status(406).body("errorIn expensesArrayList To array" + e.getMessage());

        }

    }

    public static ResponseEntity<Object> getPurchasesExpensesByMonth(int branchId, int companyId, String timeText) {

        try {
            String[] times = timeText.split("x");
            System.out.println("-----------> "+times+"<-------------");
            if (times[1].compareTo(times[0]) <0) {
                System.out.println("time 1 > t2");
            }else{
                System.out.println("time2 > t1");
            }
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT EXTRACT(day FROM time::date) d, EXTRACT(month FROM time::date) m, \n" +
                    "       EXTRACT(year FROM time::date) y,time::date, sum(\"transTotal\"), count(time) t \n" +
                    "   FROM c_"+companyId+".\"InventoryTransactions_"+branchId+"\" \n" +
                    "   where \"time\" >= date_trunc('month', '"+times[0]+"'::timestamp)\n" +
                    "  \tand \"time\" < date_trunc('month', '"+times[1]+"'::timestamp) + interval '1 month' \n" +
                    "  GROUP BY 1,2,3,time::date\n" +
                    "  ORDER BY 3,2,1 ASC;\n";
            System.out.println(query);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            ArrayList<ExpensesSum> expensesArrayList = new ArrayList<>();

            try {
                while (rs.next()) {
                    ExpensesSum expenses = new ExpensesSum(rs.getDate(4),rs.getInt(5),rs.getInt(6));
                    expensesArrayList.add(expenses);
                }

            } catch (Exception e) {
                System.out.println("getProductById " + e.getMessage());
                return ResponseEntity.status(406).body("errorIn getProductNames To array" + e.getMessage());
            }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(200).body(expensesArrayList);

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return ResponseEntity.status(406).body("errorIn expensesArrayList To array" + e.getMessage());

        }

    }

    static public ResponseEntity<Object> AddExpenses(int branchId, int companyId, Expenses expenses, boolean isStatic) {
        try {

            if (isStatic&& iSExistExpensesStatic(branchId,companyId,expenses.getName())) {
                return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body("the Name Exists! => "+expenses.getName());

            }

            String query = " ";
            if (isStatic) {
                query = "INSERT INTO c_" + companyId + ".\"ExpensesStatic\"(\n" +
                        "\t type, amount, \"time\", \"branchId\", \"user\" , name)\n" +
                        "\tVALUES ( ?, ?, ?, ?, ?,?);";
            } else {
                query = "INSERT INTO c_" + companyId + ".\"Expenses\"(\n" +
                        "\t type, amount, \"time\", \"branchId\", \"user\" , name)\n" +
                        "\tVALUES ( ?, ?, ?, ?, ?,?);";
            }
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement(query);

            stmt.setString(1, expenses.getType());
            stmt.setBigDecimal(2, expenses.getAmount());
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(4, branchId);
            stmt.setString(5, expenses.getUser());
            stmt.setString(6, expenses.getName());

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Record Not Inserted");

        }

        return ResponseEntity.status(HttpStatus.CREATED).body("Record Inserted");
    }

    //todo Update
    static public ResponseEntity<Object> updateExpenses(int branchId, int companyId, Expenses expenses) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE c_" + companyId + ".\"Expenses\"\n" +
                    "\tSET type=?, amount=?, \"time\"=?, \"branchId\"=?, \"user\"=? ,name=? \n" +
                    "\tWHERE  \"eId\"=?;");

            stmt.setString(1, expenses.getType());
            stmt.setBigDecimal(2, expenses.getAmount());
            stmt.setTimestamp(3, expenses.getTime());
            stmt.setInt(4, branchId);
            stmt.setString(5, expenses.getUser());
            stmt.setString(6, expenses.getName());
            stmt.setInt(7, expenses.getExId());

            int i = stmt.executeUpdate();
            System.out.println(i + " client update record ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Record Not Updated");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Record Updated");
    }


}
