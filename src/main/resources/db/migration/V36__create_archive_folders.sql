CREATE TABLE archive_folders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(500),
    parent_id    UUID REFERENCES archive_folders(id) ON DELETE CASCADE,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (name, parent_id)
);

CREATE INDEX idx_archive_folders_parent ON archive_folders(parent_id);
