/*package com.example.valueinsoftbackend.DatabaseRequests;


import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import org.springframework.http.ResponseEntity;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class DbClient {


    private static String checkExistClientName(int comId, String cName, String phone) {

        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "SELECT  \"clientName\" , \"clientPhone\"\n" +
                    "\tFROM C_"+comId+".\"Client\" where  \"clientPhone\" = '" + phone + "' ;";

            // create the java statement
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                return rs.getString(1) + " , Phone:" + rs.getString(2) + " ";


                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(" no user exist");
            return null;

        }
        return null;

    }


    public static  ResponseEntity<ArrayList<Client>> getClientByPhoneNumberOrName(int comId ,String phone, String name, Timestamp date, String shiftStartTime, int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (branchId == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" \"branchId\" =" + branchId + " And ");
            }

            if (name == null) {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM C_"+comId+".\"Client\" where "+stringBuilder+" \"clientPhone\" = '" + phone + "' ;";
            } else {
                query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                        "\tFROM C_"+comId+".\"Client\" where "+stringBuilder+" \"clientName\" LIKE '%" + name + "%' ;";
            }

            System.out.println(query);
            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
               // System.out.println("add  connected to company " + rs.getTimestamp(7));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        null
                );
                cList.add(cl);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return  ResponseEntity.ok().body(cList);
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return  ResponseEntity.noContent().build();

        }

    }

    public static ArrayList<Client> getLatestClients(int comId , int max, int branchId) {
        ArrayList<Client> cList = new ArrayList<>();

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (branchId == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" where \"branchId\" =" + branchId + "");
            }
            query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                    "\tFROM C_"+comId+".\"Client\" "+stringBuilder.toString()+" ORDER BY c_id DESC LIMIT  " + max + ";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        //rs.getTimestamp(6)
                        null
                );
                cList.add(cl);
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
            return cList;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;

    }
    public static Client getClientById(int companyId, int bid, int clientId) {

        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (bid == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" \"branchId\" =" + bid + " And ");
            }

            query = "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\"\n" +
                    "\tFROM C_"+companyId+".\"Client\" where  "+stringBuilder+"  c_id = " + clientId + ";";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);

            while (rs.next()) {
                System.out.println("add  connected to company " + rs.getString(1));

                Client cl = new Client(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        //rs.getTimestamp(6)
                        null
                );

                System.out.println(cl);
                return cl;
                // print the results
            }

            rs.close();
            st.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;
    }

    static public String AddClient(int comId,String clientName, String phoneNumber, int branchId, String gender, String desc) {
        try {

            String checkExist = checkExistClientName(comId ,clientName, phoneNumber);
            if (checkExist != null) {
                String[] parts = checkExist.split(" ,");
                System.out.println(parts[1]);
                if (parts[1].length() > 10) {
                    return "The " + parts[1] + " is taken by: " + parts[0];

                }
                return "The Client Name exist: " + checkExist;
            }

            Connection conn = ConnectionPostgres.getConnection();


            PreparedStatement stmt = conn.prepareStatement("INSERT INTO C_"+comId+".\"Client\"(\n" +
                    "\t \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\")\n" +
                    "\tVALUES ( ?, ?, ?, ?, ?, ?);");

            stmt.setString(1, clientName);
            stmt.setString(2, phoneNumber);
            stmt.setString(3, gender);
            stmt.setString(4, desc);
            stmt.setInt(5, branchId);
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));

            int i = stmt.executeUpdate();
            System.out.println(i + " records inserted");
            stmt.close();
            conn.close();

            // Crate Branch table for new branch in DB

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the user not added -> error in server!";

        }

        return "the Client added! " + clientName;
    }

    //todo Update
    static public String updateClient(int comId, Client client, int branchId) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("UPDATE C_"+comId+".\"Client\"\n" +
                    "\tSET  \"clientName\"=?, \"clientPhone\"=?, gender=?, description=?, \"branchId\"=?\n" +
                    "\tWHERE c_id =?;");

            stmt.setString(1, client.getClientName());
            stmt.setString(2, client.getClientPhone());
            stmt.setString(3, client.getGender());
            stmt.setString(4, client.getDescription());
            stmt.setInt(5, branchId);
            stmt.setInt(6, client.getClientId());

            int i = stmt.executeUpdate();
            System.out.println(i + " client update record ");
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "the client not updates by error!";
        }
        return "the client updates with (ok 200)";
    }

    //todo -- delete
    public static boolean deleteClient(int comId, int clId, int branchId) {
        System.out.println("text -> " + clId + " " + branchId);
        try {
            Connection conn = ConnectionPostgres.getConnection();

            String query = "DELETE FROM C_"+comId+".\"Client\" \n" +
                    "\tWHERE c_id = " + clId + ";";

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

    //todo Features
    public static HashMap<String, ArrayList<String>> getClientsByYear(int companyId, int bid) {
        try {
            Connection conn = ConnectionPostgres.getConnection();
            String query = "";
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println(timestamp);

            StringBuilder stringBuilder = new StringBuilder("");

            if (bid == 0) {
                stringBuilder.append("");
            }else
            {
                stringBuilder.append(" \"branchId\" =" + bid + " And ");
            }

            query = "SELECT count(c_id) as numClients, TO_CHAR(date_trunc('month', \"registeredTime\"), 'Mon') AS \"mon\"\n" +
                    "\tFROM c_"+companyId+".\"Client\" where "+stringBuilder+" date_trunc('year', \"registeredTime\") = date_trunc('year', now()::timestamp) \n" +
                    "GROUP BY\n" +
                    "\t mon ;";


            // create the java statement
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(query);
            HashMap<String, ArrayList<String>> integerStringHashMap = new HashMap<>();
            ArrayList<String>integers = new ArrayList<>();
            ArrayList<String> strings = new ArrayList<>();
            while (rs.next()) {
                integers.add(rs.getInt(1)+"");
                strings.add((rs.getString(2)));
            }
            integerStringHashMap.put("labels",strings);
            integerStringHashMap.put("data",integers);

            rs.close();
            st.close();
            conn.close();
            return integerStringHashMap;

        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());

        }
        return null;
    }



}
*/

