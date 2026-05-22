package com.example.backbase.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for image metadata stored in PostgreSQL.
 * Binary objects live in S3; this table holds only keys + attributes.
 *
 * Optimistic locking via @Version prevents concurrent admin operations
 * from clobbering each other (e.g. two delete-all requests racing).
 */
@Entity
@Table(
    name = "image_metadata",
    indexes = {
        @Index(name = "idx_img_created_at",    columnList = "created_at DESC"),
        @Index(name = "idx_img_owner_created", columnList = "owner_id, created_at DESC"),
        @Index(name = "idx_img_status",        columnList = "status"),
        @Index(name = "idx_img_content_hash",  columnList = "content_hash")
    }
)
public class ImageMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** S3 object key (unique). */
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    /** SHA-256 hex of file bytes — used for deduplication. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Long size;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    /** Optional: future auth integration. Null = anonymous upload. */
    @Column(name = "owner_id", length = 255)
    private String ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set on soft delete. Null = active record. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private ImageStatus status;

    /** Optimistic locking — Spring Data increments on each save. */
    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null)    status    = ImageStatus.ACTIVE;
    }

    public enum ImageStatus { ACTIVE, DELETED, PROCESSING }

    // ---- Getters / Setters ----

    public Long getId()                   { return id; }
    public String getObjectKey()          { return objectKey; }
    public void setObjectKey(String v)    { objectKey = v; }
    public String getContentHash()        { return contentHash; }
    public void setContentHash(String v)  { contentHash = v; }
    public Long getSize()                 { return size; }
    public void setSize(Long v)           { size = v; }
    public String getMimeType()           { return mimeType; }
    public void setMimeType(String v)     { mimeType = v; }
    public String getOwnerId()            { return ownerId; }
    public void setOwnerId(String v)      { ownerId = v; }
    public Instant getCreatedAt()         { return createdAt; }
    public Instant getDeletedAt()         { return deletedAt; }
    public void setDeletedAt(Instant v)   { deletedAt = v; }
    public ImageStatus getStatus()        { return status; }
    public void setStatus(ImageStatus v)  { status = v; }
    public Long getVersion()              { return version; }
}
