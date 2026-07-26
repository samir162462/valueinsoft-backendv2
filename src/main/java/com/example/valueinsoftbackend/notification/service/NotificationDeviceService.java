package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.NotificationDeviceView;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Service
public class NotificationDeviceService {
    private static final Set<String> PROVIDERS = Set.of("fcm", "apns");
    private static final Set<String> PLATFORMS = Set.of("android", "ios");

    private final DbNotificationDevice devices;
    private final NotificationTokenCipher cipher;

    public NotificationDeviceService(DbNotificationDevice devices,
                                     NotificationTokenCipher cipher) {
        this.devices = devices;
        this.cipher = cipher;
    }

    @Transactional
    public NotificationDeviceView register(long companyId,
                                           Integer branchId,
                                           int userId,
                                           RegistrationCommand command) {
        ValidatedRegistration validated = validate(branchId, command);
        NotificationTokenCipher.EncryptedToken encrypted = cipher.encrypt(command.pushToken());
        OffsetDateTime reportedAt = normalizeReportedAt(command.tokenReportedAt());
        DbNotificationDevice.Registration registration = new DbNotificationDevice.Registration(
                command.installId().trim(),
                validated.provider(),
                command.appBundleId().trim(),
                validated.apnsEnvironment(),
                validated.platform(),
                branchId,
                trim(command.appVersion()),
                trim(command.osVersion()),
                command.payloadVersionMax() == null
                        ? 1 : Math.max(1, command.payloadVersionMax()),
                normalizeLocale(command.locale()),
                normalizeTimezone(command.timezone()));

        devices.lockRegistration(
                registration.identityKey(userId, companyId), encrypted.hash());
        NotificationDevice existing = devices.findIdentity(
                registration.installId(),
                registration.provider(),
                registration.appBundleId(),
                registration.apnsEnvironment(),
                userId,
                companyId).orElse(null);
        Long existingId = existing == null ? null : existing.deviceId();
        devices.revokeOtherTokenHolders(
                encrypted.hash(),
                registration.provider(),
                registration.appBundleId(),
                registration.apnsEnvironment(),
                existingId,
                registration.installId(),
                userId,
                companyId,
                userId);

        NotificationDevice result;
        if (existing == null) {
            result = devices.insert(
                    userId, companyId, branchId, registration,
                    encrypted.encrypted(), encrypted.keyId(), encrypted.hash(), reportedAt);
        } else if (existing.lastRotatedAt() != null
                && !reportedAt.isAfter(existing.lastRotatedAt())) {
            result = existing;
        } else {
            boolean tokenChanged = !Arrays.equals(existing.credentialHash(), encrypted.hash());
            boolean reactivated = !"active".equals(existing.status());
            result = devices.update(
                    existing,
                    registration,
                    encrypted.encrypted(),
                    encrypted.keyId(),
                    encrypted.hash(),
                    reportedAt,
                    tokenChanged || reactivated,
                    reactivated ? "reactivated" : "token_rotated",
                    userId);
        }
        return NotificationDeviceView.from(result);
    }

    @Transactional
    public void logout(long companyId, int userId, String installId) {
        requireInstallId(installId);
        devices.bumpByInstall(companyId, userId, installId.trim(), "logout", true);
    }

    @Transactional
    public void shiftClose(long companyId, int userId, String installId) {
        requireInstallId(installId);
        devices.bumpByInstall(companyId, userId, installId.trim(), "shift_close", false);
    }

    @Transactional
    public void adminRevoke(long companyId,
                            int actorUserId,
                            java.util.UUID deviceUuid) {
        if (!devices.supportRevoke(deviceUuid, companyId, actorUserId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "NOTIFICATION_DEVICE_NOT_FOUND",
                    "Notification device was not found");
        }
    }

    private static ValidatedRegistration validate(Integer branchId, RegistrationCommand command) {
        if (command == null) {
            throw badRequest("DEVICE_REQUEST_REQUIRED", "Device registration is required");
        }
        requireInstallId(command.installId());
        String provider = lower(command.provider());
        String platform = lower(command.platform());
        if (!PROVIDERS.contains(provider)) {
            throw badRequest("DEVICE_PROVIDER_INVALID", "Provider must be fcm or apns");
        }
        if (!PLATFORMS.contains(platform)) {
            throw badRequest("DEVICE_PLATFORM_INVALID", "Platform must be android or ios");
        }
        if ("apns".equals(provider) && !"ios".equals(platform)) {
            throw badRequest("DEVICE_PROVIDER_PLATFORM_MISMATCH",
                    "APNs devices must use the iOS platform");
        }
        if ("fcm".equals(provider) && !"android".equals(platform)) {
            throw badRequest("DEVICE_PROVIDER_PLATFORM_MISMATCH",
                    "FCM devices must use the Android platform");
        }
        if (command.appBundleId() == null || command.appBundleId().isBlank()
                || command.appBundleId().length() > 255) {
            throw badRequest("DEVICE_BUNDLE_INVALID", "A valid app bundle id is required");
        }
        String environment = lower(command.apnsEnvironment());
        if ("apns".equals(provider)) {
            if (!Set.of("sandbox", "production").contains(environment)) {
                throw badRequest("DEVICE_APNS_ENV_INVALID",
                        "APNs environment must be sandbox or production");
            }
        } else {
            environment = "none";
        }
        if (branchId != null && branchId <= 0) {
            throw badRequest("DEVICE_BRANCH_INVALID", "Branch id must be positive");
        }
        return new ValidatedRegistration(provider, platform, environment);
    }

    private static OffsetDateTime normalizeReportedAt(OffsetDateTime reportedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        if (reportedAt == null
                || Duration.between(now, reportedAt).abs().compareTo(Duration.ofMinutes(5)) > 0) {
            return now;
        }
        return reportedAt;
    }

    private static void requireInstallId(String installId) {
        if (installId == null || installId.isBlank() || installId.length() > 255) {
            throw badRequest("DEVICE_INSTALL_ID_INVALID", "A valid install id is required");
        }
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        String normalized = Locale.forLanguageTag(locale).toLanguageTag();
        return normalized.isBlank() ? "en" : normalized;
    }

    private static String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "UTC";
        }
        try {
            return java.time.ZoneId.of(timezone).getId();
        } catch (RuntimeException exception) {
            throw badRequest("DEVICE_TIMEZONE_INVALID", "Timezone must be a valid IANA zone");
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record ValidatedRegistration(
            String provider, String platform, String apnsEnvironment) {
    }

    public record RegistrationCommand(
            String installId,
            String provider,
            String appBundleId,
            String apnsEnvironment,
            String platform,
            String pushToken,
            OffsetDateTime tokenReportedAt,
            String appVersion,
            String osVersion,
            Integer payloadVersionMax,
            String locale,
            String timezone
    ) {
    }
}
