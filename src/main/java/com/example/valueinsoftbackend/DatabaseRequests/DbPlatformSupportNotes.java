package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNoteItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNotesPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public class DbPlatformSupportNotes {

    private static final RowMapper<PlatformSupportNoteItem> SUPPORT_NOTE_ROW_MAPPER = (rs, rowNum) ->
            new PlatformSupportNoteItem(
                    rs.getLong("note_id"),
                    rs.getInt("tenant_id"),
                    rs.getString("company_name"),
                    (Integer) rs.getObject("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("note_type"),
                    rs.getString("subject"),
                    rs.getString("body"),
                    rs.getString("visibility"),
                    (Integer) rs.getObject("created_by_user_id"),
                    rs.getString("created_by_user_name"),
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("updated_at")
            );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformSupportNotes(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformSupportNotesPageResponse getSupportNotes(Integer tenantId,
                                                            Integer branchId,
                                                            String noteType,
                                                            String visibility,
                                                            int page,
                                                            int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedSize)
                .addValue("offset", offset);

        String whereClause = buildWhereClause(tenantId, branchId, noteType, visibility, params);
        String baseSql = "FROM public.platform_support_notes psn " +
                "JOIN public.\"Company\" c ON c.id = psn.tenant_id " +
                "LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = psn.branch_id " +
                whereClause;

        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseSql,
                params,
                Long.class
        );
        long totalItems = totalItemsValue == null ? 0 : totalItemsValue;

        String listSql = "SELECT psn.note_id, psn.tenant_id, c.\"companyName\" AS company_name, psn.branch_id, " +
                "b.\"branchName\" AS branch_name, psn.note_type, psn.subject, psn.body, psn.visibility, " +
                "psn.created_by_user_id, psn.created_by_user_name, psn.created_at, psn.updated_at " +
                baseSql +
                " ORDER BY psn.created_at DESC, psn.note_id DESC LIMIT :limit OFFSET :offset";

        ArrayList<PlatformSupportNoteItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(listSql, params, SUPPORT_NOTE_ROW_MAPPER)
        );

        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
        return new PlatformSupportNotesPageResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    public PlatformSupportNoteItem createSupportNote(int tenantId,
                                                     Integer branchId,
                                                     String noteType,
                                                     String subject,
                                                     String body,
                                                     String visibility,
                                                     Integer createdByUserId,
                                                     String createdByUserName) {
        String sql = "INSERT INTO public.platform_support_notes " +
                "(tenant_id, branch_id, note_type, subject, body, visibility, created_by_user_id, created_by_user_name) " +
                "VALUES (:tenantId, :branchId, :noteType, :subject, :body, :visibility, :createdByUserId, :createdByUserName)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("branchId", branchId)
                .addValue("noteType", noteType)
                .addValue("subject", subject)
                .addValue("body", body)
                .addValue("visibility", visibility)
                .addValue("createdByUserId", createdByUserId)
                .addValue("createdByUserName", createdByUserName);

        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[]{"note_id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUPPORT_NOTE_CREATE_FAILED", "Support note could not be created");
        }

        return getSupportNoteById(key.longValue());
    }

    public PlatformSupportNoteItem getSupportNoteById(long noteId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("noteId", noteId);
        String sql = "SELECT psn.note_id, psn.tenant_id, c.\"companyName\" AS company_name, psn.branch_id, " +
                "b.\"branchName\" AS branch_name, psn.note_type, psn.subject, psn.body, psn.visibility, " +
                "psn.created_by_user_id, psn.created_by_user_name, psn.created_at, psn.updated_at " +
                "FROM public.platform_support_notes psn " +
                "JOIN public.\"Company\" c ON c.id = psn.tenant_id " +
                "LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = psn.branch_id " +
                "WHERE psn.note_id = :noteId";
        ArrayList<PlatformSupportNoteItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(sql, params, SUPPORT_NOTE_ROW_MAPPER)
        );
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUPPORT_NOTE_NOT_FOUND", "Support note not found");
        }
        return items.get(0);
    }

    public ArrayList<PlatformSupportNoteItem> getRecentBillingNotes(int tenantId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", safeLimit);

        String sql = "SELECT psn.note_id, psn.tenant_id, c.\"companyName\" AS company_name, psn.branch_id, " +
                "b.\"branchName\" AS branch_name, psn.note_type, psn.subject, psn.body, psn.visibility, " +
                "psn.created_by_user_id, psn.created_by_user_name, psn.created_at, psn.updated_at " +
                "FROM public.platform_support_notes psn " +
                "JOIN public.\"Company\" c ON c.id = psn.tenant_id " +
                "LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = psn.branch_id " +
                "WHERE psn.tenant_id = :tenantId AND LOWER(psn.note_type) = 'billing' " +
                "ORDER BY psn.created_at DESC, psn.note_id DESC LIMIT :limit";

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, SUPPORT_NOTE_ROW_MAPPER));
    }

    public int countBillingNotes(int tenantId, String visibility) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId);

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM public.platform_support_notes psn " +
                        "WHERE psn.tenant_id = :tenantId AND LOWER(psn.note_type) = 'billing' "
        );
        if (visibility != null && !visibility.trim().isEmpty()) {
            params.addValue("visibility", visibility.trim().toLowerCase());
            sql.append("AND LOWER(psn.visibility) = :visibility ");
        }

        Integer count = namedParameterJdbcTemplate.queryForObject(sql.toString(), params, Integer.class);
        return count == null ? 0 : count;
    }

    private String buildWhereClause(Integer tenantId,
                                    Integer branchId,
                                    String noteType,
                                    String visibility,
                                    MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            where.append(" AND psn.tenant_id = :tenantId ");
        }

        if (branchId != null) {
            params.addValue("branchId", branchId);
            where.append(" AND psn.branch_id = :branchId ");
        }

        if (noteType != null && !noteType.trim().isEmpty()) {
            params.addValue("noteType", noteType.trim().toLowerCase());
            where.append(" AND LOWER(psn.note_type) = :noteType ");
        }

        if (visibility != null && !visibility.trim().isEmpty()) {
            params.addValue("visibility", visibility.trim().toLowerCase());
            where.append(" AND LOWER(psn.visibility) = :visibility ");
        }

        return where.toString();
    }
}
