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

import java.io.IOException;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PhotoServiceAWS {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final S3Client s3;

    public PhotoServiceAWS() {
        this.s3 = S3Client.builder()
                .region(Region.EU_CENTRAL_1)
                .build();
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
                    return new ImageMetadata(filename, "/api/photos/" + filename, uploadedAt, fileSize);
                })
                .sorted(Comparator.comparingLong(ImageMetadata::getUploadedAt).reversed())
                .collect(Collectors.toList());
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
                    return new ImageMetadata(filename, "/api/photos/" + filename, uploadedAt, fileSize);
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