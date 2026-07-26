package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationFeedEvent;
import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.model.NotificationFeedPage;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeedChange;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipient;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationFeedService {
    private final DbNotificationFeed feed;
    private final DbNotificationFeedChange changes;
    private final DbNotificationRecipient recipients;
    private final NotificationAudienceResolver audience;
    private final NotificationCursorCodec cursors;
    private final NotificationSummaryService summaries;

    public NotificationFeedService(DbNotificationFeed feed,
                                   DbNotificationFeedChange changes,
                                   DbNotificationRecipient recipients,
                                   NotificationAudienceResolver audience,
                                   NotificationCursorCodec cursors,
                                   NotificationSummaryService summaries) {
        this.feed = feed;
        this.changes = changes;
        this.recipients = recipients;
        this.audience = audience;
        this.cursors = cursors;
        this.summaries = summaries;
    }

    public NotificationFeedPage page(long companyId, int userId, String cursor,
                                     String category, Integer branchId,
                                     String state, int requestedSize) {
        int size = Math.max(1, Math.min(requestedSize, 100));
        List<NotificationFeedItem> raw = feed.page(companyId, userId, cursors.decode(cursor),
                category, branchId, state, size + 1);
        List<NotificationFeedItem> visible = new ArrayList<>(size);
        for (int i = 0; i < Math.min(size, raw.size()); i++) {
            NotificationFeedItem item = raw.get(i);
            if (audience.userHasCapability(companyId, userId, item.branchId(),
                    item.requiredCapability())) {
                visible.add(item);
            }
        }
        String next = raw.size() > size
                ? cursors.encode(raw.get(size - 1).lastEventAt(),
                        recipientIdForCursor(companyId, userId, raw.get(size - 1).recipientUuid()))
                : null;
        return new NotificationFeedPage(List.copyOf(visible), next);
    }

    public NotificationFeedItem detail(long companyId, int userId, UUID uuid) {
        NotificationFeedItem item = feed.require(companyId, userId, uuid);
        assertVisible(companyId, userId, item);
        return item;
    }

    public List<NotificationFeedEvent> lineage(long companyId, int userId, UUID uuid) {
        detail(companyId, userId, uuid);
        return feed.lineage(companyId, userId, uuid);
    }

    @Transactional
    public void markSeen(long companyId, int userId, List<UUID> uuids, String channel) {
        for (UUID uuid : uuids == null ? List.<UUID>of() : uuids) {
            DbNotificationFeed.LockedRecipient row = feed.lock(companyId, userId, uuid);
            if (!"unseen".equals(row.state())) {
                continue;
            }
            long sequence = changes.nextSequence(companyId);
            feed.markSeen(companyId, row.recipientId(), sequence);
            changes.insert(companyId, sequence, userId, row.recipientId(), "seen", null);
            recipients.audit(companyId, row.recipientId(), userId, row.category(),
                    row.state(), "seen", normalizeChannel(channel));
        }
        summaries.invalidateAfterCommit(companyId, userId);
    }

    @Transactional
    public void markRead(long companyId, int userId, UUID uuid, String channel) {
        DbNotificationFeed.LockedRecipient row = feed.lock(companyId, userId, uuid);
        if ("read".equals(row.state()) || "archived".equals(row.state())) {
            return;
        }
        long sequence = changes.nextSequence(companyId);
        feed.markRead(companyId, row.recipientId(), sequence);
        changes.insert(companyId, sequence, userId, row.recipientId(), "read", null);
        recipients.audit(companyId, row.recipientId(), userId, row.category(),
                row.state(), "read", normalizeChannel(channel));
        summaries.invalidateAfterCommit(companyId, userId);
    }

    @Transactional
    public void markAllRead(long companyId, int userId, String channel) {
        for (DbNotificationFeed.LockedRecipient row : feed.lockUnread(companyId, userId)) {
            long sequence = changes.nextSequence(companyId);
            feed.markRead(companyId, row.recipientId(), sequence);
            changes.insert(companyId, sequence, userId, row.recipientId(), "read", null);
            recipients.audit(companyId, row.recipientId(), userId, row.category(),
                    row.state(), "read", normalizeChannel(channel));
        }
        summaries.invalidateAfterCommit(companyId, userId);
    }

    @Transactional
    public void markClicked(long companyId, int userId, UUID uuid, String channel) {
        DbNotificationFeed.LockedRecipient row = feed.lock(companyId, userId, uuid);
        long sequence = changes.nextSequence(companyId);
        feed.markClicked(companyId, row.recipientId(), sequence);
        changes.insert(companyId, sequence, userId, row.recipientId(), "clicked", null);
        recipients.audit(companyId, row.recipientId(), userId, row.category(),
                row.state(), "clicked", normalizeChannel(channel));
        summaries.invalidateAfterCommit(companyId, userId);
    }

    @Transactional
    public void archive(long companyId, int userId, UUID uuid, String channel) {
        DbNotificationFeed.LockedRecipient row = feed.lock(companyId, userId, uuid);
        if ("archived".equals(row.state())) {
            return;
        }
        long sequence = changes.nextSequence(companyId);
        feed.archive(companyId, row.recipientId(), sequence);
        changes.insert(companyId, sequence, userId, row.recipientId(), "archived", null);
        recipients.audit(companyId, row.recipientId(), userId, row.category(),
                row.state(), "archived", normalizeChannel(channel));
        summaries.invalidateAfterCommit(companyId, userId);
    }

    private void assertVisible(long companyId, int userId, NotificationFeedItem item) {
        if (!audience.userHasCapability(companyId, userId, item.branchId(),
                item.requiredCapability())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                    "Notification not found");
        }
    }

    private long recipientIdForCursor(long companyId, int userId, UUID uuid) {
        return feed.locklessRecipientId(companyId, userId, uuid);
    }

    private static String normalizeChannel(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "mobile", "web" -> value.toLowerCase();
            default -> "web";
        };
    }
}
