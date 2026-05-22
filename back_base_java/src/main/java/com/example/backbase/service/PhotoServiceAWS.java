package com.example.backbase.service;

import com.example.backbase.model.CursorPage;
import com.example.backbase.model.ImageMetadata;
import com.example.backbase.model.ImageMetadataEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.backend", havingValue = "s3", matchIfMissing = true)
public class PhotoServiceAWS implements StorageService {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region:eu-central-1}")
    private String regionName;

    @Value("${aws.s3.presigned-url.upload-ttl-minutes:15}")
    private int uploadTtlMinutes;

    @Value("${aws.s3.presigned-url.download-ttl-minutes:60}")
    private int downloadTtlMinutes;

    @Value("${gallery.initial-load:30}")
    private int initialLoad;

    @Value("${gallery.page-load:20}")
    private int pageLoad;

    private S3Client s3;
    private S3Presigner presigner;

    private final MetadataService metadataService;
    private final CacheService cacheService;

    public PhotoServiceAWS(MetadataService metadataService, CacheService cacheService) {
        this.metadataService = metadataService;
        this.cacheService = cacheService;
    }

    @PostConstruct
    public void init() {
        Region region = Region.of(regionName);
        this.s3 = S3Client.builder().region(region).build();
        this.presigner = S3Presigner.builder().region(region).build();
    }

    // -------------------------------------------------------------------------
    // Upload — with SHA-256 deduplication
    // -------------------------------------------------------------------------

    @Override
    public String store(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String contentHash = sha256Hex(bytes);

        // Dedup: if same content already stored, return existing key
        Optional<ImageMetadataEntity> existing = metadataService.findDuplicate(contentHash);
        if (existing.isPresent()) {
            return existing.get().getObjectKey();
        }

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) ext = "bin";
        String objectKey = System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + ext;

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(bytes));

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        metadataService.save(objectKey, contentHash, file.getSize(), mimeType, null);
        cacheService.evictAllLists();
        return objectKey;
    }

    // -------------------------------------------------------------------------
    // Confirm-upload — for presigned URL flow: register metadata post-upload
    // Client provides contentHash; server dedupes and saves (or returns existing).
    // If duplicate found: schedules S3 cleanup of the just-uploaded object key.
    // -------------------------------------------------------------------------

    public ConfirmResult confirmUpload(String objectKey, String contentHash,
                                       long size, String mimeType) {
        Optional<ImageMetadataEntity> existing = metadataService.findDuplicate(contentHash);
        if (existing.isPresent()) {
            // Clean up the redundant S3 object the client just uploaded
            deleteS3Object(objectKey);
            return new ConfirmResult(existing.get().getObjectKey(), true);
        }
        metadataService.save(objectKey, contentHash, size, mimeType, null);
        cacheService.evictAllLists();
        return new ConfirmResult(objectKey, false);
    }

    public record ConfirmResult(String objectKey, boolean deduplicated) {}

    // -------------------------------------------------------------------------
    // List — cache-aside with request coalescing
    // -------------------------------------------------------------------------

    @Override
    public List<ImageMetadata> listImages() throws IOException {
        String cacheKey = "all";
        Optional<String> cached = cacheService.getList(cacheKey);
        if (cached.isPresent()) {
            return cacheService.deserialize(cached.get(), new TypeReference<List<ImageMetadata>>() {})
                    .orElse(List.of());
        }

        boolean lock = cacheService.tryAcquireLock(cacheKey);
        if (!lock) {
            sleepQuietly(120);
            cached = cacheService.getList(cacheKey);
            if (cached.isPresent()) {
                return cacheService.deserialize(cached.get(), new TypeReference<List<ImageMetadata>>() {})
                        .orElse(List.of());
            }
        }
        try {
            CursorPage<ImageMetadata> page = metadataService.listPage(
                    null, 1000, e -> generatePresignedDownloadUrl(e.getObjectKey()));
            cacheService.putList(cacheKey, page.items());
            return page.items();
        } finally {
            if (lock) cacheService.releaseLock(cacheKey);
        }
    }

    @Override
    public CursorPage<ImageMetadata> listImagesCursor(String cursor, int size) throws IOException {
        String cacheKey = "cursor:" + (cursor != null ? cursor : "first") + ":" + size;
        Optional<String> cached = cacheService.getList(cacheKey);
        if (cached.isPresent()) {
            CursorPage<ImageMetadata> hit = cacheService.deserialize(
                    cached.get(), new TypeReference<CursorPage<ImageMetadata>>() {}).orElse(null);
            if (hit != null) return hit;
        }

        boolean lock = cacheService.tryAcquireLock(cacheKey);
        if (!lock) {
            sleepQuietly(120);
            cached = cacheService.getList(cacheKey);
            if (cached.isPresent()) {
                CursorPage<ImageMetadata> hit = cacheService.deserialize(
                        cached.get(), new TypeReference<CursorPage<ImageMetadata>>() {}).orElse(null);
                if (hit != null) return hit;
            }
        }
        try {
            CursorPage<ImageMetadata> page = metadataService.listPage(
                    cursor, size, e -> generatePresignedDownloadUrl(e.getObjectKey()));
            cacheService.putList(cacheKey, page);
            return page;
        } finally {
            if (lock) cacheService.releaseLock(cacheKey);
        }
    }

    /** Offset pagination — backward compat. Internally uses cursor page. */
    @Override
    public List<ImageMetadata> listImagesPaginated(int page) throws IOException {
        int size = page == 0 ? initialLoad : pageLoad;
        int skip = page == 0 ? 0 : initialLoad + (page - 1) * pageLoad;
        CursorPage<ImageMetadata> all = listImagesCursor(null, Math.max(1000, skip + size));
        List<ImageMetadata> items = all.items();
        if (skip >= items.size()) return List.of();
        return items.subList(skip, Math.min(skip + size, items.size()));
    }

    // -------------------------------------------------------------------------
    // Read (proxy — use presigned URL for high-traffic)
    // -------------------------------------------------------------------------

    @Override
    public byte[] loadImage(String filename) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName).key(filename).build();
        try (ResponseInputStream<GetObjectResponse> obj = s3.getObject(request)) {
            return obj.readAllBytes();
        }
    }

    // -------------------------------------------------------------------------
    // Delete — soft delete via MetadataService; S3 cleanup via HardDeleteJob
    // -------------------------------------------------------------------------

    @Override
    public void deleteImage(String filename) throws IOException {
        metadataService.softDelete(filename);
        cacheService.evictMeta(filename);
        cacheService.evictAllLists();
    }

    @Override
    public int deleteAll() throws IOException {
        int count = metadataService.softDeleteAll();
        cacheService.evictAllLists();
        return count;
    }

    // -------------------------------------------------------------------------
    // Presigned URLs
    // -------------------------------------------------------------------------

    @Override
    public String generatePresignedUploadUrl(String objectKey, String contentType) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
                .signatureDuration(Duration.ofMinutes(uploadTtlMinutes))
                .putObjectRequest(p -> p
                        .bucket(bucketName).key(objectKey).contentType(contentType)));
        return presigned.url().toString();
    }

    @Override
    public String generatePresignedDownloadUrl(String objectKey) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                .signatureDuration(Duration.ofMinutes(downloadTtlMinutes))
                .getObjectRequest(g -> g
                        .bucket(bucketName).key(objectKey)));
        return presigned.url().toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private void deleteS3Object(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName).key(objectKey).build());
        } catch (Exception e) {
            // Non-fatal: object will be cleaned up by HardDeleteJob eventually
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
