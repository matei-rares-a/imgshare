package com.example.backbase.service;

import com.example.backbase.model.ImageMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PhotoService {

    @Value("${photo.storage.dir:photos}")
    private String storageDir;

    private Path photosPath;

    @PostConstruct
    public void init() throws IOException {
        photosPath = Paths.get(storageDir);
        if (!Files.exists(photosPath)) {
            Files.createDirectories(photosPath);
        }
    }

    public String store(MultipartFile file) throws IOException {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) {
            ext = "jpg";
        }
        String randomName = System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random())) + "." + ext;
        Path target = photosPath.resolve(randomName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return randomName;
    }

    public List<ImageMetadata> listImages() throws IOException {
        if (!Files.exists(photosPath)) {
            return new ArrayList<>();
        }
        List<ImageMetadata> images = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(photosPath)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    String filename = p.getFileName().toString();
                    long uploadedAt = parseTimestamp(filename, Files.size(p));
                    String fileSize = String.format("%.2f KB", Files.size(p) / 1024.0);
                    images.add(new ImageMetadata(filename, "/api/photos/" + filename, uploadedAt, fileSize));
                }
            }
        }
        return images.stream()
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
        Path file = photosPath.resolve(filename);
        if (!file.normalize().startsWith(photosPath)) {
            throw new IOException("Invalid file path");
        }
        if (!Files.exists(file)) {
            throw new NoSuchFileException(filename);
        }
        return Files.readAllBytes(file);
    }

    public void deleteImage(String filename) throws IOException {
        Path file = photosPath.resolve(filename);
        if (!file.normalize().startsWith(photosPath)) {
            throw new IOException("Invalid file path");
        }
        Files.deleteIfExists(file);
    }

    public int deleteAll() throws IOException {
        if (!Files.exists(photosPath)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(photosPath)) {
            int count = 0;
            for (Path p : stream) {
                Files.deleteIfExists(p);
                count++;
            }
            return count;
        }
    }
}