package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Repository
public class DbNotificationDevice {
    private final JdbcTemplate jdbc;

    public DbNotificationDevice(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockRegistration(String identityKey, byte[] tokenHash) {
        jdbc.queryForObject(
                """
                SELECT TRUE
                FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) locked
                """,
                Boolean.class,
                "token|" + HexFormat.of().formatHex(tokenHash));
        jdbc.queryForObject(
                """
                SELECT TRUE
                FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) locked
                """,
                Boolean.class,
                "identity|" + identityKey);
    }

    public Optional<NotificationDevice> findIdentity(String installId,
                                                     String provider,
                                                     String appBundleId,
                                                     String apnsEnvironment,
                                                     int userId,
                                                     long companyId) {
        return jdbc.query("""
                SELECT * FROM public.notification_device
                WHERE install_id=? AND provider=? AND app_bundle_id=?
                  AND apns_environment=? AND user_id=? AND company_id=?
                FOR UPDATE
                """, this::map,
                installId, provider, appBundleId, apnsEnvironment, userId, companyId)
                .stream().findFirst();
    }

    public List<NotificationDevice> revokeOtherTokenHolders(byte[] tokenHash,
                                                            String provider,
                                                            String appBundleId,
                                                            String apnsEnvironment,
                                                            Long excludedDeviceId,
                                                            String targetInstallId,
                                                            int targetUserId,
                                                            long targetCompanyId,
                                                            int actorUserId) {
        List<NotificationDevice> conflicts = jdbc.query("""
                SELECT * FROM public.notification_device
                WHERE token_hash=? AND provider=? AND app_bundle_id=?
                  AND apns_environment=? AND status='active'
                  AND (?::bigint IS NULL OR device_id<>?::bigint)
                FOR UPDATE
                """, this::map, tokenHash, provider, appBundleId, apnsEnvironment,
                excludedDeviceId, excludedDeviceId);
        for (NotificationDevice conflict : conflicts) {
            String reason = reassignmentReason(
                    conflict, targetInstallId, targetUserId, targetCompanyId);
            jdbc.update("""
                    UPDATE public.notification_device
                    SET status='revoked', revoked_at=NOW(),
                        revoked_reason=?,
                        binding_version=binding_version+1
                    WHERE device_id=?
                    """, reason, conflict.deviceId());
            audit(conflict, conflict.bindingVersion() + 1, conflict.userId(), null,
                    reason, actorUserId);
            cancelQueued(conflict.deviceId());
        }
        return conflicts;
    }

    public NotificationDevice insert(int userId,
                                     long companyId,
                                     Integer branchId,
                                     Registration registration,
                                     byte[] encrypted,
                                     String keyId,
                                     byte[] hash,
                                     OffsetDateTime rotatedAt) {
        return jdbc.query("""
                INSERT INTO public.notification_device (
                    user_id, company_id, branch_id, install_id, provider, app_bundle_id,
                    apns_environment, platform, push_token_enc, token_key_id, token_hash,
                    app_version, os_version, payload_version_max, locale, timezone,
                    last_rotated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """, this::map,
                userId, companyId, branchId, registration.installId(), registration.provider(),
                registration.appBundleId(), registration.apnsEnvironment(),
                registration.platform(), encrypted, keyId, hash, registration.appVersion(),
                registration.osVersion(), registration.payloadVersionMax(),
                registration.locale(), registration.timezone(), rotatedAt).getFirst();
    }

    public NotificationDevice update(NotificationDevice existing,
                                     Registration registration,
                                     byte[] encrypted,
                                     String keyId,
                                     byte[] hash,
                                     OffsetDateTime rotatedAt,
                                     boolean bump,
                                     String reason,
                                     int actorUserId) {
        long nextVersion = existing.bindingVersion() + (bump ? 1 : 0);
        NotificationDevice updated = jdbc.query("""
                UPDATE public.notification_device
                SET branch_id=?, platform=?, push_token_enc=?, token_key_id=?, token_hash=?,
                    app_version=?, os_version=?, payload_version_max=?, locale=?, timezone=?,
                    status='active', consecutive_failures=0, invalidated_at=NULL,
                    revoked_at=NULL, revoked_reason=NULL, last_seen_at=NOW(),
                    last_rotated_at=?, binding_version=?
                WHERE device_id=?
                RETURNING *
                """, this::map,
                registration.branchId(), registration.platform(), encrypted, keyId, hash,
                registration.appVersion(), registration.osVersion(),
                registration.payloadVersionMax(), registration.locale(),
                registration.timezone(), rotatedAt, nextVersion, existing.deviceId()).getFirst();
        if (bump) {
            audit(existing, nextVersion, existing.userId(), existing.userId(),
                    reason, actorUserId);
            cancelQueued(existing.deviceId());
        }
        return updated;
    }

    public List<NotificationDevice> activeForUser(long companyId, int userId) {
        return jdbc.query("""
                SELECT * FROM public.notification_device
                WHERE company_id=? AND user_id=? AND status='active'
                ORDER BY device_id
                """, this::map, companyId, userId);
    }

    public Optional<NotificationDevice> findById(long deviceId) {
        return jdbc.query("SELECT * FROM public.notification_device WHERE device_id=?",
                this::map, deviceId).stream().findFirst();
    }

    public Optional<NotificationDevice> findByUuid(java.util.UUID deviceUuid) {
        return jdbc.query("SELECT * FROM public.notification_device WHERE device_uuid=?",
                this::map, deviceUuid).stream().findFirst();
    }

    public boolean supportRevoke(java.util.UUID deviceUuid,
                                 long companyId,
                                 int actorUserId) {
        NotificationDevice device = findByUuid(deviceUuid).orElse(null);
        if (device == null || device.companyId() != companyId) {
            return false;
        }
        jdbc.update("""
                UPDATE public.notification_device
                SET status='revoked', revoked_at=NOW(),
                    revoked_reason='support_revocation',
                    binding_version=binding_version+1
                WHERE device_id=?
                """, device.deviceId());
        audit(device, device.bindingVersion() + 1, device.userId(), null,
                "support_revocation", actorUserId);
        cancelQueued(device.deviceId());
        return true;
    }

    public int bumpByInstall(long companyId,
                             int userId,
                             String installId,
                             String reason,
                             boolean revoke) {
        List<NotificationDevice> devices = jdbc.query("""
                SELECT * FROM public.notification_device
                WHERE company_id=? AND user_id=? AND install_id=?
                FOR UPDATE
                """, this::map, companyId, userId, installId);
        int changed = 0;
        for (NotificationDevice device : devices) {
            jdbc.update("""
                    UPDATE public.notification_device
                    SET binding_version=binding_version+1,
                        status=CASE WHEN ? THEN 'revoked' ELSE status END,
                        revoked_at=CASE WHEN ? THEN NOW() ELSE revoked_at END,
                        revoked_reason=CASE WHEN ? THEN ? ELSE revoked_reason END,
                        last_seen_at=NOW()
                    WHERE device_id=?
                    """, revoke, revoke, revoke, reason, device.deviceId());
            audit(device, device.bindingVersion() + 1, device.userId(),
                    revoke ? null : device.userId(), reason, userId);
            cancelQueued(device.deviceId());
            changed++;
        }
        return changed;
    }

    public void resetFailures(long deviceId) {
        jdbc.update("""
                UPDATE public.notification_device
                SET consecutive_failures=0, last_seen_at=NOW()
                WHERE device_id=?
                """, deviceId);
    }

    public void invalidate(long deviceId,
                           String reason,
                           OffsetDateTime invalidatedAt,
                           boolean revoke,
                           boolean staleImmediately,
                           int actorUserId) {
        NotificationDevice existing = findById(deviceId).orElse(null);
        if (existing == null) {
            return;
        }
        jdbc.update("""
                UPDATE public.notification_device
                SET consecutive_failures=consecutive_failures+1,
                    status=CASE
                      WHEN ? THEN 'revoked'
                      WHEN ? THEN 'stale'
                      WHEN consecutive_failures+1>=3 THEN 'stale'
                      ELSE status
                    END,
                    invalidated_at=?,
                    revoked_at=CASE WHEN ? THEN NOW() ELSE revoked_at END,
                    revoked_reason=CASE WHEN ? THEN ? ELSE revoked_reason END,
                    binding_version=binding_version+1
                WHERE device_id=?
                """, revoke, staleImmediately, invalidatedAt,
                revoke, revoke, reason, deviceId);
        audit(existing, existing.bindingVersion() + 1, existing.userId(),
                revoke ? null : existing.userId(), "provider_invalidated", actorUserId);
        cancelQueued(deviceId);
    }

    public int cancelQueued(long deviceId) {
        return jdbc.update("""
                UPDATE public.notification_push_outbox
                SET status='cancelled', cancelled_reason='DEVICE_BINDING_CHANGED',
                    claimed_by=NULL, claimed_at=NULL, claim_expires_at=NULL
                WHERE device_id=? AND status IN ('pending','failed')
                """, deviceId);
    }

    private void audit(NotificationDevice device,
                       long toVersion,
                       Integer fromUserId,
                       Integer toUserId,
                       String reason,
                       Integer actorUserId) {
        jdbc.update("""
                INSERT INTO public.notification_device_binding_audit (
                    device_id, company_id, from_version, to_version,
                    from_user_id, to_user_id, reason, actor_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, device.deviceId(), device.companyId(), device.bindingVersion(), toVersion,
                fromUserId, toUserId, reason, actorUserId);
    }

    private NotificationDevice map(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new NotificationDevice(
                rs.getLong("device_id"),
                rs.getObject("device_uuid", java.util.UUID.class),
                rs.getInt("user_id"),
                rs.getLong("company_id"),
                (Integer) rs.getObject("branch_id"),
                rs.getString("install_id"),
                rs.getString("provider"),
                rs.getString("app_bundle_id"),
                rs.getString("apns_environment"),
                rs.getString("platform"),
                rs.getLong("binding_version"),
                rs.getBytes("push_token_enc"),
                rs.getString("token_key_id"),
                rs.getBytes("token_hash"),
                rs.getString("locale"),
                rs.getString("timezone"),
                rs.getInt("payload_version_max"),
                rs.getString("status"),
                rs.getInt("consecutive_failures"),
                rs.getObject("invalidated_at", OffsetDateTime.class),
                rs.getObject("last_rotated_at", OffsetDateTime.class));
    }

    private static String reassignmentReason(NotificationDevice conflict,
                                             String targetInstallId,
                                             int targetUserId,
                                             long targetCompanyId) {
        if (conflict.installId().equals(targetInstallId)) {
            if (conflict.companyId() != targetCompanyId) {
                return "company_switch";
            }
            if (conflict.userId() != targetUserId) {
                return "user_switch";
            }
        }
        return "token_reassigned";
    }

    public record Registration(
            String installId,
            String provider,
            String appBundleId,
            String apnsEnvironment,
            String platform,
            Integer branchId,
            String appVersion,
            String osVersion,
            int payloadVersionMax,
            String locale,
            String timezone
    ) {
        public String identityKey(int userId, long companyId) {
            return installId + "|" + provider + "|" + appBundleId + "|"
                    + apnsEnvironment + "|" + userId + "|" + companyId;
        }
    }
}
