CREATE TABLE device_directory (
    device_id  uuid        PRIMARY KEY,
    tenant_id  uuid        NOT NULL,
    store_id   uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    tenant_id      uuid        NOT NULL,
    id             uuid        NOT NULL,
    family_id      uuid        NOT NULL,
    staff_id       uuid        NOT NULL,
    device_id      uuid        NOT NULL,
    token_hash     text        NOT NULL,
    issued_at      timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    consumed_at    timestamptz,
    revoked_at     timestamptz,
    revoked_reason text,
    PRIMARY KEY (tenant_id, id)
);

CREATE UNIQUE INDEX refresh_token_hash_uq ON refresh_token (token_hash);
CREATE INDEX refresh_token_family_idx ON refresh_token (tenant_id, family_id);
CREATE INDEX refresh_token_expiry_idx ON refresh_token (expires_at)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token FORCE  ROW LEVEL SECURITY;

CREATE POLICY refresh_token_tenant_isolation ON refresh_token
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON device_directory TO cloudpos_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_token TO cloudpos_app;
