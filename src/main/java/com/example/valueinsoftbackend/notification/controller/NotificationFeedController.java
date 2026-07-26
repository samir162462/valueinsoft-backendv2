package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationFeedEvent;
import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.model.NotificationFeedPage;
import com.example.valueinsoftbackend.notification.model.NotificationSummary;
import com.example.valueinsoftbackend.notification.service.NotificationFeedService;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import com.example.valueinsoftbackend.notification.service.NotificationSummaryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationFeedController {
    private static final String FEED_CAPABILITY = "notification.feed.read.self";

    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationFeedService feed;
    private final NotificationSummaryService summaries;

    public NotificationFeedController(NotificationProperties properties,
                                      ObjectProvider<NotificationControlGate> gateProvider,
                                      NotificationRequestContextResolver contexts,
                                      AuthorizationService authorization,
                                      NotificationFeedService feed,
                                      NotificationSummaryService summaries) {
        this.properties = properties;
        this.gateProvider = gateProvider;
        this.contexts = contexts;
        this.authorization = authorization;
        this.feed = feed;
        this.summaries = summaries;
    }

    @GetMapping("/feed")
    public NotificationFeedPage page(
            Principal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "20") int size) {
        var context = requireContext(principal);
        return feed.page(context.companyId(), context.userId(), cursor, category,
                branchId, state, size);
    }

    @GetMapping("/feed/{recipientUuid}")
    public NotificationFeedItem detail(Principal principal,
                                       @PathVariable UUID recipientUuid) {
        var context = requireContext(principal);
        return feed.detail(context.companyId(), context.userId(), recipientUuid);
    }

    @GetMapping("/feed/{recipientUuid}/events")
    public List<NotificationFeedEvent> lineage(Principal principal,
                                               @PathVariable UUID recipientUuid) {
        var context = requireContext(principal);
        return feed.lineage(context.companyId(), context.userId(), recipientUuid);
    }

    @GetMapping("/summary")
    public NotificationSummary summary(Principal principal) {
        var context = requireContext(principal);
        return summaries.summary(context.companyId(), context.userId());
    }

    @PostMapping("/seen")
    public void seen(Principal principal,
                     @RequestBody SeenRequest request,
                     @RequestHeader(value = "X-Client-Channel",
                             defaultValue = "web") String channel) {
        var context = requireContext(principal);
        feed.markSeen(context.companyId(), context.userId(),
                request.recipientUuids(), channel);
    }

    @PostMapping("/feed/{recipientUuid}/read")
    public void read(Principal principal, @PathVariable UUID recipientUuid,
                     @RequestHeader(value = "X-Client-Channel",
                             defaultValue = "web") String channel) {
        var context = requireContext(principal);
        feed.markRead(context.companyId(), context.userId(), recipientUuid, channel);
    }

    @PostMapping("/read-all")
    public void readAll(Principal principal,
                        @RequestHeader(value = "X-Client-Channel",
                                defaultValue = "web") String channel) {
        var context = requireContext(principal);
        feed.markAllRead(context.companyId(), context.userId(), channel);
    }

    @PostMapping("/feed/{recipientUuid}/click")
    public void click(Principal principal, @PathVariable UUID recipientUuid,
                      @RequestHeader(value = "X-Client-Channel",
                              defaultValue = "web") String channel) {
        var context = requireContext(principal);
        feed.markClicked(context.companyId(), context.userId(), recipientUuid, channel);
    }

    @PostMapping("/feed/{recipientUuid}/archive")
    public void archive(Principal principal, @PathVariable UUID recipientUuid,
                        @RequestHeader(value = "X-Client-Channel",
                                defaultValue = "web") String channel) {
        var context = requireContext(principal);
        feed.archive(context.companyId(), context.userId(), recipientUuid, channel);
    }

    private NotificationRequestContextResolver.Context requireContext(Principal principal) {
        NotificationControlGate gate = gateProvider.getIfAvailable();
        if (!properties.isEnabled()
                || (gate != null && !gate.isEnabled(NotificationComponent.FEED_READ))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_FEED_DISABLED", "Notification feed is temporarily disabled");
        }
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(), FEED_CAPABILITY);
        return context;
    }

    public record SeenRequest(List<UUID> recipientUuids) {}
}
