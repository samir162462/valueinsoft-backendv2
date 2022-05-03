/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

public class DbSlotsFixArea {

    public static  ResponseEntity<Object> getFixAreaSlot(int branchId, String companyName) {
        ArrayList<SlotsFixArea> slotsFixAreas = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"faId\", \"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, \"userName_Recived\", status, \"desc\", \"branchId\"" +
                    "\tFROM c_"+companyName+".\"FixArea\" where \"branchId\"  ="+branchId+" ;";
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                if (rs.getBoolean(8) == true) {
                    SlotsFixArea slot = new SlotsFixArea(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getDate(4),
                            rs.getDate(5), rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getString(9),rs.getString(10),
                            rs.getString(11),rs.getInt(12));
                    slotsFixAreas.add(slot);
                }
            }
            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body( slotsFixAreas);
        } catch (Exception e) {
            System.out.println("err in get DamagedList : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body( null);

        }
    }


    static public ResponseEntity<Object> AddFixAreaSlot(int branchId,String companyName,SlotsFixArea slotsFixArea) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO c_"+companyName+".\"FixArea\"(\n" +
                    "\"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, \"userName_Recived\", status, \"desc\" , \"branchId\" )\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?);");

            stmt.setInt(1, slotsFixArea.getFixSlot());
            stmt.setInt(2, slotsFixArea.getClientId());
            stmt.setDate(3, slotsFixArea.getDateIn());
            stmt.setDate(4, slotsFixArea.getDateFinished());
            stmt.setString(5, slotsFixArea.getPhoneName());
            stmt.setString(6, slotsFixArea.getProblem());
            stmt.setBoolean(7, slotsFixArea.isShow());
            stmt.setString(8, slotsFixArea.getUserName_Recived());
            stmt.setString(9, slotsFixArea.getStatus());
            stmt.setString(10, slotsFixArea.getDesc());
            stmt.setInt(11, slotsFixArea.getBranchId());
            System.out.println(stmt.toString());
            int i = stmt.executeUpdate();
            System.out.println(i + " AddDamagedItem added records inserted");
            stmt.close();
            conn.close();
            return ResponseEntity.status(HttpStatus.CREATED).body( "The Fix Slot Added (success)");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body( "The Fix Slot Not Added (Error)"+e.getMessage());
        }
    }



    //todo -- delete
    public static boolean deleteDamagedItem( int branchId,String companyName,int DId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "DELETE FROM public.\"DamagedList\"\n" +
                    "\tWHERE \"DId\" = " + DId + ";";

            PreparedStatement pstmt = null;
            pstmt = conn.prepareStatement(query);
            pstmt.executeUpdate();
            // create the java statement
            pstmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
            return false;
        }
        return true;
    }

    //todo Update Not Yet
    public static ResponseEntity<Object> updateFixAreaSlot( String companyName, SlotsFixArea slotsFixArea) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE C_"+companyName+".\"FixArea\"\n" +
                    "\tSET    \"dateFinished\"=?, problem=?, show=?, status=?, \"desc\"=?\n" +
                    "\tWHERE \"faId\"=? And  \"branchId\"=?;");

            stmt.setDate(1, slotsFixArea.getDateFinished());
            stmt.setString(2, slotsFixArea.getProblem());
            stmt.setBoolean(3, slotsFixArea.isShow());
            stmt.setString(4, slotsFixArea.getStatus());
            stmt.setString(5, slotsFixArea.getDesc());
            stmt.setInt(6, slotsFixArea.getFaId());
            stmt.setInt(7, slotsFixArea.getBranchId());
            System.out.println(stmt);
            int i = stmt.executeUpdate();
            System.out.println(i + " Fix Slot update record ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body( "The Fix Slot Not Added (Failed)");

        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body( "The Fix Slot Updated (success)");

    }
}
