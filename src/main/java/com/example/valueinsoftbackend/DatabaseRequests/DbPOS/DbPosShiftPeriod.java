package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;

import java.util.ArrayList;

public class DbPosShiftPeriod {


    private static boolean checkShiftStart(int companyId , int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "select EXISTS (SELECT * FROM c_"+companyId+".\"PosShiftPeriod\"\n" +
                    "where \"branchId\" = "+branchId+" and \"ShiftEndTime\" IS NULL)";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                return rs.getBoolean(1);


                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(" no user exist");
            return true;

        }
        return false;
    }

    static public ResponseEntity<Object>  startShiftPeriod(int comId, int branchId) {
        try {

            if (checkShiftStart(comId,branchId)) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("There is Exists Shift opened") ;
            }


            Connection conn = ConnectionPostgres.getConnection();


            String stmt = new String("with newShift as (\n" +
                    "INSERT INTO C_"+comId+".\"PosShiftPeriod\"(\n" +
                    "\t \"ShiftStartTime\", \"ShiftEndTime\" , \"branchId\")\n" +
                    "\tVALUES ( '"+new Timestamp(System.currentTimeMillis())+"', null, "+branchId+")\n" +
                    "\treturning \"PosSOID\" , \"ShiftStartTime\"\n" +
                    ")\n" +
                    "select * from newShift");

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(stmt);
            ShiftPeriod sp = null ;
            while (rs.next())
            {
                 sp = new ShiftPeriod(rs.getInt(1),rs.getTimestamp(2),null,null);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(HttpStatus.CREATED).body(sp) ;

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("There is Error In Shift Start ") ;

        }

    }


    static public String endShiftPeriod(int comId, int shiftPeriodId ) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE C_"+comId+".\"PosShiftPeriod\"\n" +
                    "\tSET   \"ShiftEndTime\"= '"+new Timestamp(System.currentTimeMillis())+"'\n" +
                    "\tWHERE \"PosSOID\"= ? ;");

            Statement st = conn.createStatement();
            stmt.setInt(1, shiftPeriodId);


            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();



            return "The Shift Ended ";



        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;

        }

    }//ShiftOrdersById
    public static ArrayList<Order> ShiftOrdersByPeriod(int comId,int branchId ,int spId)
    {

        return DbPosOrder.getOrdersByShiftId(comId,branchId,spId);
    }

    public static ShiftPeriod dealingWithCurrentShiftData(int comId,int branchId ,boolean withDetails)
    {
        System.out.println("dealing with currebt shift orders");
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\", \"branchId\"\n" +
                    "\tFROM C_"+comId+".\"PosShiftPeriod\" where \"branchId\" = "+branchId+" and \"ShiftEndTime\" IS NULL;";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ShiftPeriod sf = null;
            while (rs.next())
            {
                System.out.println("add user connected to user "+rs.getString(1));
                 sf = new ShiftPeriod(
                        rs.getInt(1),rs.getTimestamp(2),rs.getTimestamp(3),null

                );
                System.out.println("rs.getTimestamp(2): "+rs.getTimestamp(2));

                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            if (withDetails ) {
                sf.setOrderShiftList(DbPosOrder.getOrdersByPeriod(branchId,sf.getStartTime(),new Timestamp(System.currentTimeMillis()), comId));
            }
            return  sf;


        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }



    }

    public static ArrayList<ShiftPeriod> currentBranchShiftData(int comId,int branchId )
    {
        try {

            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT * FROM C_"+comId+".\"PosShiftPeriod\" where \"branchId\" = "+branchId+" \n" +
                    "ORDER BY \"PosSOID\" DESC  ;";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ShiftPeriod sf = null;
            ArrayList<ShiftPeriod> shiftPeriods = new ArrayList<>();
            while (rs.next())
            {
                System.out.println(" connected to currentBranchShiftData "+rs.getString(1));
                sf = new ShiftPeriod(
                        rs.getInt(1),rs.getTimestamp(2),rs.getTimestamp(3),null
                );
                shiftPeriods.add(sf);

                // print the results
            }
            rs.close();
            st.close();
            conn.close();

            return  shiftPeriods;


        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }



    }

}
