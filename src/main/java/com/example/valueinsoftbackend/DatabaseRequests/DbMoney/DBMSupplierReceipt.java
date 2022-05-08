/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbMoney;

import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.*;
import java.util.ArrayList;

public class DBMSupplierReceipt {

    static public ResponseEntity<Object> getSupplierReceipts(int companyId, int  supplierId )
    {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"srId\", \"transId\", \"amountPaid\"::money::numeric::float8, \"remainingAmount\"::money::numeric::float8, \"receiptTime\", \"userRecived\", \"supplierId\", type, \"branchId\"\n" +
                    "\tFROM c_"+companyId+".\"supplierReciepts\" where \"supplierId\"  =  "+supplierId+" ORDER BY \"receiptTime\" DESC ;";
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            ArrayList<SupplierReceipt> supplierReceipts = new ArrayList<>();
            System.out.println(query);
            while (rs.next())
            {
                SupplierReceipt clientReceiptIn = new SupplierReceipt(
                        rs.getInt(1),rs.getInt(2),rs.getBigDecimal(3),rs.getBigDecimal(4),
                        rs.getTimestamp(5),rs.getString(6),rs.getInt(7),rs.getString(8),rs.getInt(9));
                supplierReceipts.add(clientReceiptIn);
            }
            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(HttpStatus.CREATED).body(supplierReceipts);

        }catch (Exception e)
        {
            System.out.println(" no user exist"+e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);

        }

    }

    static public ResponseEntity<Object> AddSupplierReceipt(int companyId,SupplierReceipt supplierReceipt) {
        try {

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("BEGIN;\n" +
                    " INSERT INTO c_"+companyId+".\"supplierReciepts\"(\n" +
                    " \"transId\", \"amountPaid\", \"remainingAmount\", \"receiptTime\", \"userRecived\", \"supplierId\", type, \"branchId\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?); " +
                    "UPDATE c_"+companyId+".\"InventoryTransactions_"+supplierReceipt.getBranchId()+"\"\n" +
                    "\tSET \"RemainingAmount\"=?\n" +
                    "\tWHERE \"transId\"=?; " +
                    "UPDATE c_"+companyId+".supplier_"+supplierReceipt.getBranchId()+"\n" +
                    "\tSET  \"supplierRemainig\"= \"supplierRemainig\" -  ?\n" +
                    "\tWHERE \"supplierId\"=?;" +
                    "COMMIT;");

            stmt.setInt(1, supplierReceipt.getTransId());
            stmt.setBigDecimal(2, supplierReceipt.getAmountPaid());
            stmt.setBigDecimal(3, supplierReceipt.getRemainingAmount());
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setString(5, supplierReceipt.getUserRecived());
            stmt.setInt(6, supplierReceipt.getSupplierId());
            stmt.setString(7, supplierReceipt.getType());
            stmt.setInt(8, supplierReceipt.getBranchId());
            stmt.setInt(9, supplierReceipt.getRemainingAmount().intValue());
            stmt.setInt(10, supplierReceipt.getTransId());
            stmt.setInt(11, supplierReceipt.getAmountPaid().intValue());
            stmt.setInt(12, supplierReceipt.getSupplierId());
            System.out.println(stmt.toString());

            int i = stmt.executeUpdate();
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("the ReceiptUser not added -> error in server!") ;

        }
        System.out.println("inAddSupplierReceipt");

        return ResponseEntity.status(HttpStatus.CREATED).body("the Client Receipt Added Successfully : " + supplierReceipt.getSupplierId()) ;

    }

}
