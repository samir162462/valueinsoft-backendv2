package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Repository
public class DbClient {

    private static final Logger log = LoggerFactory.getLogger(DbClient.class);

    private final JdbcTemplate jdbcTemplate;

    public DbClient(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String checkExistClientName(int companyId, String phone) {
        try {
            String query = "SELECT \"clientName\", \"clientPhone\" FROM " + TenantSqlIdentifiers.clientTable(companyId) +
                    " WHERE \"clientPhone\" = ?";
            return jdbcTemplate.query(query, rs -> {
                if (rs.next()) {
                    return rs.getString(1) + " , Phone:" + rs.getString(2);
                }
                return null;
            }, phone);
        } catch (Exception e) {
            log.warn("Failed to check client uniqueness for company {}", companyId, e);
            return null;
        }
    }

    public ResponseEntity<ArrayList<Client>> getClientByPhoneNumberOrName(int companyId, String phone, String name, Timestamp date, String shiftStartTime, int branchId) {
        try {
            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM ")
                    .append(TenantSqlIdentifiers.clientTable(companyId))
                    .append(" WHERE ");
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
            log.error("Failed to fetch clients for company {} branch {}", companyId, branchId, e);
            return ResponseEntity.noContent().build();
        }
    }

    public ArrayList<Client> getLatestClients(int companyId, int max, int branchId) {
        try {
            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM ")
                    .append(TenantSqlIdentifiers.clientTable(companyId));

            List<Object> params = new ArrayList<>();
            if (branchId != 0) {
                queryBuilder.append(" WHERE \"branchId\" = ?");
                params.add(branchId);
            }

            queryBuilder.append(" ORDER BY c_id DESC LIMIT ?");
            params.add(max);

            return new ArrayList<>(jdbcTemplate.query(queryBuilder.toString(), params.toArray(), getClientRowMapper()));
        } catch (Exception e) {
            log.error("Failed to fetch latest clients for company {} branch {}", companyId, branchId, e);
            return new ArrayList<>();
        }
    }

    public Client getClientById(int companyId, int branchId, int clientId) {
        try {
            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT c_id, \"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\" FROM ")
                    .append(TenantSqlIdentifiers.clientTable(companyId))
                    .append(" WHERE ");

            List<Object> params = new ArrayList<>();
            if (branchId != 0) {
                queryBuilder.append("\"branchId\" = ? AND ");
                params.add(branchId);
            }

            queryBuilder.append("c_id = ?");
            params.add(clientId);

            return jdbcTemplate.queryForObject(queryBuilder.toString(), params.toArray(), getClientRowMapper());
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch client {} for company {} branch {}", clientId, companyId, branchId, e);
            return null;
        }
    }

    public String addClient(int companyId, String clientName, String phoneNumber, int branchId, String gender, String desc) {
        try {
            String checkExist = checkExistClientName(companyId, phoneNumber);
            if (checkExist != null) {
                String[] parts = checkExist.split(" ,");
                if (parts.length > 1 && parts[1].length() > 10) {
                    return "The " + parts[1] + " is taken by: " + parts[0];
                }
                return "The Client Name exists: " + checkExist;
            }

            String query = "INSERT INTO " + TenantSqlIdentifiers.clientTable(companyId) +
                    " (\"clientName\", \"clientPhone\", gender, description, \"branchId\", \"registeredTime\") VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(query, clientName, phoneNumber, gender, desc, branchId, new Timestamp(System.currentTimeMillis()));
            return "The Client added! " + clientName;
        } catch (Exception e) {
            log.error("Failed to create client for company {} branch {}", companyId, branchId, e);
            return "The user not added -> error in server!";
        }
    }

    public String updateClient(int companyId, Client client, int branchId) {
        try {
            String query = "UPDATE " + TenantSqlIdentifiers.clientTable(companyId) +
                    " SET \"clientName\" = ?, \"clientPhone\" = ?, gender = ?, description = ?, \"branchId\" = ? WHERE c_id = ?";
            jdbcTemplate.update(query, client.getClientName(), client.getClientPhone(), client.getGender(), client.getDescription(), branchId, client.getClientId());
            return "The client updated successfully.";
        } catch (Exception e) {
            log.error("Failed to update client {} for company {}", client.getClientId(), companyId, e);
            return "The client was not updated due to an error.";
        }
    }

    public boolean deleteClient(int companyId, int clientId, int branchId) {
        try {
            String query = "DELETE FROM " + TenantSqlIdentifiers.clientTable(companyId) + " WHERE c_id = ?";
            jdbcTemplate.update(query, clientId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete client {} for company {} branch {}", clientId, companyId, branchId, e);
            return false;
        }
    }

    public HashMap<String, ArrayList<String>> getClientsByYear(int companyId, int branchId) {
        try {
            StringBuilder queryBuilder = new StringBuilder(
                    "SELECT count(c_id) AS numClients, TO_CHAR(date_trunc('month', \"registeredTime\"), 'Mon') AS mon FROM ")
                    .append(TenantSqlIdentifiers.clientTable(companyId))
                    .append(" WHERE ");

            List<Object> params = new ArrayList<>();
            if (branchId != 0) {
                queryBuilder.append("\"branchId\" = ? AND ");
                params.add(branchId);
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
            log.error("Failed to fetch clients by year for company {} branch {}", companyId, branchId, e);
            return new HashMap<>();
        }
    }

    private RowMapper<Client> getClientRowMapper() {
        return (rs, rowNum) -> new Client(
                rs.getInt("c_id"),
                rs.getString("clientName"),
                rs.getString("clientPhone"),
                rs.getString("gender"),
                rs.getString("description"),
                rs.getTimestamp("registeredTime")
        );
    }
}
