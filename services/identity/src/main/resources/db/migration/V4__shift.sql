CREATE TABLE shift (
    tenant_id            uuid        NOT NULL,
    id                   uuid        NOT NULL,
    store_id             uuid        NOT NULL,
    staff_id             uuid        NOT NULL,
    device_id            uuid        NOT NULL,
    business_date        date        NOT NULL,
    opened_at            timestamptz NOT NULL DEFAULT now(),
    closed_at            timestamptz,
    opening_float_amount bigint      NOT NULL DEFAULT 0,
    currency             varchar(3)  NOT NULL DEFAULT 'KRW',
    status               text        NOT NULL DEFAULT 'OPEN'
                                     CHECK (status IN ('OPEN', 'CLOSED')),
    version              bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, store_id) REFERENCES store (tenant_id, id)
);

CREATE UNIQUE INDEX shift_one_open_per_staff
    ON shift (tenant_id, store_id, staff_id)
    WHERE status = 'OPEN';

CREATE INDEX shift_business_date_idx ON shift (tenant_id, store_id, business_date);

ALTER TABLE shift ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift FORCE  ROW LEVEL SECURITY;

CREATE POLICY shift_tenant_isolation ON shift
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON shift TO cloudpos_app;
