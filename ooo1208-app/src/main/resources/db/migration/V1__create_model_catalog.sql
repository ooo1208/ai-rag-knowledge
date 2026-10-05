CREATE TABLE provider_connection (
    id VARCHAR(100) PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL
        CHECK (provider_type IN ('OLLAMA', 'OPENAI_COMPATIBLE')),
    name VARCHAR(200) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    credential_ref VARCHAR(500),
    owner_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    owner_id VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_checked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_provider_connection_enabled
    ON provider_connection (enabled, status);

CREATE TABLE model_binding (
    id VARCHAR(100) PRIMARY KEY,
    connection_id VARCHAR(100) NOT NULL
        REFERENCES provider_connection (id) ON DELETE RESTRICT,
    upstream_model_id VARCHAR(300) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    capabilities TEXT NOT NULL DEFAULT '',
    context_window INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    last_synced_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_model_binding_connection_model
        UNIQUE (connection_id, upstream_model_id)
);

CREATE INDEX idx_model_binding_enabled
    ON model_binding (connection_id, enabled);

CREATE TABLE model_preset (
    id VARCHAR(100) PRIMARY KEY,
    model_binding_id VARCHAR(100) NOT NULL
        REFERENCES model_binding (id) ON DELETE RESTRICT,
    name VARCHAR(200) NOT NULL,
    temperature NUMERIC(4, 3) NOT NULL DEFAULT 0.7
        CHECK (temperature >= 0),
    max_tokens INTEGER NOT NULL DEFAULT 2048
        CHECK (max_tokens > 0),
    system_prompt TEXT NOT NULL DEFAULT '',
    rag_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    owner_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    owner_id VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_model_preset_enabled
    ON model_preset (enabled, model_binding_id);
