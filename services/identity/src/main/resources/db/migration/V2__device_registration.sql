CREATE TABLE device (
    tenant_id          uuid        NOT NULL,
    id                 uuid        NOT NULL,
    store_id           uuid        NOT NULL,
    label              text        NOT NULL,
    status             text        NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    secret_hash        text,
    paired_at          timestamptz,
    revoked_at         timestamptz,
    last_seen_at       timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, store_id) REFERENCES store (tenant_id, id)
);

CREATE INDEX device_store_idx ON device (tenant_id, store_id) WHERE status <> 'REVOKED';

CREATE TABLE device_pairing (
    id          uuid        PRIMARY KEY,
    code_hash   text        NOT NULL UNIQUE,
    tenant_id   uuid        NOT NULL,
    device_id   uuid        NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX device_pairing_expiry_idx ON device_pairing (expires_at)
    WHERE consumed_at IS NULL;

ALTER TABLE device ENABLE ROW LEVEL SECURITY;
ALTER TABLE device FORCE  ROW LEVEL SECURITY;

CREATE POLICY device_tenant_isolation ON device
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON device TO cloudpos_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON device_pairing TO cloudpos_app;
