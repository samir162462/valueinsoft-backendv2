# SSE proxy and load-balancer configuration (NC-6.10)

Server-Sent Events break in ways that look like application bugs. Every failure below is a
proxy default, not a code fault, and each has bitten someone before.

Reference: `NOTIFICATION_CENTER_PLAN.md` §7.4.

---

## The four defaults that break SSE

| Default | Symptom | Fix |
|---|---|---|
| `proxy_buffering on` (nginx default) | Stream appears dead; events arrive in a burst minutes later, or only when the buffer fills | `proxy_buffering off` **and** the `X-Accel-Buffering: no` header the controller already sends |
| `proxy_read_timeout 60s` (nginx default) | Connection drops every 60s; clients reconnect in a loop and the reconnect rate looks like an outage | Raise to `3600s`. The 25s ping keeps it alive, but the timeout must exceed the ping interval by a wide margin |
| ALB / ELB idle timeout 60s | Same as above, one layer out | Raise to at least `300s` |
| HTTP/1.0 upstream | Chunked transfer disabled; nothing streams | `proxy_http_version 1.1` and clear the `Connection` header |

---

## nginx

```nginx
location /api/v1/notifications/stream {
    proxy_pass              http://valueinsoft_backend;

    # SSE needs HTTP/1.1 for chunked transfer.
    proxy_http_version      1.1;
    proxy_set_header        Connection '';

    # Do not buffer: the whole point is incremental delivery.
    proxy_buffering         off;
    proxy_cache             off;

    # Comfortably above the 25s keep-alive ping.
    proxy_read_timeout      3600s;
    proxy_send_timeout      3600s;

    proxy_set_header        Host              $host;
    proxy_set_header        X-Real-IP         $remote_addr;
    proxy_set_header        X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header        X-Forwarded-Proto $scheme;

    # EventSource sends this on auto-reconnect; it carries the change sequence and must
    # survive the hop or every reconnect silently replays from zero.
    proxy_set_header        Last-Event-ID     $http_last_event_id;
}
```

Everything else keeps the ordinary short timeouts. Applying `3600s` globally would mean a
hung upstream ties up a worker for an hour.

---

## AWS ALB

- Target group idle timeout → **300 seconds** minimum.
- Do **not** enable response buffering on any intermediate CloudFront distribution for this
  path; `Managed-CachingDisabled` plus origin request policy `AllViewer` so `Last-Event-ID`
  reaches the origin.
- Health checks stay on `/actuator/health`, never on the stream path — a health check that
  opens an SSE connection never completes and marks the target unhealthy.

---

## Spring / servlet container

`spring.mvc.async.request-timeout` must not undercut the emitter timeout. The emitter is
constructed with `valueinsoft.notification.sse.connection-timeout-ms` (default 3,600,000);
leave the MVC async timeout unset so the emitter's own value governs.

Tomcat's default `maxConnections` and thread pool are the real ceiling on concurrent
streams. The per-instance cap of 5,000 (`sse.max-connections-per-instance`) is deliberately
below any container default — over it the endpoint returns `503` with `Retry-After` and the
client polls, which is a graceful degradation rather than a thread-pool exhaustion.

Virtual threads are already enabled (`spring.threads.virtual.enabled=true`), so a blocked
emitter does not pin a platform thread.

---

## Verifying it works

```bash
# 1. Get a ticket (bearer-authenticated).
TICKET=$(curl -sS -X POST "$API/api/v1/notifications/stream/ticket" \
  -H "Authorization: Bearer $JWT" | jq -r .ticket)

# 2. Open the stream. Events should appear immediately, and a ':ping' comment
#    every ~25 seconds. If nothing arrives for minutes and then a block arrives
#    at once, buffering is still on somewhere.
curl -N -sS "$API/api/v1/notifications/stream?companyId=$COMPANY&ticket=$TICKET"

# 3. Replaying the same ticket must fail with 401 — GETDEL consumed it.
curl -sS -o /dev/null -w '%{http_code}\n' \
  "$API/api/v1/notifications/stream?companyId=$COMPANY&ticket=$TICKET"
```

`curl -N` disables curl's own buffering. Forgetting it produces exactly the symptom you are
testing for, which has wasted more than one afternoon.

---

## Operating signals

| Metric | Meaning |
|---|---|
| `notification.sse.connections` | Live emitters per instance. Approaching 5,000 means scale out. |
| `notification.sse.reset{reason}` | `replay_window_exceeded` rising means the 7-day change-log retention is too short for how long devices stay offline. |
| `notification.sse.replay` | Reconnect volume. A step change usually means a proxy timeout regression. |

A high reconnect rate with healthy pings almost always means `proxy_read_timeout`, not the
application.
