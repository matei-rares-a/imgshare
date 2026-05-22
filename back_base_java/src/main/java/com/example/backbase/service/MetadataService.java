package com.example.backbase.service;

import com.example.backbase.model.CursorPage;
import com.example.backbase.model.ImageMetadata;
import com.example.backbase.model.ImageMetadataEntity;
import com.example.backbase.repository.ImageMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * All DB operations for image metadata.
 * Active only when storage.backend=s3 (requires JPA / PostgreSQL).
 */
@Service
@ConditionalOnProperty(name = "storage.backend", havingValue = "s3", matchIfMissing = true)
public class MetadataService {

    private final ImageMetadataRepository repo;

    @Value("${gallery.page-load:20}")
    private int defaultPageSize;

    public MetadataService(ImageMetadataRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public ImageMetadataEntity save(String objectKey, String contentHash, long size,
                                    String mimeType, String ownerId) {
        ImageMetadataEntity e = new ImageMetadataEntity();
        e.setObjectKey(objectKey);
        e.setContentHash(contentHash);
        e.setSize(size);
        e.setMimeType(mimeType);
        e.setOwnerId(ownerId);
        return repo.save(e);
    }

    /** Deduplication: returns existing ACTIVE record with same SHA-256, if any. */
    public Optional<ImageMetadataEntity> findDuplicate(String contentHash) {
        return repo.findByContentHashAndStatus(contentHash, ImageMetadataEntity.ImageStatus.ACTIVE);
    }

    public Optional<ImageMetadataEntity> findByObjectKey(String objectKey) {
        return repo.findByObjectKeyAndStatus(objectKey, ImageMetadataEntity.ImageStatus.ACTIVE);
    }

    /** Soft delete: sets status=DELETED + deleted_at. Optimistic lock via @Version. */
    @Transactional
    public void softDelete(String objectKey) {
        repo.findByObjectKeyAndStatus(objectKey, ImageMetadataEntity.ImageStatus.ACTIVE)
                .ifPresent(e -> {
                    e.setStatus(ImageMetadataEntity.ImageStatus.DELETED);
                    e.setDeletedAt(Instant.now());
                    repo.save(e);
                });
    }

    @Transactional
    public int softDeleteAll() {
        List<ImageMetadataEntity> active = repo.findAllByStatus(ImageMetadataEntity.ImageStatus.ACTIVE);
        Instant now = Instant.now();
        active.forEach(e -> {
            e.setStatus(ImageMetadataEntity.ImageStatus.DELETED);
            e.setDeletedAt(now);
        });
        repo.saveAll(active);
        return active.size();
    }

    /**
     * Cursor-based page. cursor=null → first page.
     * urlResolver maps each entity to a download URL (presigned or CDN).
     * Fetches size+1 to detect hasMore without a COUNT query.
     */
    public CursorPage<ImageMetadata> listPage(String cursor, int size,
                                               Function<ImageMetadataEntity, String> urlResolver) {
        int limit = size + 1;
        List<ImageMetadataEntity> rows;

        if (cursor == null || cursor.isBlank()) {
            rows = repo.findFirstPage(PageRequest.of(0, limit));
        } else {
            CursorParts p = decodeCursor(cursor);
            rows = repo.findPageAfterCursor(p.ts(), p.id(), PageRequest.of(0, limit));
        }

        boolean hasMore = rows.size() > size;
        List<ImageMetadataEntity> page = hasMore ? rows.subList(0, size) : rows;

        List<ImageMetadata> items = page.stream()
                .map(e -> new ImageMetadata(
                        e.getObjectKey(),
                        urlResolver.apply(e),
                        e.getCreatedAt().toEpochMilli(),
                        String.format("%.2f KB", e.getSize() / 1024.0)))
                .toList();

        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    public List<ImageMetadataEntity> findPendingHardDelete(Instant cutoff) {
        return repo.findByStatusAndDeletedAtBefore(ImageMetadataEntity.ImageStatus.DELETED, cutoff);
    }

    @Transactional
    public void hardDeleteBatch(List<Long> ids) {
        if (!ids.isEmpty()) repo.deleteAllByIdIn(ids);
    }

    // ---- cursor encode / decode ----

    String encodeCursor(ImageMetadataEntity last) {
        String raw = last.getCreatedAt().toEpochMilli() + ":" + last.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CursorParts decodeCursor(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split(":", 2);
        return new CursorParts(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                Long.parseLong(parts[1]));
    }

    private record CursorParts(Instant ts, Long id) {}
}
