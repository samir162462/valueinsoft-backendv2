package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;

import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;

public class DbPosProduct {


    public static ArrayList<Product>  getProductBySearchText(String[] text, String branchId )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product>productArrayList = new ArrayList<>();
            String query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\"" +
                    "\tFROM public.\"PosProduct_"+branchId+"\" where ";


            // create the java statement
            StringBuilder qy = new StringBuilder(query);

            if (text.length > 0) {
                for (int i = 0; i < text.length; i++) {
                    if (i == 0) {
                        String capital = text[i].substring(0, 1).toUpperCase() + text[i].substring(1);
                        String small = text[i].substring(0, 1).toLowerCase() + text[i].substring(1);
                        String s1 = "\"productName\" LIKE '%"+capital+"%' or \"productName\" LIKE '%"+small+"%'";
                        qy.append(s1);
                        continue;
                    }
                    String s2 = " And \"productName\" LIKE '%"+text[i]+"%'";
                    qy.append(s2);
                }
                qy.append(";");
            }
            System.out.println(qy.toString());


            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(qy.toString());

            while (rs.next())
            {
                System.out.println("add user connected to user "+rs.getString(1));

                Product prod = new Product(rs.getInt(1) ,rs.getString(2),rs.getTimestamp(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),
                rs.getInt(13),rs.getString(14),rs.getString(15),rs.getInt(16),rs.getString(17));
                productArrayList.add(prod);

                // print the results
            }

            st.close();
            conn.close();
            return  productArrayList;

        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }

    }


    public static ArrayList<Product>  getProductBySearchCompanyName(String comName, String branchId )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            ArrayList<Product>productArrayList = new ArrayList<>();
            String query = "SELECT \"productId\", \"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\", \"lPrice\", \"bPrice\",\n" +
                    "\"companyName\", type, \"ownerName\", serial, \"desc\", \"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity,\n" +
                    "\"pState\"" +
                    "\tFROM public.\"PosProduct_"+branchId+"\" where \"companyName\" = '"+comName+"' ";


            // create the java statement



            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next())
            {
                System.out.println("add user connected to user "+rs.getString(1));

                Product prod = new Product(rs.getInt(1) ,rs.getString(2),rs.getTimestamp(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),
                        rs.getInt(13),rs.getString(14),rs.getString(15),rs.getInt(16),rs.getString(17));
                productArrayList.add(prod);

                // print the results
            }

            st.close();
            conn.close();
            return  productArrayList;

        }catch (Exception e)
        {
            System.out.println("err : "+e.getMessage());
            return null;

        }

    }

    static public String AddProduct(Product prod,String branchId) {
        try {


//           if (checkExistBranchName(branchName))
//            {
//                return "The Branch Name existed!" ;
//            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO public.\"PosProduct_"+branchId+"\"(\n" +
                     "\"productName\", \"buyingDay\", \"activationPeriod\", \"rPrice\",\n" +
                    "\t\"lPrice\", \"bPrice\", \"companyName\", type, \"ownerName\", serial, \"desc\",\n" +
                    "\t\"batteryLife\", \"ownerPhone\", \"ownerNI\", quantity, \"pState\")\n" +
                    "\tVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

            stmt.setString(1, prod.getProductName());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(3,Integer.valueOf(prod.getActivationPeriod()) );
            stmt.setInt(4,prod.getrPrice());
            stmt.setInt(5,prod.getlPrice());
            stmt.setInt(6,prod.getbPrice());
            stmt.setString(7, prod.getCompanyName());
            stmt.setString(8, prod.getType());
            stmt.setString(9, prod.getOwnerName());
            stmt.setString(10, prod.getSerial());
            stmt.setString(11, prod.getDesc());
            stmt.setInt(12, prod.getBatteryLife());
            stmt.setString(13, prod.getOwnerPhone());
            stmt.setString(14, prod.getOwnerNI());
            stmt.setInt(15, prod.getQuantity());
            stmt.setString(16, prod.getpState());

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            conn.close();
            return "The Product Saved";

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added bs error!";

        }

    }


}
