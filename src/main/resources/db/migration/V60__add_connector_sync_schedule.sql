-- B6 (M12 #10): sync schedule configuration for integration connectors. Adds a configurable
-- automatic-sync interval (minutes; NULL = disabled) plus the last-sync outcome columns recorded
-- by ConnectorSyncJob / the "sync now" action. The connector framework itself is unchanged; only
-- the orchestration of recurring synchronisation is added.
ALTER TABLE integration_connectors
    ADD COLUMN sync_interval_minutes INTEGER,
    ADD COLUMN last_sync_at          TIMESTAMPTZ,
    ADD COLUMN last_sync_status      VARCHAR(20),
    ADD COLUMN last_sync_message     VARCHAR(1000);

ALTER TABLE integration_connectors
    ADD CONSTRAINT chk_connector_sync_interval_positive
        CHECK (sync_interval_minutes IS NULL OR sync_interval_minutes > 0);