/*
v2
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Repository
public class DbClient {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DbClient(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String checkExistClientName(int comId, String cName, String phone) {
        try {
            String query = "SELECT \"clientName\", \"clientPhone\" FROM C_" + comId + ".\"Client\" WHERE \"clientPhone\" = ?";
            return jdbcTemplate.query(query, new Object[]{phone}, rs -> {
                if (rs.next()) {
                    return rs.getString(1) + " , Phone:" + rs.getString(2);
                }
                return null;
            });
        } catch (Exception e) {
            System.out.println("No user exists");
            return null;
        }
    }

    public ResponseEntity<ArrayList<Client>> getClientByPhoneNumberOrName(int comId, String phone, String name, Timestamp date, String shiftStartTime, int branchId) {
        try {
            StringBuilder queryBuilder = new StringBuilder("SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM C_")
                    .append(comId).append(".\"Client\" WHERE ");
            List<Object> params = new ArrayList<>();

            if (branchId != 0) {
                queryBuilder.append("\"branchId\" = ? AND ");
                params.add(branchId);
            }

            if (name == null) {
                queryBuilder.append("\"clientPhone\" = ?");
                params.add(phone);
            } else {
                queryBuilder.append("\"clientName\" ILIKE ?");
                params.add("%" + name + "%");
            }

            List<Client> clients = jdbcTemplate.query(queryBuilder.toString(), params.toArray(), getClientRowMapper());
            return ResponseEntity.ok(new ArrayList<>(clients));
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return ResponseEntity.noContent().build();
        }
    }

    public ArrayList<Client> getLatestClients(int comId, int max, int branchId) {
        try {
            StringBuilder queryBuilder = new StringBuilder("SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM C_")
                    .append(comId).append(".\"Client\"");

            List<Object> params = new ArrayList<>();

            if (branchId != 0) {
                queryBuilder.append(" WHERE \"branchId\" = ?");
                params.add(branchId);
            }

            queryBuilder.append(" ORDER BY c_id DESC LIMIT ?");
            params.add(max);

            return new ArrayList<>(jdbcTemplate.query(queryBuilder.toString(), params.toArray(), getClientRowMapper()));
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;
        }
    }

    public Client getClientById(int companyId, int bid, int clientId) {
        try {
            StringBuilder queryBuilder = new StringBuilder("SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM C_")
                    .append(companyId).append(".\"Client\" WHERE ");

            List<Object> params = new ArrayList<>();

            if (bid != 0) {
                queryBuilder.append("\"branchId\" = ? AND ");
                params.add(bid);
            }

            queryBuilder.append("c_id = ?");
            params.add(clientId);

            return jdbcTemplate.queryForObject(queryBuilder.toString(), params.toArray(), getClientRowMapper());
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;
        }
    }

    public String addClient(int comId, String clientName, String phoneNumber, int branchId, String gender, String desc) {
        try {
            String checkExist = checkExistClientName(comId, clientName, phoneNumber);
            if (checkExist != null) {
                String[] parts = checkExist.split(" ,");
                if (parts.length > 1 && parts[1].length() > 10) {
                    return "The " + parts[1] + " is taken by: " + parts[0];
                }
                return "The Client Name exists: " + checkExist;
            }

            String query = "INSERT INTO C_" + comId + ".\"Client\"(\"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\") VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(query, clientName, phoneNumber, gender, desc, branchId, new Timestamp(System.currentTimeMillis()));

            return "The Client added! " + clientName;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "The user not added -> error in server!";
        }
    }

    public String updateClient(int comId, Client client, int branchId) {
        try {
            String query = "UPDATE C_" + comId + ".\"Client\" SET \"clientName\" = ?, \"clientPhone\" = ?, gender = ?, description = ?, \"branchId\" = ? WHERE c_id = ?";
            jdbcTemplate.update(query, client.getClientName(), client.getClientPhone(), client.getGender(), client.getDescription(), branchId, client.getClientId());
            return "The client updated successfully.";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "The client was not updated due to an error.";
        }
    }

    public boolean deleteClient(int comId, int clId, int branchId) {
        try {
            String query = "DELETE FROM C_" + comId + ".\"Client\" WHERE c_id = ?";
            jdbcTemplate.update(query, clId);
            return true;
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
            return false;
        }
    }

    public HashMap<String, ArrayList<String>> getClientsByYear(int companyId, int bid) {
        try {
            StringBuilder queryBuilder = new StringBuilder("SELECT count(c_id) AS numClients, TO_CHAR(date_trunc('month', \"registeredTime\"), 'Mon') AS mon FROM C_")
                    .append(companyId).append(".\"Client\" WHERE ");

            List<Object> params = new ArrayList<>();

            if (bid != 0) {
                queryBuilder.append("\"branchId\" = ? AND ");
                params.add(bid);
            }

            queryBuilder.append("date_trunc('year', \"registeredTime\") = date_trunc('year', now()::timestamp) GROUP BY mon");

            HashMap<String, ArrayList<String>> result = new HashMap<>();
            ArrayList<String> counts = new ArrayList<>();
            ArrayList<String> months = new ArrayList<>();

            jdbcTemplate.query(queryBuilder.toString(), params.toArray(), rs -> {
                counts.add(String.valueOf(rs.getInt("numClients")));
                months.add(rs.getString("mon"));
            });

            result.put("labels", months);
            result.put("data", counts);
            return result;
        } catch (Exception e) {
            System.out.println("err : " + e.getMessage());
            return null;
        }
    }

    private RowMapper<Client> getClientRowMapper() {
        return (rs, rowNum) -> new Client(
                rs.getInt("c_id"),
                rs.getString("clientName"),
                rs.getString("clientPhone"),
                rs.getString("gender"),
                rs.getString("description"),
                null
        );
    }
}







