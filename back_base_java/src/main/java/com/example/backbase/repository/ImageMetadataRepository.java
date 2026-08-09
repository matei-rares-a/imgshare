package com.example.backbase.repository;

import com.example.backbase.model.ImageMetadataEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ImageMetadataRepository extends JpaRepository<ImageMetadataEntity, Long> {

    /** First page: most recent ACTIVE records. */
    @Query("SELECT m FROM ImageMetadataEntity m WHERE m.status = 'ACTIVE' ORDER BY m.createdAt DESC, m.id DESC")
    List<ImageMetadataEntity> findFirstPage(Pageable pageable);

    /**
     * Keyset (cursor) pagination. Fetches records strictly before the cursor position.
     * Avoids offset drift on concurrent inserts.
     */
    @Query("""
            SELECT m FROM ImageMetadataEntity m
            WHERE m.status = 'ACTIVE'
              AND (m.createdAt < :cursorTs OR (m.createdAt = :cursorTs AND m.id < :cursorId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<ImageMetadataEntity> findPageAfterCursor(
            @Param("cursorTs") Instant cursorTs,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    Optional<ImageMetadataEntity> findByObjectKeyAndStatus(
            String objectKey, ImageMetadataEntity.ImageStatus status);

    /** Used for deduplication lookup by content hash. */
    Optional<ImageMetadataEntity> findByContentHashAndStatus(
            String contentHash, ImageMetadataEntity.ImageStatus status);

    List<ImageMetadataEntity> findAllByStatus(ImageMetadataEntity.ImageStatus status);

    /** Used by HardDeleteJob to find soft-deleted records past the retention window. */
    List<ImageMetadataEntity> findByStatusAndDeletedAtBefore(
            ImageMetadataEntity.ImageStatus status, Instant cutoff);

    @Modifying
    @Query("DELETE FROM ImageMetadataEntity m WHERE m.id IN :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);
}
