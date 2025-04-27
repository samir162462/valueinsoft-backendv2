
/*
package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPosShiftPeriod {

    private final DbPosOrder dbPosOrder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DbPosShiftPeriod(DbPosOrder dbPosOrder, JdbcTemplate jdbcTemplate) {
        this.dbPosOrder = dbPosOrder;
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean checkShiftStart(int companyId, int branchId) {
        try {
            String sql = String.format(
                    "SELECT EXISTS (SELECT 1 FROM C_%d.\"PosShiftPeriod\" WHERE \"branchId\" = ? AND \"ShiftEndTime\" IS NULL);",
                    companyId
            );
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId);
            return exists != null && exists;
        } catch (Exception e) {
            System.out.println("Error checking shift start: " + e.getMessage());
            return true; // If error occurs, assume shift exists to prevent opening another one
        }
    }

    @Transactional
    public ResponseEntity<Object> startShiftPeriod(int companyId, int branchId) {
        try {
            if (checkShiftStart(companyId, branchId)) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("There is an existing opened shift.");
            }

            String sql = String.format(
                    "WITH newShift AS ( " +
                            "INSERT INTO C_%d.\"PosShiftPeriod\"(\"ShiftStartTime\", \"ShiftEndTime\", \"branchId\") " +
                            "VALUES (?, NULL, ?) " +
                            "RETURNING \"PosSOID\", \"ShiftStartTime\" " +
                            ") " +
                            "SELECT * FROM newShift;",
                    companyId
            );

            ShiftPeriod shiftPeriod = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ShiftPeriod(
                    rs.getInt("PosSOID"),
                    rs.getTimestamp("ShiftStartTime"),
                    null,
                    null
            ), new Timestamp(System.currentTimeMillis()), branchId);

            return ResponseEntity.status(HttpStatus.CREATED).body(shiftPeriod);
        } catch (Exception e) {
            System.out.println("Error starting shift: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error starting shift.");
        }
    }

    @Transactional
    public String endShiftPeriod(int companyId, int shiftPeriodId) {
        try {
            String sql = String.format(
                    "UPDATE C_%d.\"PosShiftPeriod\" SET \"ShiftEndTime\" = ? WHERE \"PosSOID\" = ?;",
                    companyId
            );
            int updatedRows = jdbcTemplate.update(sql, new Timestamp(System.currentTimeMillis()), shiftPeriodId);
            return updatedRows > 0 ? "The Shift Ended" : "Shift not found.";
        } catch (Exception e) {
            System.out.println("Error ending shift: " + e.getMessage());
            return "Error ending shift.";
        }
    }

    public List<Order> shiftOrdersByPeriod(int companyId, int branchId, int shiftPeriodId) {
        return dbPosOrder.getOrdersByShiftId(companyId, branchId, shiftPeriodId);
    }

    public ShiftPeriod dealingWithCurrentShiftData(int companyId, int branchId, boolean withDetails) {
        try {
            String sql = String.format(
                    "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\", \"branchId\" " +
                            "FROM C_%d.\"PosShiftPeriod\" WHERE \"branchId\" = ? AND \"ShiftEndTime\" IS NULL;",
                    companyId
            );

            ShiftPeriod shiftPeriod = jdbcTemplate.queryForObject(sql, new ShiftPeriodRowMapper(), branchId);

            if (shiftPeriod != null && withDetails) {
                shiftPeriod.setOrderShiftList((ArrayList<Order>) dbPosOrder.getOrdersByPeriod(
                        branchId,
                        shiftPeriod.getStartTime(),
                        new Timestamp(System.currentTimeMillis()),
                        companyId
                ));
            }
            return shiftPeriod;

        } catch (EmptyResultDataAccessException e) {
            System.out.println("No current shift found.");
            return null;
        } catch (Exception e) {
            System.out.println("Error dealing with current shift: " + e.getMessage());
            return null;
        }
    }

    public List<ShiftPeriod> currentBranchShiftData(int companyId, int branchId) {
        try {
            String sql = String.format(
                    "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\", \"branchId\" " +
                            "FROM C_%d.\"PosShiftPeriod\" WHERE \"branchId\" = ? ORDER BY \"PosSOID\" DESC;",
                    companyId
            );

            return jdbcTemplate.query(sql, new ShiftPeriodRowMapper(), branchId);
        } catch (Exception e) {
            System.out.println("Error getting branch shift data: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // RowMapper for ShiftPeriod
    private static class ShiftPeriodRowMapper implements RowMapper<ShiftPeriod> {
        @Override
        public ShiftPeriod mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ShiftPeriod(
                    rs.getInt("PosSOID"),
                    rs.getTimestamp("ShiftStartTime"),
                    rs.getTimestamp("ShiftEndTime"),
                    null
            );
        }
    }
}

*/

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.sql.*;

import java.util.ArrayList;


@Repository
public class DbPosShiftPeriod {

    private final DbPosOrder dbPosOrder;


    public DbPosShiftPeriod(DbPosOrder dbPosOrder) {
        this.dbPosOrder = dbPosOrder;
    }

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
    public  ArrayList<Order> ShiftOrdersByPeriod(int comId,int branchId ,int spId)
    {

        return (ArrayList<Order>) dbPosOrder.getOrdersByShiftId(comId,branchId,spId);
    }

    public  ShiftPeriod dealingWithCurrentShiftData(int comId,int branchId ,boolean withDetails)
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
                sf.setOrderShiftList((ArrayList<Order>) dbPosOrder.getOrdersByPeriod(branchId,sf.getStartTime(),new Timestamp(System.currentTimeMillis()), comId));
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
