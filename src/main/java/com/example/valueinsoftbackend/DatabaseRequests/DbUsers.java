package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbUsers {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // your methods go here
    private static final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getInt("id"),
            rs.getString("userName"),
            rs.getString("userPassword"),
            rs.getString("userEmail"),
            rs.getString("firstName"),
            rs.getString("lastName"),
            rs.getString("userPhone"),
            rs.getString("userRole"),
            rs.getInt("gender"),
            rs.getInt("branchId"),
            rs.getTimestamp("creationTime")
    );

    public boolean checkExistUsername(String userName) {
        String sql = "SELECT COUNT(*) FROM public.users WHERE \"userName\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userName);
        return count != null && count > 0;
    }

    public boolean checkExistingEmail(String email) {
        String sql = "SELECT COUNT(*) FROM public.users WHERE \"userEmail\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public User getUser(String userName) {
        try {
            String sql = "SELECT * FROM public.users WHERE \"userName\" = ?";
            return jdbcTemplate.queryForObject(sql, userRowMapper, userName);
        } catch (Exception e) {
            System.out.println("err in get user : " + e.getMessage());
            return null;
        }
    }

    public User getUserDetails(String userName) {
        try {
            String sql = "SELECT id, \"userName\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\" " +
                    "FROM public.users WHERE \"userName\" = ?";
            return jdbcTemplate.queryForObject(sql, userRowMapper, userName);
        } catch (Exception e) {
            System.out.println("err in get user details: " + e.getMessage());
            return null;
        }
    }

    public List<User> getAllUsers(int branchId) {
        String sql = "SELECT * FROM public.users";
        if (branchId != 0) {
            sql += " WHERE \"branchId\" = ?";
            return jdbcTemplate.query(sql, userRowMapper, branchId);
        } else {
            return jdbcTemplate.query(sql, userRowMapper);
        }
    }


    public String getUserImg(String userName) {
        try {
            String sql = "SELECT \"imgFile\" FROM public.users WHERE \"userName\" = ?";
            return jdbcTemplate.queryForObject(sql, String.class, userName);
        } catch (Exception e) {
            System.out.println("err in get user img : " + e.getMessage());
            return "";
        }
    }

    public String addUser(String username, String password, String email, String role, String fName, String lName, int gender, String userPhone, int branchId, String imgFile) {
        if (checkExistUsername(username)) {
            return "The user exist! in the db";
        }
        if (checkExistingEmail(email)) {
            return "The email exist! in the db";
        }
        String sql = "INSERT INTO public.users (\"userName\", \"userPassword\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\", \"imgFile\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int rows = jdbcTemplate.update(sql, username, password, email, role, userPhone, branchId, fName, lName, gender, new Timestamp(System.currentTimeMillis()), imgFile);
        return rows > 0 ? "The user added!" : "The user not added because of error!";
    }

    public String updateRole(String schemaName, int userId, String newRole) {
        String sql = "UPDATE " + schemaName + ".users SET \"userRole\" = ? WHERE id = ?";
        int rows = jdbcTemplate.update(sql, newRole, userId);
        return rows > 0 ? "The user Role Updated!" : "The user not Updated because of error!";
    }

    public ResponseEntity<String> updateUserPassword(String userName, String oldPassword, String userPassword) {
        String sql = "UPDATE public.users SET \"userPassword\" = ? WHERE \"userName\" = ? AND \"userPassword\" = ?";
        int rows = jdbcTemplate.update(sql, userPassword, userName, oldPassword);
        if (rows == 1) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("The Password Changed!");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("The Old Password incorrect!");
        }
    }

    public ResponseEntity<String> updateUserImg(String userName, String img) {
        String sql = "UPDATE public.users SET \"imgFile\" = ? WHERE \"userName\" = ?";
        int rows = jdbcTemplate.update(sql, img, userName);
        if (rows == 1) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("The Image Updated!");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("User not found!");
        }
    }

}
