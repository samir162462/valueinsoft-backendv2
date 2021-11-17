package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;

public class DbPosOrder {

    static public String AddOrder(Order order) {
        try {

            int branchId = order.getBranchId();
            Connection conn = ConnectionPostgres.getConnection();
            String query = "with new_order as (\n" +
                    "INSERT INTO public.\"PosOrder_"+branchId+"\"(\n" +
                    "\t \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\")\n" +
                    "\tVALUES ( ? , ?, ?, ?,?, ?)\n" +
                    "  returning \"orderId\"\n" +
                    ")\n" +
                    "INSERT INTO public.\"PosOrderDetail_"+branchId+"\"(\n" +
                    "\t \"itemId\", \"itemName\", quantity, price, total, \"orderId\") VALUES \n" ;

            StringBuilder sb = new StringBuilder(query);
            ArrayList<OrderDetails> orddet = order.getOrderDetails();
            for (int i = 0; i < orddet.size() ; i++) {
                OrderDetails obj = orddet.get(i);

                sb.append("  ( "+obj.getItemId()+", '"+obj.getItemName()+"', "+obj.getQuantity()+", "+obj.getPrice()+", "+obj.getTotal()+", (select \"orderId\" from new_order)) ");
                if (i != orddet.size()-1) {
                    sb.append(" , ");
                }
            }
            sb.append(" ; ");
            System.out.println(sb.toString());
                PreparedStatement stmt = conn.prepareStatement(sb.toString());
                stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                stmt.setString(2, order.getClientName());
                stmt.setString(3, order.getOrderType());
                stmt.setInt(4,order.getOrderDiscount());
                stmt.setInt(5,order.getOrderTotal());
                stmt.setString(6,order.getSalesUser());


                int i = stmt.executeUpdate();
                System.out.println(i + " records inserted");
                stmt.close();
                conn.close();


            return "The Order Saved";

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the Order not added bs error! "+e.getMessage();

        }

    }

}
