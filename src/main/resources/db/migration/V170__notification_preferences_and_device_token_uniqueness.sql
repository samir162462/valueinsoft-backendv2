-- Preferences remain sparse: a missing row means catalog defaults.

CREATE TABLE IF NOT EXISTS public.notification_preference (
    user_id        INTEGER     NOT NULL,
    company_id     INTEGER     NOT NULL,
    type_key       TEXT        NOT NULL,
    channel_in_app BOOLEAN     NOT NULL DEFAULT TRUE,
    channel_push   BOOLEAN     NOT NULL DEFAULT TRUE,
    muted_until    TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, company_id, type_key),
    CONSTRAINT fk_np_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_np_type FOREIGN KEY (type_key)
        REFERENCES public.notification_type_catalog (type_key)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_np_company FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.notification_preference_global (
    user_id           INTEGER     NOT NULL,
    company_id        INTEGER     NOT NULL,
    quiet_hours_start TIME,
    quiet_hours_end   TIME,
    quiet_hours_tz    TEXT        NOT NULL DEFAULT 'UTC',
    dnd_until         TIMESTAMPTZ,
    min_priority      TEXT        NOT NULL DEFAULT 'low',
    digest_mode       TEXT        NOT NULL DEFAULT 'off',
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, company_id),
    CONSTRAINT fk_npg_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_npg_company FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id) ON DELETE CASCADE,
    CONSTRAINT chk_npg_min_priority CHECK (min_priority IN ('critical','high','normal','low')),
    CONSTRAINT chk_npg_digest CHECK (digest_mode IN ('off','hourly','daily')),
    CONSTRAINT chk_npg_quiet_pair CHECK (
        (quiet_hours_start IS NULL) = (quiet_hours_end IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nd_token_hash_active
    ON public.notification_device (token_hash, provider, app_bundle_id, apns_environment)
    WHERE status = 'active';

