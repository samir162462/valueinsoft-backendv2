CREATE TABLE IF NOT EXISTS public.whatsapp_settings (
    settings_id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    phone_number_id VARCHAR(64),
    business_account_id VARCHAR(64),
    access_token_encrypted TEXT,
    default_country_code VARCHAR(5) DEFAULT '20',
    graph_api_version VARCHAR(10) NOT NULL DEFAULT 'v21.0',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_whatsapp_settings_company
    ON public.whatsapp_settings (company_id);

DROP TRIGGER IF EXISTS trg_whatsapp_settings_set_updated_at ON public.whatsapp_settings;
CREATE TRIGGER trg_whatsapp_settings_set_updated_at
BEFORE UPDATE ON public.whatsapp_settings
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.whatsapp_message_log (
    log_id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    template_name VARCHAR(128),
    language_code VARCHAR(10),
    message_body TEXT,
    provider_message_id VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(64),
    error_message TEXT,
    request_payload TEXT,
    response_payload TEXT,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_message_log_company
    ON public.whatsapp_message_log (company_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_whatsapp_message_log_set_updated_at ON public.whatsapp_message_log;
CREATE TRIGGER trg_whatsapp_message_log_set_updated_at
BEFORE UPDATE ON public.whatsapp_message_log
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();
