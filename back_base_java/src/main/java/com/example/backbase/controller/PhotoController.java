package com.example.backbase.controller;

import com.example.backbase.model.ImageMetadata;
import com.example.backbase.service.PhotoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.NoSuchFileException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            String filename = photoService.store(file);
            return ResponseEntity.ok(Map.of("success", true, "filename", filename));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store file"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        try {
            List<ImageMetadata> images = photoService.listImages();
            return ResponseEntity.ok(Map.of("images", images));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list images"));
        }
    }

    @GetMapping("/photos/{filename:.+}")
    public ResponseEntity<?> getPhoto(@PathVariable String filename) {
        try {
            byte[] data = photoService.loadImage(filename);
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            MediaType mediaType = switch (ext) {
                case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                case "png" -> MediaType.IMAGE_PNG;
                case "gif" -> MediaType.IMAGE_GIF;
                case "webp" -> MediaType.parseMediaType("image/webp");
                case "svg" -> MediaType.parseMediaType("image/svg+xml");
                case "bmp" -> MediaType.parseMediaType("image/bmp");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(mediaType)
                    .body(data);
        } catch (NoSuchFileException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read file"));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Filename is required"));
        }
        try {
            photoService.deleteImage(filename);
            return ResponseEntity.ok(Map.of("success", true, "message", "File deleted successfully"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete file"));
        }
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAll() {
        try {
            int count = photoService.deleteAll();
            return ResponseEntity.ok(Map.of("success", true, "message", "Deleted " + count + " file(s) successfully", "deletedCount", count));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete files"));
        }
    }
}