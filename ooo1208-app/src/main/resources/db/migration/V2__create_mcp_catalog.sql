CREATE TABLE mcp_server_connection (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    transport_type VARCHAR(32) NOT NULL
        CHECK (transport_type IN ('STREAMABLE_HTTP', 'SSE', 'STDIO')),
    endpoint_url VARCHAR(1000),
    credential_ref VARCHAR(500),
    owner_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    owner_id VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_checked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mcp_server_transport_endpoint
        CHECK (
            (transport_type = 'STDIO' AND endpoint_url IS NULL)
            OR (transport_type <> 'STDIO' AND endpoint_url IS NOT NULL)
        )
);

CREATE INDEX idx_mcp_server_connection_enabled
    ON mcp_server_connection (enabled, status);

CREATE TABLE mcp_tool (
    id VARCHAR(100) PRIMARY KEY,
    server_connection_id VARCHAR(100) NOT NULL
        REFERENCES mcp_server_connection (id) ON DELETE RESTRICT,
    name VARCHAR(300) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    max_calls INTEGER NOT NULL DEFAULT 10
        CHECK (max_calls > 0),
    last_synced_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mcp_tool_server_name
        UNIQUE (server_connection_id, name)
);

CREATE INDEX idx_mcp_tool_enabled
    ON mcp_tool (server_connection_id, enabled);

CREATE TABLE model_preset_tool (
    model_preset_id VARCHAR(100) NOT NULL
        REFERENCES model_preset (id) ON DELETE CASCADE,
    mcp_tool_id VARCHAR(100) NOT NULL
        REFERENCES mcp_tool (id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    max_calls INTEGER NOT NULL DEFAULT 10
        CHECK (max_calls > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (model_preset_id, mcp_tool_id)
);

CREATE INDEX idx_model_preset_tool_enabled
    ON model_preset_tool (model_preset_id, enabled);
