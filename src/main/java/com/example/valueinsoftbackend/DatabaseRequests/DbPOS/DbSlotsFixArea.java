/*
 * Copyright (c) Samir Filifl
 */
/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DbSlotsFixArea {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static JsonNode stringToJSONObject(String jsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(jsonString);
    }

    public ResponseEntity<Object> getFixAreaSlot(int branchId, String companyName, int prevMonth) {
        try {
            String sql;
            if (prevMonth > 0) {
                sql = "SELECT \"faId\", \"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", " +
                        "problem, show, \"userName_Recived\", status, \"desc\", fees::money::numeric::float8, " +
                        "json_build_object('clientName', \"clientName\", 'clientPhone', \"clientPhone\") AS data " +
                        "FROM c_" + companyName + ".\"FixArea\" " +
                        "LEFT JOIN c_" + companyName + ".\"Client\" ON \"clientId\" = c_id " +
                        "WHERE c_" + companyName + ".\"FixArea\".\"branchId\" = ? " +
                        "AND \"dateIn\" >= date_trunc('month', current_date - interval '" + prevMonth + " month') ;" ;
                        //+ "AND \"dateIn\" < date_trunc('month', current_date);";

            } else {
                sql = "SELECT \"faId\", \"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", " +
                        "problem, show, \"userName_Recived\", status, \"desc\", fees::money::numeric::float8, " +
                        "json_build_object('clientName', \"clientName\", 'clientPhone', \"clientPhone\") AS data " +
                        "FROM c_" + companyName + ".\"FixArea\" " +
                        "LEFT JOIN c_" + companyName + ".\"Client\" ON \"clientId\" = c_id " +
                        "WHERE c_" + companyName + ".\"FixArea\".\"branchId\" = ? " +
                        "AND c_" + companyName + ".\"FixArea\".show = 'true';";
            }

            System.out.println(sql);
            List<SlotsFixArea> slotsFixAreas = jdbcTemplate.query(sql, new Object[]{branchId}, new RowMapper<SlotsFixArea>() {
                @Override
                public SlotsFixArea mapRow(ResultSet rs, int rowNum) throws SQLException {
                    try {
                        SlotsFixArea slot = new SlotsFixArea(
                                rs.getInt("faId"),
                                rs.getInt("fixSlot"),
                                rs.getInt("clientId"),
                                rs.getDate("dateIn"),
                                rs.getDate("dateFinished"),
                                rs.getString("phoneName"),
                                rs.getString("problem"),
                                rs.getBoolean("show"),
                                rs.getString("userName_Recived"),
                                rs.getString("status"),
                                rs.getString("desc"),
                                branchId,
                                rs.getBigDecimal("fees")
                        );
                        try {
                            JsonNode jsonNode = stringToJSONObject(rs.getString("data"));
                            System.out.println(jsonNode.toString());
                            slot.setClientData(jsonNode);
                        } catch (Exception ignored) {}
                        return slot;
                    } catch (Exception ex) {
                        throw new SQLException("Error mapping row", ex);
                    }
                }
            });

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(slotsFixAreas);

        } catch (Exception e) {
            System.out.println("Error in getFixAreaSlot: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(null);
        }
    }

    public ResponseEntity<Object> addFixAreaSlot(int branchId, String companyName, SlotsFixArea slotsFixArea) {
        try {
            String sql = "INSERT INTO c_" + companyName + ".\"FixArea\"(" +
                    "\"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, \"userName_Recived\", status, \"desc\", \"branchId\") " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

            int rows = jdbcTemplate.update(sql,
                    slotsFixArea.getFixSlot(),
                    slotsFixArea.getClientId(),
                    slotsFixArea.getDateIn(),
                    slotsFixArea.getDateFinished(),
                    slotsFixArea.getPhoneName(),
                    slotsFixArea.getProblem(),
                    slotsFixArea.isShow(),
                    slotsFixArea.getUserName_Recived(),
                    slotsFixArea.getStatus(),
                    slotsFixArea.getDesc(),
                    slotsFixArea.getBranchId()
            );

            System.out.println(rows + " records inserted");

            return ResponseEntity.status(HttpStatus.CREATED).body("The Fix Slot Added (success)");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("The Fix Slot Not Added (Error): " + e.getMessage());
        }
    }

    public boolean deleteDamagedItem(int branchId, String companyName, int dId) {
        try {
            String sql = "DELETE FROM public.\"DamagedList\" WHERE \"DId\" = ?;";
            int rows = jdbcTemplate.update(sql, dId);
            return rows > 0;
        } catch (Exception e) {
            System.out.println("Error in deleteDamagedItem: " + e.getMessage());
            return false;
        }
    }

    public ResponseEntity<Object> updateFixAreaSlot(String companyName, SlotsFixArea slotsFixArea) {
        try {
            String sql = "UPDATE C_" + companyName + ".\"FixArea\" " +
                    "SET \"dateFinished\"=?, problem=?, show=?, status=?, \"desc\"=?, fees=? " +
                    "WHERE \"faId\"=? AND \"branchId\"=?;";

            int rows = jdbcTemplate.update(sql,
                    slotsFixArea.getDateFinished(),
                    slotsFixArea.getProblem(),
                    slotsFixArea.isShow(),
                    slotsFixArea.getStatus(),
                    slotsFixArea.getDesc(),
                    slotsFixArea.getFees(),
                    slotsFixArea.getFaId(),
                    slotsFixArea.getBranchId()
            );

            System.out.println(rows + " Fix Slot updated");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body("The Fix Slot Updated (success)");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("The Fix Slot Not Updated (Failed)");
        }
    }
}


/*
package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Slots.SlotsFixArea;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;


@Repository
public class DbSlotsFixArea {

    public static JsonNode stringToJSONObject(String jsonString) throws Exception {
        ObjectMapper jacksonObjMapper = new ObjectMapper();
        return jacksonObjMapper.readTree(jsonString);
    }

    public static  ResponseEntity<Object> getFixAreaSlot(int branchId, String companyName,int prevMonth) {
        ArrayList<SlotsFixArea> slotsFixAreas = new ArrayList<>();
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "SELECT \"faId\", \"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, \"userName_Recived\", status, \"desc\", fees::money::numeric::float8 ,json_build_object('clientName', \"clientName\", 'clientPhone', \"clientPhone\") AS data \n" +
                    "\tFROM c_"+companyName+".\"FixArea\"  LEFT JOIN c_"+companyName+".\"Client\" ON \"clientId\" = c_id where c_"+companyName+".\"FixArea\".\"branchId\"  ="+branchId+" and  c_"+companyName+".\"FixArea\".show =  'true' ;";

            String queryHistory = "SELECT \"faId\", \"fixSlot\", \"clientId\", \"dateIn\", \"dateFinished\", \"phoneName\", problem, show, \"userName_Recived\", status, \"desc\",fees::money::numeric::float8 ,json_build_object('clientName', \"clientName\", 'clientPhone', \"clientPhone\") AS data \n" +
                    "\tFROM c_"+companyName+".\"FixArea\"  LEFT JOIN c_"+companyName+".\"Client\" ON \"clientId\" = c_id where c_"+companyName+".\"FixArea\".\"branchId\"  ="+branchId+" and  \"dateIn\" >= date_trunc('month', current_date - interval '"+prevMonth+"' month)\n" +
                    "  \tand \"dateIn\" < date_trunc('month', current_date);";
            Statement st = conn.createStatement();
            ResultSet rs = null;

            if (prevMonth > 0) {
                rs = st.executeQuery(queryHistory);
                System.out.println(queryHistory);

            }else
            {
                rs = st.executeQuery(query);
                System.out.println(query);

            }
            int x = 0 ;
            while (rs.next()) {

                System.out.println(++x);
                    SlotsFixArea slot = new SlotsFixArea(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getDate(4),
                            rs.getDate(5), rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getString(9)
                            ,rs.getString(10), rs.getString(11),branchId,rs.getBigDecimal(12));
                try {
                    JsonNode jsonNode = stringToJSONObject(rs.getString(13));
                    slot.setClientData(jsonNode);

                }catch (Exception err){
                }

                    slotsFixAreas.add(slot);
                }

            rs.close();
            st.close();
            conn.close();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body( slotsFixAreas);
        } catch (Exception e) {
            System.out.println("err in get getFixAreaSlot : " + e.getMessage());
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
                    "\tSET    \"dateFinished\"=?, problem=?, show=?, status=?, \"desc\"=? , fees=?\n" +
                    "\tWHERE \"faId\"=? And  \"branchId\"=?;");

            stmt.setDate(1, slotsFixArea.getDateFinished());
            stmt.setString(2, slotsFixArea.getProblem());
            stmt.setBoolean(3, slotsFixArea.isShow());
            stmt.setString(4, slotsFixArea.getStatus());
            stmt.setString(5, slotsFixArea.getDesc());
            stmt.setBigDecimal(6, slotsFixArea.getFees());
            stmt.setInt(7, slotsFixArea.getFaId());
            stmt.setInt(8, slotsFixArea.getBranchId());
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
*/