package com.example.backbase.service;

import com.example.backbase.model.ImageMetadataEntity;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.util.List;

/**
 * Async hard-delete job.
 *
 * Soft-deleted records accumulate with status=DELETED + deleted_at set.
 * After retention window (default 30 min) this job:
 *   1. Finds qualifying records in DB.
 *   2. Deletes each object from S3.
 *   3. Hard-deletes the DB row.
 *
 * Runs every hard-delete.interval-ms (default 5 min).
 * Processes each object individually so partial failures don't block progress.
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "s3", matchIfMissing = true)
public class HardDeleteJob {

    private static final Logger log = LoggerFactory.getLogger(HardDeleteJob.class);

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region:eu-central-1}")
    private String regionName;

    @Value("${hard-delete.retention-minutes:30}")
    private int retentionMinutes;

    private S3Client s3;

    private final MetadataService metadataService;

    public HardDeleteJob(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostConstruct
    void init() {
        this.s3 = S3Client.builder().region(Region.of(regionName)).build();
    }

    @Scheduled(fixedDelayString = "${hard-delete.interval-ms:300000}")
    public void run() {
        Instant cutoff = Instant.now().minusSeconds((long) retentionMinutes * 60);
        List<ImageMetadataEntity> pending = metadataService.findPendingHardDelete(cutoff);

        if (pending.isEmpty()) return;

        log.info("HardDeleteJob: {} records queued for hard delete", pending.size());

        List<Long> deleted = pending.stream()
                .filter(e -> deleteFromS3(e.getObjectKey()))
                .map(ImageMetadataEntity::getId)
                .toList();

        if (!deleted.isEmpty()) {
            metadataService.hardDeleteBatch(deleted);
            log.info("HardDeleteJob: hard-deleted {} DB records + S3 objects", deleted.size());
        }
    }

    private boolean deleteFromS3(String objectKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("HardDeleteJob: S3 delete failed key={}: {}", objectKey, e.getMessage());
            return false;
        }
    }
}
