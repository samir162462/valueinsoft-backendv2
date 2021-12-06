package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;

public class DbPosShiftPeriod {


    static public ShiftPeriod startShiftPeriod( int branchId) {
        try {



            Connection conn = ConnectionPostgres.getConnection();


            String stmt = new String("with newShift as (\n" +
                    "INSERT INTO public.\"PosShiftPeriod\"(\n" +
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
            return sp;

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;

        }

    }

    static public String endShiftPeriod( int shiftPeriodId ) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE public.\"PosShiftPeriod\"\n" +
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

    }
    public static ShiftPeriod dealingWithCurrentShiftData(int branchId ,boolean withDetails)
    {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\", \"branchId\"\n" +
                    "\tFROM public.\"PosShiftPeriod\" where \"branchId\" = "+branchId+" and \"ShiftEndTime\" IS NULL;";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ShiftPeriod sf = null;
            while (rs.next())
            {
                System.out.println("add user connected to user "+rs.getString(1));
                 sf = new ShiftPeriod(
                        rs.getInt(1),rs.getTimestamp(2),rs.getTimestamp(3),null
                );


                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            if (withDetails ) {
                sf.setOrderShiftList(DbPosOrder.getOrdersByPeriod(branchId,sf.getStartTime(),new Timestamp(System.currentTimeMillis())));
            }
            return  sf;


        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }



    }

}
