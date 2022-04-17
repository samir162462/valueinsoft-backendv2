/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbApp;

import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.OnlinePayment.OPController.PayMobController;
import com.example.valueinsoftbackend.OnlinePayment.OPModel.OrderRegistration;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DbSubscription {

    public static ArrayList<AppModelSubscription> getBranchSubscription(int branchId) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"sId\", \"startTime\", \"endTime\", \"branchId\", \"amountToPay\"::money::numeric::float8, \"amountPaid\"::money::numeric::float8 , order_id, status \n" +
                    "\tFROM public.\"CompanySubscription\" where \"branchId\" = " + branchId + " ORDER BY \"sId\" ASC  ;";
            // create the java statement
            System.out.println(query);

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<AppModelSubscription> appModelSubscriptions = new ArrayList<>();
            while (rs.next()) {
                AppModelSubscription appModelSubscription = new AppModelSubscription(rs.getInt(1), rs.getDate(2), rs.getDate(3), rs.getInt(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getString(8));
                appModelSubscriptions.add(appModelSubscription);
            }
            rs.close();
            st.close();
            conn.close();
            return appModelSubscriptions;

        } catch (Exception e) {
            System.out.println(" no user exist" + e.getMessage());
            return null;

        }
    }


    static public String AddBranchSubscription(AppModelSubscription appModelSubscription) {
        try {

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"CompanySubscription\"(\n" +
                    "\t \"startTime\", \"endTime\", \"branchId\", \"amountToPay\", \"amountPaid\" , order_id, status )\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?,?,?);", Statement.RETURN_GENERATED_KEYS);

            stmt.setDate(1, appModelSubscription.getStartTime());
            stmt.setDate(2, appModelSubscription.getEndTime());
            stmt.setInt(3, appModelSubscription.getBranchId());
            stmt.setBigDecimal(4, appModelSubscription.getAmountToPay());
            stmt.setBigDecimal(5, appModelSubscription.getAmountPaid());
            stmt.setInt(6, 0);
            stmt.setString(7, "NP");
            int i = stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                // Retrieve the auto generated key(s).
                int key = rs.getInt(1);
                ArrayList<String> items = new ArrayList<>();
                BigDecimal b1 = new BigDecimal(100);
                System.out.println(appModelSubscription.getAmountToPay().multiply(b1));
                OrderRegistration orderRegistration = new OrderRegistration(PayMobController.createPostAuth(), "false",appModelSubscription.getAmountToPay().multiply(b1) +"" , "EGP", appModelSubscription.getBranchId(), key, items);
                int order_Id = Integer.valueOf(orderRegistration.createOrderRegistrationId(orderRegistration));
                if (order_Id != 0) {
                    updateBranchSubscriptionWithPayMobId(order_Id, key);
                }
            }

            System.out.println(i + " record Added to BranchSubscription");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the BranchSubscription not added -> error in server! :: -> "+e.getMessage();

        }

        return "the Add BranchSubscription Added Successfully : " + appModelSubscription.getBranchId();
    }


    static public String updateBranchSubscriptionWithPayMobId(int orderId, int sID) {
        try {

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("UPDATE public.\"CompanySubscription\"\n" +
                    "\tSET  order_id=?\n" +
                    "\tWHERE \"sId\"=? ;");

            stmt.setInt(1, orderId);
            stmt.setInt(2, sID);

            int i = stmt.executeUpdate();


            System.out.println(i + " record Updated orderId-> sID <-> BranchSubscription");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the BranchSubscription not added -> error in server!";

        }

        return "the updated BranchSubscription  Successfully : ";
    }
    static public String updateBranchSubscriptionStatusSuccess(int order_id, boolean success) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE public.\"CompanySubscription\"\n" +
                    "\tSET  status='PD'\n" +
                    "\tWHERE  order_id = "+order_id+" ;");

            System.out.println(order_id);
            System.out.println(stmt);

            int i = stmt.executeUpdate();
            System.out.println(i + " record Updated updateBranchSubscriptionStatus <-> BranchSubscription");
            stmt.close();
            conn.close();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the BranchSubscription not added -> error in server!";

        }

        return "the updated BranchSubscription  Successfully : ";
    }

    public static Map<String, Object> isActive(int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT  \"startTime\" , \"endTime\", CURRENT_DATE as currentDate , \"endTime\" - \"startTime\"  as allTime , \"endTime\" - CURRENT_DATE  as remaining ,\n" +
                    "status ,\n" +
                    "CASE\n" +
                    "\t\tWHEN \"endTime\" - CURRENT_DATE > 0 THEN true\n" +
                    "\t\tWHEN \"endTime\" - CURRENT_DATE < 1  THEN false\n" +
                    "\t\tELSE false\n" +
                    "\tEND AS active\n" +
                    "\n" +
                    "FROM public.\"CompanySubscription\"\n" +
                    "Where \"branchId\" = "+branchId+"\n" +
                    "ORDER BY \"sId\" DESC \n" +
                    "LIMIT 1";
            // create the java statement
            System.out.println(query);

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<AppModelSubscription> appModelSubscriptions = new ArrayList<>();
            while (rs.next()) {
                Date sDate =  rs.getDate(1);
                Date eDate =  rs.getDate(2);
                Date cDate =  rs.getDate(3);
                int allTime  = rs.getInt(4);
                int remainingTime  = rs.getInt(5);
                String status = rs.getString(6);
                boolean active = rs.getBoolean(7);
                Map<String, Object> branchSubscriptionObject = new HashMap<>();
                branchSubscriptionObject.put("sDate",sDate);
                branchSubscriptionObject.put("eDate",eDate);
                branchSubscriptionObject.put("cDate",cDate);
                branchSubscriptionObject.put("allTime",allTime);
                branchSubscriptionObject.put("remainingTime",remainingTime);
                branchSubscriptionObject.put("status",status);
                branchSubscriptionObject.put("active",active);
                System.out.println(branchSubscriptionObject);
                return branchSubscriptionObject;
            }
            rs.close();
            st.close();
            conn.close();

        } catch (Exception e) {
            System.out.println(" err in is active ____ " + e.getMessage());
            return null;

        }
        return null;
    }
}
