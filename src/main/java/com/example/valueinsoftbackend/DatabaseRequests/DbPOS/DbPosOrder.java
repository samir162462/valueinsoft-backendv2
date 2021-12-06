package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.util.ConvertStringToTimeStamp;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;

public class DbPosOrder {

    static public String AddOrder(Order order) {
        try {

            int branchId = order.getBranchId();
            Connection conn = ConnectionPostgres.getConnection();
            String query = "" +
                    "with new_order as (\n" +
                    "INSERT INTO public.\"PosOrder_" + branchId + "\"(\n" +
                    "\t \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\" , \"clientId\")\n" +
                    "\tVALUES ( ? , ?, ?, ?,?, ?,?)\n" +
                    "  returning \"orderId\"\n" +

                    ")\n" +
                    "INSERT INTO public.\"PosOrderDetail_" + branchId + "\"(\n" +
                    "\t \"itemId\", \"itemName\", quantity, price, total, \"orderId\" ,\"productId\" ,\"bouncedBack\") VALUES \n ";

            StringBuilder sb = new StringBuilder(query);
            ArrayList<OrderDetails> orddet = order.getOrderDetails();
            for (int i = 0; i < orddet.size(); i++) {
                OrderDetails obj = orddet.get(i);

                sb.append("  ( " + obj.getItemId() + ", '" + obj.getItemName() + "', " + obj.getQuantity() + ", " + obj.getPrice() + ", " + obj.getTotal() + ", (select \"orderId\" from new_order) , " + obj.getProductId() + ", 0 )");
                if (i != orddet.size() - 1) {
                    sb.append(" , ");
                }
            }
            sb.append("; BEGIN; ");
            //Update quantity in orderList
            for (int i = 0; i < orddet.size(); i++) {
                OrderDetails obj = orddet.get(i);
                sb.append("UPDATE public.\"PosProduct_" + branchId + "\"\n" +
                        "\tSET  quantity= quantity - " + obj.getQuantity() + "\n" +
                        "\tWHERE \"productId\" = " + obj.getProductId() + " ;");
            }
            sb.append(" " +
                    "COMMIT ; ");
            System.out.println(sb.toString());
            PreparedStatement stmt = conn.prepareStatement(sb.toString());
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setString(2, order.getClientName());
            stmt.setString(3, order.getOrderType());
            stmt.setInt(4, order.getOrderDiscount());
            stmt.setInt(5, order.getOrderTotal());
            stmt.setString(6, order.getSalesUser());
            stmt.setInt(7, order.getClientId());


            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();

            return "The Order Saved";

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the Order not added bs error! " + e.getMessage();

        }
    }

    static public ArrayList<Order> getOrdersByPeriod(int branchId, Timestamp startTime, Timestamp endTime) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Order> ordersArrayList = new ArrayList<>();
            String query = "SELECT public.\"PosOrder_" + branchId + "\".* , orderDetails\n" +
                    "FROM public.\"PosOrder_" + branchId + "\" \n" +
                    "LEFT JOIN  (SELECT array_to_json(array_agg(json_build_object('odId', orderDetail.\"orderDetailsId\" ,'itemId',orderDetail.\"itemId\",'itemName',orderDetail.\"itemName\",'quantity',orderDetail.\"quantity\",'price',orderDetail.\"price\", 'total',orderDetail.\"total\", 'productId',orderDetail.\"productId\", 'bouncedBack',orderDetail.\"bouncedBack\"))) AS orderDetails,orderDetail.\"orderId\" AS order_id \n" +
                    "            FROM public.\"PosOrderDetail_" + branchId + "\" AS orderDetail \n" +
                    "            GROUP BY orderDetail.\"orderId\") orderDetails \n" +
                    "ON order_id = public.\"PosOrder_" + branchId + "\".\"orderId\"    \n" +
                    "JOIN public.\"PosOrderDetail_" + branchId + "\" ON public.\"PosOrderDetail_" + branchId + "\".\"orderDetailsId\" = public.\"PosOrder_" + branchId + "\".\"orderId\"\n" +
                    "WHERE public.\"PosOrder_" + branchId + "\".\"orderTime\" between '" + startTime + "' and '" + endTime + "' order by \"orderId\" DESC";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Order ord = new Order(
                        rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7), branchId, rs.getInt(8), null
                );
                String details = rs.getString(9);
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<OrderDetails>>() {
                }.getType();
                ArrayList<OrderDetails> detailsArrayList = gson.fromJson(details, listType);
                ord.setOrderDetails(detailsArrayList);
                System.out.println("orders In Loop get otder Time " + ord.getOrderTime());
                ordersArrayList.add(ord);
                // print the results
            }
            rs.close();
            st.close();
            conn.close();
            return ordersArrayList;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;

        }

    }

    //--------------------------------Update-----------------------------------//
    //--------------------------BounceBack Order ---------------------//dispatch

    static public String bounceBackOrderDetailItem(int odId, int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("Do $$\n" +
                    "Begin\n" +
                    "update public.\"PosProduct_" + branchId + "\" set \"quantity\" = \"quantity\" + (select \"quantity\" from public.\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") where \"productId\" = (select \"productId\" from public.\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") ;\n" +
                    "update public.\"PosOrderDetail_" + branchId + "\"\n" +
                    "\tset \"bouncedBack\" = 1" +
                    "\tWHERE \"orderDetailsId\" = " + odId + ";\n" +
                    "Exception When Others then Rollback;\n" +
                    "end $$;");

            Statement st = conn.createStatement();
            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted num " + odId);
            stmt.close();
            conn.close();

            return "The Shift Ended ";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    //--------------------------------Update-----------------------------------//
    //-----------------Dispatch Product Order To Inventory---------------------//
    static public String dispatchProductOrderQuantity(int productId, int quantity, int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement(" UPDATE public.\"PosProduct_" + branchId + "\"\n" +
                    "\tSET  quantity= quantity - " + quantity + "\n" +
                    "\tWHERE \"productId\" = " + productId + ";");

            Statement st = conn.createStatement();
            int i = stmt.executeUpdate();
            System.out.println(productId + ": dispatchProductOrderQuantity inserted num " + quantity);
            stmt.close();
            conn.close();

            return "The Shift Ended ";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

}
