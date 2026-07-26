package com.example.valueinsoftbackend.notification.stream;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.service.NotificationRateLimiter;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import com.example.valueinsoftbackend.notification.service.NotificationSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The SSE endpoint (NC-6.2, NC-6.3).
 *
 * <p>{@code POST /stream/ticket} is bearer-authenticated like every other endpoint.
 * {@code GET /stream} is not — the browser's {@code EventSource} cannot send headers, so
 * the ticket in the query string is the credential and this controller performs the
 * authentication itself. That is why the URL has to be permitted in
 * {@code SecurityConfiguration}: it is authenticated, just not by the filter chain.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationStreamController {

    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final SseTicketService tickets;
    private final SseConnectionRegistry registry;
    private final FeedChangeReplayService replay;
    private final NotificationSummaryService summaries;
    private final NotificationRateLimiter rateLimiter;

    /**
     * One daemon thread drives every keep-alive on this instance. A per-connection timer
     * would mean 5,000 threads at the connection cap.
     */
    private final ScheduledExecutorService pings =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "notif-sse-ping");
                thread.setDaemon(true);
                return thread;
            });

    public NotificationStreamController(NotificationProperties properties,
                                        ObjectProvider<NotificationControlGate> gateProvider,
                                        NotificationRequestContextResolver contexts,
                                        AuthorizationService authorization,
                                        SseTicketService tickets,
                                        SseConnectionRegistry registry,
                                        FeedChangeReplayService replay,
                                        NotificationSummaryService summaries,
                                        NotificationRateLimiter rateLimiter) {
        this.properties = properties;
        this.gateProvider = gateProvider;
        this.contexts = contexts;
        this.authorization = authorization;
        this.tickets = tickets;
        this.registry = registry;
        this.replay = replay;
        this.summaries = summaries;
        this.rateLimiter = rateLimiter;
    }

    // ── Ticket issuance (bearer-authenticated) ─────────────────────────────

    @PostMapping("/stream/ticket")
    public ResponseEntity<SseTicketService.Ticket> issueTicket(
            Principal principal,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        requireStreamEnabled();

        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(),
                "notification.feed.read.self");

        if (!rateLimiter.tryAcquire(context.companyId(),
                NotificationRateLimiter.SSE_TICKET, String.valueOf(context.userId()))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "NOTIFICATION_TICKET_RATE_LIMITED",
                    "Too many stream connections; try again shortly");
        }

        String session = sessionId == null || sessionId.isBlank()
                ? principalName : sessionId;
        return ResponseEntity.status(HttpStatus.CREATED).body(
                tickets.issue(new SseTicketService.TicketClaims(
                        context.companyId(), context.userId(), principalName, session)));
    }

    // ── The stream (ticket-authenticated) ──────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestParam String ticket,
            @RequestParam long companyId,
            @RequestParam(required = false, defaultValue = "0") long lastEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {

        requireStreamEnabled();

        Optional<SseTicketService.TicketClaims> claims = tickets.redeem(companyId, ticket);
        if (claims.isEmpty()) {
            // Also the path a replayed ticket takes: GETDEL already consumed it.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SseTicketService.TicketClaims claim = claims.get();

        // A ticket proves a valid JWT existed 30 seconds ago; it does not prove the user
        // still holds the capability for this company. Re-check before opening the stream.
        try {
            authorization.assertAuthenticatedCapability(claim.username(),
                    Math.toIntExact(claim.companyId()), null, "notification.feed.read.self");
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // EventSource sends Last-Event-ID as a header on auto-reconnect and cannot set a
        // query parameter; the first connection can only use the parameter. Accept both,
        // preferring the header because it reflects what the browser actually processed.
        long since = parseLastEventId(lastEventIdHeader, lastEventId);

        SseEmitter emitter = new SseEmitter(properties.getSse().getConnectionTimeoutMs());
        SseConnectionRegistry.Connection connection = new SseConnectionRegistry.Connection(
                emitter, claim.companyId(), claim.userId(), claim.sessionId());

        if (!registry.register(connection)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "30")
                    .build();
        }

        ScheduledFuture<?> ping = pings.scheduleAtFixedRate(
                () -> registry.ping(connection),
                properties.getSse().getPingIntervalSeconds(),
                properties.getSse().getPingIntervalSeconds(),
                TimeUnit.SECONDS);

        Runnable cleanup = () -> {
            ping.cancel(false);
            registry.unregister(connection);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        try {
            catchUp(connection, since);
        } catch (RuntimeException ex) {
            log.warn("SSE catch-up failed for company {} user {}: {}",
                    claim.companyId(), claim.userId(), ex.toString());
            registry.complete(connection);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok()
                // Nginx buffers proxied responses by default, which holds every event until
                // the buffer fills — the stream appears dead. This header disables it.
                .header("X-Accel-Buffering", "no")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .body(emitter);
    }

    private void catchUp(SseConnectionRegistry.Connection connection, long since) {
        FeedChangeReplayService.Outcome outcome =
                replay.catchUp(connection.companyId(), connection.userId(), since);

        if (outcome instanceof FeedChangeReplayService.Outcome.Reset reset) {
            var summary = summaries.summary(connection.companyId(), connection.userId());
            registry.send(connection, NotificationStreamEvent.RESET, summary.changeSequence(),
                    new NotificationStreamEvent.ResetPayload(reset.reason(), summary.changeSequence()));
            return;
        }

        for (NotificationFeedItem item :
                ((FeedChangeReplayService.Outcome.Replay) outcome).items()) {
            registry.send(connection, NotificationStreamEvent.NOTIFICATION,
                    item.changeSequence(), item);
        }
        registry.send(connection, NotificationStreamEvent.SUMMARY, null,
                summaries.summary(connection.companyId(), connection.userId()));
    }

    private static long parseLastEventId(String header, long parameter) {
        if (header != null && !header.isBlank()) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException ignored) {
                // A malformed header means start fresh rather than fail the connection.
                return 0L;
            }
        }
        return Math.max(parameter, 0L);
    }

    private void requireStreamEnabled() {
        NotificationControlGate gate = gateProvider.getIfAvailable();
        if (!properties.isEnabled()
                || (gate != null && !gate.isEnabled(NotificationComponent.SSE))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_STREAM_DISABLED",
                    "Notification stream is temporarily disabled");
        }
    }
}
