package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbUsers {

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

    private static final RowMapper<User> userDetailsRowMapper = (rs, rowNum) -> new User(
            rs.getInt("id"),
            rs.getString("userName"),
            null,
            rs.getString("userEmail"),
            rs.getString("firstName"),
            rs.getString("lastName"),
            rs.getString("userPhone"),
            rs.getString("userRole"),
            rs.getInt("gender"),
            rs.getInt("branchId"),
            rs.getTimestamp("creationTime")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DbUsers(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

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
        String sql = "SELECT * FROM public.users WHERE \"userName\" = ?";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, userName);
        return users.isEmpty() ? null : users.get(0);
    }

    public User getUserDetails(String userName) {
        String sql = "SELECT id, \"userName\", \"userEmail\", \"userRole\", \"userPhone\", \"branchId\", " +
                "\"firstName\", \"lastName\", gender, \"creationTime\" FROM public.users WHERE \"userName\" = ?";
        List<User> users = jdbcTemplate.query(sql, userDetailsRowMapper, userName);
        return users.isEmpty() ? null : users.get(0);
    }

    public List<User> getAllUsers(int branchId) {
        String sql = "SELECT * FROM public.users";
        if (branchId != 0) {
            sql += " WHERE \"branchId\" = ?";
            return jdbcTemplate.query(sql, userRowMapper, branchId);
        }
        return jdbcTemplate.query(sql, userRowMapper);
    }

    public String getUserImg(String userName) {
        String sql = "SELECT \"imgFile\" FROM public.users WHERE \"userName\" = ?";
        List<String> images = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("imgFile"), userName);
        return images.isEmpty() ? "" : images.get(0);
    }

    public String addUser(String username, String password, String email, String role, String fName, String lName,
                          int gender, String userPhone, int branchId, String imgFile) {
        if (checkExistUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "The user exist! in the db");
        }
        if (checkExistingEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "The email exist! in the db");
        }

        String sql = "INSERT INTO public.users (\"userName\", \"userPassword\", \"userEmail\", \"userRole\", " +
                "\"userPhone\", \"branchId\", \"firstName\", \"lastName\", gender, \"creationTime\", \"imgFile\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int rows = jdbcTemplate.update(
                sql,
                username,
                passwordEncoder.encode(password),
                email,
                role,
                userPhone,
                branchId,
                fName,
                lName,
                gender,
                new Timestamp(System.currentTimeMillis()),
                imgFile
        );
        return rows > 0 ? "The user added!" : "The user not added because of error!";
    }

    public String updateRole(String schemaName, int userId, String newRole) {
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        String validatedSchemaName = TenantSqlIdentifiers.requireSchemaName(schemaName);
        String sql = "UPDATE " + validatedSchemaName + ".users SET \"userRole\" = ? WHERE id = ?";
        int rows = jdbcTemplate.update(sql, newRole, userId);
        return rows > 0 ? "The user Role Updated!" : "The user not Updated because of error!";
    }

    public String updateUserPassword(String userName, String oldPassword, String userPassword) {
        User user = getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        if (!passwordEncoder.matches(oldPassword, user.getUserPassword())) {
            throw new ApiException(HttpStatus.CONFLICT, "PASSWORD_MISMATCH", "The Old Password incorrect!");
        }

        String sql = "UPDATE public.users SET \"userPassword\" = ? WHERE \"userName\" = ?";
        int rows = jdbcTemplate.update(sql, passwordEncoder.encode(userPassword), userName);
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PASSWORD_UPDATE_FAILED", "The password could not be changed");
        }
        return "The Password Changed!";
    }

    public void upgradeStoredPassword(String userName, String encodedPassword) throws DataAccessException {
        String sql = "UPDATE public.users SET \"userPassword\" = ? WHERE \"userName\" = ?";
        jdbcTemplate.update(sql, encodedPassword, userName);
    }

    public String updateUserImg(String userName, String img) {
        String sql = "UPDATE public.users SET \"imgFile\" = ? WHERE \"userName\" = ?";
        int rows = jdbcTemplate.update(sql, img, userName);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found!");
        }
        return "The Image Updated!";
    }
}
