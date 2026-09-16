CREATE TABLE tenant (
    id         uuid PRIMARY KEY,
    name       text        NOT NULL,
    status     text        NOT NULL DEFAULT 'ACTIVE'
                           CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE store (
    tenant_id           uuid        NOT NULL REFERENCES tenant (id),
    id                  uuid        NOT NULL,
    name                text        NOT NULL,
    timezone            text        NOT NULL DEFAULT 'Asia/Seoul',
    business_day_cutoff time        NOT NULL DEFAULT '05:00',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE staff (
    tenant_id         uuid        NOT NULL,
    id                uuid        NOT NULL,
    store_id          uuid        NOT NULL,
    employee_code     text        NOT NULL,
    display_name      text        NOT NULL,
    role              text        NOT NULL
                                  CHECK (role IN ('OWNER', 'MANAGER', 'WAITER', 'KITCHEN')),
    employment_status text        NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (employment_status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),
    pin_hash          text        NOT NULL,
    pin_failed_count  int         NOT NULL DEFAULT 0,
    pin_locked_until  timestamptz,
    joined_on         date,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    version           bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, store_id) REFERENCES store (tenant_id, id)
);

CREATE UNIQUE INDEX staff_employee_code_uq
    ON staff (tenant_id, store_id, employee_code)
    WHERE deleted_at IS NULL;

ALTER TABLE store ENABLE ROW LEVEL SECURITY;
ALTER TABLE store FORCE  ROW LEVEL SECURITY;
ALTER TABLE staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff FORCE  ROW LEVEL SECURITY;

CREATE POLICY store_tenant_isolation ON store
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

CREATE POLICY staff_tenant_isolation ON staff
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudpos_app;
