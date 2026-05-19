package com.example.backbase.service;

import com.example.backbase.model.ImageMetadata;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

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

    private final S3Client s3;
    private final S3Presigner presigner;

    public PhotoServiceAWS() {
        Region region = Region.of(System.getProperty("aws.s3.region",
                System.getenv().getOrDefault("AWS_REGION", "eu-central-1")));
        this.s3 = S3Client.builder().region(region).build();
        this.presigner = S3Presigner.builder().region(region).build();
    }

    public String store(MultipartFile file) throws IOException {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) {
            ext = "jpg";
        }

        String randomName = System.currentTimeMillis() + "-" +
                Long.toHexString(Double.doubleToLongBits(Math.random())) + "." + ext;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(randomName)
                .contentType(file.getContentType())
                .build();

        s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return randomName;
    }

    @Override
    public List<ImageMetadata> listImages() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response response = s3.listObjectsV2(request);

        return response.contents().stream()
                .map(obj -> {
                    String filename = obj.key();
                    long uploadedAt = parseTimestamp(filename, obj.size());
                    String fileSize = String.format("%.2f KB", obj.size() / 1024.0);
                    // Return presigned GET URL — client fetches directly from S3, not through app
                    String url = generatePresignedDownloadUrl(filename);
                    return new ImageMetadata(filename, url, uploadedAt, fileSize);
                })
                .sorted(Comparator.comparingLong(ImageMetadata::getUploadedAt).reversed())
                .collect(Collectors.toList());
    }

    

    /**
     * Returns a presigned S3 PUT URL valid for uploadTtlMinutes.
     * Client uploads the file binary directly to S3 — zero traffic through the app node.
     */
    @Override
    public String generatePresignedUploadUrl(String objectKey, String contentType) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
                .signatureDuration(Duration.ofMinutes(uploadTtlMinutes))
                .putObjectRequest(p -> p
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(contentType)
                )
        );
        return presigned.url().toString();
    }

    /**
     * Returns a presigned S3 GET URL valid for downloadTtlMinutes.
     * Client fetches the image directly from S3 — zero traffic through the app node.
     */
    @Override
    public String generatePresignedDownloadUrl(String objectKey) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(r -> r
                .signatureDuration(Duration.ofMinutes(downloadTtlMinutes))
                .getObjectRequest(g -> g
                        .bucket(bucketName)
                        .key(objectKey)
                )
        );
        return presigned.url().toString();
    }

    private long parseTimestamp(String filename, long defaultValue) {
        try {
            String[] parts = filename.split("-");
            return Long.parseLong(parts[0]);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public byte[] loadImage(String filename) throws IOException {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(request)) {
            return s3Object.readAllBytes();
        }
    }

    public void deleteImage(String filename) {

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .build();

        s3.deleteObject(request);
    }

    public int deleteAll() {

        ListObjectsV2Response list = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucketName).build()
        );

        int count = 0;

        for (S3Object obj : list.contents()) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(obj.key())
                    .build());
            count++;
        }

        return count;
    }

    @Value("${gallery.initial-load}")
    private int initialLoad;

    @Value("${gallery.page-load}")
    private int pageLoad;

    public List<ImageMetadata> listImagesPaginated(int page) {
        // First page (0) returns 50 images, subsequent pages return 40 images
        int pageSize = page == 0 ? initialLoad : pageLoad;
        // Request extra items to ensure we get enough after sorting
        int requestSize = pageSize + initialLoad; // Buffer for sorting by timestamp
        
        int markerIndex = page == 0 ? 0 : initialLoad + (page - 1) * pageLoad;
        
        // Fetch all objects and sort by timestamp (most recent first)
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .maxKeys(Math.max(1000, markerIndex + requestSize)) // Request enough to get to current page
                .build();

        ListObjectsV2Response response = s3.listObjectsV2(request);

        // Convert to ImageMetadata and sort by timestamp descending
        List<ImageMetadata> allImages = response.contents().stream()
                .map(obj -> {
                    String filename = obj.key();
                    long uploadedAt = parseTimestamp(filename, obj.size());
                    String fileSize = String.format("%.2f KB", obj.size() / 1024.0);
                    String url = generatePresignedDownloadUrl(filename);
                    return new ImageMetadata(filename, url, uploadedAt, fileSize);
                })
                .sorted(Comparator.comparingLong(ImageMetadata::getUploadedAt).reversed())
                .collect(Collectors.toList());

        // Calculate pagination boundaries
        int startIndex = markerIndex;
        int endIndex = Math.min(markerIndex + pageSize, allImages.size());

        if (startIndex >= allImages.size()) {
            return List.of();
        }

        return allImages.subList(startIndex, endIndex);
    }
}