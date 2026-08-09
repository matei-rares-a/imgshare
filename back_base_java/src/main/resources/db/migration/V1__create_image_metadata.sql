-- V1: image_metadata table
-- Stores object keys + attributes; binary objects live in S3.

CREATE TABLE IF NOT EXISTS image_metadata (
    id           BIGSERIAL       PRIMARY KEY,
    object_key   VARCHAR(512)    NOT NULL UNIQUE,
    content_hash VARCHAR(64)     NOT NULL,
    size         BIGINT          NOT NULL,
    mime_type    VARCHAR(128)    NOT NULL,
    owner_id     VARCHAR(255),
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    status       VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    version      BIGINT          NOT NULL DEFAULT 0
);

-- High-traffic read indexes
CREATE INDEX idx_img_created_at    ON image_metadata (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_img_owner_created ON image_metadata (owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_img_status        ON image_metadata (status) WHERE deleted_at IS NULL;
-- Deduplication lookup
CREATE INDEX idx_img_content_hash  ON image_metadata (content_hash) WHERE deleted_at IS NULL;
