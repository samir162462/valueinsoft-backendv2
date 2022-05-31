package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;

public class DbPosOrder {

    static public ResponseEntity<Integer>  AddOrder(Order order , int companyId) {
        try {

            int branchId = order.getBranchId();
            Connection conn = ConnectionPostgres.getConnection();
            String query = "" +
                    "with new_order as (\n" +
                    "INSERT INTO C_"+companyId+".\"PosOrder_" + branchId + "\"(\n" +
                    "\t \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\" , \"clientId\", \"orderIncome\",\"orderBouncedBack\")\n" +
                    "\tVALUES ( ? , ?, ?, ?,?, ?,?,?,?)\n" +
                    "  returning \"orderId\"\n" +

                    ")\n" +
                    "INSERT INTO C_"+companyId+".\"PosOrderDetail_" + branchId + "\"(\n" +
                    "\t \"itemId\", \"itemName\", quantity, price, total, \"orderId\" ,\"productId\" ,\"bouncedBack\") VALUES \n ";

            StringBuilder sb = new StringBuilder(query);
            ArrayList<OrderDetails> orddet = order.getOrderDetails();
            for (int i = 0; i < orddet.size(); i++) {
                OrderDetails obj = orddet.get(i);

                sb.append("  ( " + obj.getItemId() + ", '" + obj.getItemName() + "', " + obj.getQuantity() + ", " + obj.getPrice() + ", " + obj.getTotal() + ", (select \"orderId\" from new_order) , " + obj.getProductId() + ", 0)");
                if (i != orddet.size() - 1) {
                    sb.append(" , ");
                }
            }
            sb.append("; BEGIN; ");
            //Update quantity in orderList
            for (int i = 0; i < orddet.size(); i++) {
                OrderDetails obj = orddet.get(i);
                sb.append("UPDATE C_"+companyId+".\"PosProduct_" + branchId + "\"\n" +
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
            stmt.setInt(8, order.getOrderIncome());
            stmt.setInt(9, 0);


            int i = stmt.executeUpdate();

            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();

            return  ResponseEntity.status(201).body(getLastIdOrder(branchId,companyId));

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(0);

        }
    }
    static public int getLastIdOrder( int branchId ,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            int id = 0 ;
            query = "select max(\"orderId\") from C_"+companyId+".\"PosOrder_"+branchId+"\";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));
                id = rs.getInt(1);
            }

            rs.close();
            st.close();
            conn.close();
            return id;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return 0;

    }

    static public ArrayList<Order> getOrdersByPeriod(int branchId, Timestamp startTime, Timestamp endTime , int companyId) {
        try {
            System.out.println("in getOrdersByPeriod ");
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Order> ordersArrayList = new ArrayList<>();
            String query = "SELECT C_"+companyId+".\"PosOrder_" + branchId + "\".* , orderDetails\n" +
                    "FROM C_"+companyId+".\"PosOrder_" + branchId + "\" \n" +
                    "LEFT JOIN  (SELECT array_to_json(array_agg(json_build_object('odId', orderDetail.\"orderDetailsId\" ,'itemId',orderDetail.\"itemId\",'itemName',orderDetail.\"itemName\",'quantity',orderDetail.\"quantity\",'price',orderDetail.\"price\", 'total',orderDetail.\"total\", 'productId',orderDetail.\"productId\", 'bouncedBack',orderDetail.\"bouncedBack\"))) AS orderDetails,orderDetail.\"orderId\" AS order_id \n" +
                    "            FROM C_"+companyId+".\"PosOrderDetail_" + branchId + "\" AS orderDetail \n" +
                    "            GROUP BY orderDetail.\"orderId\") orderDetails \n" +
                    "ON order_id = C_"+companyId+".\"PosOrder_" + branchId + "\".\"orderId\"    \n" +
                    "WHERE C_"+companyId+".\"PosOrder_" + branchId + "\".\"orderTime\" between '" + startTime + "' and '" + endTime + "' order by \"orderId\" DESC";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            System.out.println(query);
            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Order ord = new Order(
                        rs.getInt(1), rs.getTimestamp(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7), branchId, rs.getInt(8),rs.getInt(9),rs.getInt(10), null
                );
                String details = rs.getString(11);
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
            System.out.println("12--err : " + e.getMessage());
            return null;

        }

    }
    static public ArrayList<Order> getOrdersByShiftId(int comId ,int branchId, int spId) {
        try {
            System.out.println("in getOrdersByPeriod ");
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Order> ordersArrayList = new ArrayList<>();
            String query = "WITH sales AS (\n" +
                    "SELECT \"ShiftStartTime\" , \"ShiftEndTime\" FROM C_"+comId+".\"PosShiftPeriod\" where \"branchId\" = "+branchId+" AND \"PosSOID\" = "+spId+"\n" +
                    "     )\n" +
                    "SELECT C_"+comId+".\"PosOrder_"+branchId+"\".* , orderDetails\n" +
                    "FROM C_"+comId+".\"PosOrder_"+branchId+"\" \n" +
                    "LEFT JOIN  (SELECT array_to_json(array_agg(json_build_object('odId', orderDetail.\"orderDetailsId\" ,'itemId',orderDetail.\"itemId\",'itemName',orderDetail.\"itemName\",'quantity',orderDetail.\"quantity\",'price',orderDetail.\"price\", 'total',orderDetail.\"total\", 'productId',orderDetail.\"productId\", 'bouncedBack',orderDetail.\"bouncedBack\"))) AS orderDetails,orderDetail.\"orderId\" AS order_id \n" +
                    "            FROM C_"+comId+".\"PosOrderDetail_"+branchId+"\" AS orderDetail \n" +
                    "            GROUP BY orderDetail.\"orderId\") orderDetails \n" +
                    "ON order_id = C_"+comId+".\"PosOrder_"+branchId+"\".\"orderId\"    \n" +
                    "WHERE C_"+comId+".\"PosOrder_"+branchId+"\".\"orderTime\" between (SELECT \"ShiftStartTime\" FROM sales) and (SELECT \"ShiftEndTime\" FROM sales) order by \"orderId\" DESC ; ";

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            System.out.println(query);
            while (rs.next()) {
                System.out.println("add user connected to user " + rs.getString(1));
                Order ord = new Order(
                        rs.getInt(1), rs.getTimestamp(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7), branchId, rs.getInt(8),rs.getInt(9),rs.getInt(10), null
                );
                String details = rs.getString(11);
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

    //Get Orders By ClientID
    static public ArrayList<Order> getOrdersByClientId(int clientId, int branchId ,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);
            ArrayList<Order> orderArrayList = new ArrayList<>();

            query = "SELECT \"orderId\", \"orderTime\", \"clientName\", \"orderType\", \"orderDiscount\", \"orderTotal\", \"salesUser\", \"clientId\", \"orderIncome\",\"orderBouncedBack\"\n" +
                    "\tFROM C_"+companyId+".\"PosOrder_"+branchId+"\" where \"clientId\" =  " + clientId + ";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                Order cl = new Order(
                        rs.getInt(1),
                        rs.getTimestamp(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getString(7),
                        branchId,
                        rs.getInt(8),
                        rs.getInt(9),
                        rs.getInt(10),
                        //rs.getTimestamp(6)
                        null
                );
                orderArrayList.add(cl);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return orderArrayList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }
    static public ArrayList<OrderDetails> getOrdersDetailsByOrderId(int orderId, int branchId ,int companyId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);
            ArrayList<OrderDetails> orderDetailsArrayList = new ArrayList<>();

            query = "SELECT \"orderDetailsId\", \"itemId\", \"itemName\", quantity, price, total, \"orderId\", \"productId\", \"bouncedBack\"\n" +
                    "\tFROM c_"+companyId+".\"PosOrderDetail_"+branchId+"\" where \"orderId\" = " + orderId + ";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                OrderDetails ol = new OrderDetails(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getInt(8),
                        rs.getInt(9)
                );
                orderDetailsArrayList.add(ol);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return orderDetailsArrayList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }


        //--------------------------------Update-----------------------------------//
    //--------------------------BounceBack Order ---------------------//dispatch

    static public String bounceBackOrderDetailItem(int odId, int branchId ,int companyId ,int toWho) { //Inventory
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("Do $$\n" +
                    "Begin\n" +
                    "update C_"+companyId+".\"PosProduct_" + branchId + "\" set \"quantity\" = \"quantity\" + (select \"quantity\" from C_"+companyId+".\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") where \"productId\" = (select \"productId\" from C_"+companyId+".\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") ;\n" +
                    "update C_"+companyId+".\"PosOrder_" + branchId + "\" set \"orderBouncedBack\" = \"orderBouncedBack\" + (select \"total\" from C_"+companyId+".\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") where \"orderId\" = (select \"orderId\" from C_"+companyId+".\"PosOrderDetail_" + branchId + "\" where \"orderDetailsId\" = " + odId + ") ;\n" +
                    "update C_"+companyId+".\"PosOrder_"+branchId+"\" set \"orderIncome\" = \"orderIncome\" -  \n" +
                    "((select \"total\"  from C_"+companyId+".\"PosOrderDetail_"+branchId+"\" where \"orderDetailsId\" = "+odId+") -\n" +
                    "((select \"bPrice\"  from C_"+companyId+".\"PosProduct_"+branchId+"\" where \"productId\" = \n" +
                    "  (select \"productId\"  from C_"+companyId+".\"PosOrderDetail_"+branchId+"\" where \"orderDetailsId\" = "+odId+"))*\n" +
                    "  (select \"quantity\"  from C_"+companyId+".\"PosOrderDetail_"+branchId+"\" where \"orderDetailsId\" = "+odId+")))\n" +
                    " where \"orderId\" = (select \"orderId\" from C_"+companyId+".\"PosOrderDetail_"+branchId+"\" where \"orderDetailsId\" = "+odId+") ;" +
                    "update C_"+companyId+".\"PosOrderDetail_" + branchId + "\"\n" +
                    "\tset \"bouncedBack\" = "+toWho+"  " +
                    "\tWHERE \"orderDetailsId\" = " + odId + ";\n" +
                    "Exception When Others then Rollback;\n" +
                    "end $$;");

            Statement st = conn.createStatement();
            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted num " + stmt.toString());
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


}
