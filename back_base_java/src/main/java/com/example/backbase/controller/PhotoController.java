package com.example.backbase.controller;

import com.example.backbase.model.CursorPage;
import com.example.backbase.model.ImageMetadata;
import com.example.backbase.service.IdempotencyService;
import com.example.backbase.service.PhotoServiceAWS;
import com.example.backbase.service.StorageService;

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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PhotoController {

    private final StorageService storageService;
    private final IdempotencyService idempotencyService;
    /** Nullable: only present when storage.backend=s3. Used for confirm-upload dedup. */
    private final PhotoServiceAWS photoServiceAWS;

    public PhotoController(StorageService storageService,
                           IdempotencyService idempotencyService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                           PhotoServiceAWS photoServiceAWS) {
        this.storageService = storageService;
        this.idempotencyService = idempotencyService;
        this.photoServiceAWS = photoServiceAWS;
    }

    // -------------------------------------------------------------------------
    // Presigned URL endpoints — client uploads/downloads directly to/from S3
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/upload-url
     * Body: { "filename": "photo.jpg", "contentType": "image/jpeg" }
     * Returns: { "objectKey": "<uuid>.jpg", "uploadUrl": "<presigned PUT URL>" }
     * Client PUTs the file binary to uploadUrl directly; no data passes through the app.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<?> requestUploadUrl(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        String contentType = body.getOrDefault("contentType", "application/octet-stream");
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "filename is required"));
        }
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "bin";
        String objectKey = System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + ext;
        String uploadUrl = storageService.generatePresignedUploadUrl(objectKey, contentType);
        if (uploadUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "Presigned URLs not supported by active storage backend"));
        }
        return ResponseEntity.ok(Map.of("objectKey", objectKey, "uploadUrl", uploadUrl));
    }

    /**
     * GET /api/v1/photos/{filename}/download-url
     * Returns a short-lived presigned GET URL for a specific object key.
     * Client uses this URL to fetch the image directly from S3.
     */
    @GetMapping("/photos/{filename:.+}/download-url")
    public ResponseEntity<?> requestDownloadUrl(@PathVariable String filename) {
        String downloadUrl = storageService.generatePresignedDownloadUrl(filename);
        if (downloadUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "Presigned URLs not supported by active storage backend"));
        }
        return ResponseEntity.ok(Map.of("filename", filename, "downloadUrl", downloadUrl));
    }

    // -------------------------------------------------------------------------
    // Cursor-based list — preferred over offset pagination for high traffic
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/list-cursor?cursor=<base64>&size=20
     * Returns { images: [...], nextCursor: "...", hasMore: true }
     * cursor omitted or blank → first page.
     * Use nextCursor from response as cursor param for next page.
     */
    @GetMapping("/list-cursor")
    public ResponseEntity<?> listCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        try {
            CursorPage<ImageMetadata> page = storageService.listImagesCursor(cursor, size);
            return ResponseEntity.ok(Map.of(
                    "images", page.items(),
                    "nextCursor", page.nextCursor() != null ? page.nextCursor() : "",
                    "hasMore", page.hasMore()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list images"));
        }
    }

    // -------------------------------------------------------------------------
    // Confirm-upload — for presigned URL flow + server-side deduplication
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/confirm-upload
     * Body: { "objectKey": "...", "contentHash": "<sha256hex>", "size": 12345, "mimeType": "image/jpeg" }
     * Call after client finishes PUT to presigned URL.
     * If duplicate found: redundant S3 object is cleaned up; response returns canonical objectKey.
     */
    @PostMapping("/confirm-upload")
    public ResponseEntity<?> confirmUpload(@RequestBody Map<String, Object> body) {
        String objectKey   = (String) body.get("objectKey");
        String contentHash = (String) body.get("contentHash");
        String mimeType    = (String) body.getOrDefault("mimeType", "application/octet-stream");
        long   size        = body.get("size") instanceof Number n ? n.longValue() : 0L;

        if (objectKey == null || contentHash == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "objectKey and contentHash are required"));
        }
        if (photoServiceAWS == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "confirm-upload not supported for local backend"));
        }
        PhotoServiceAWS.ConfirmResult result = photoServiceAWS.confirmUpload(objectKey, contentHash, size, mimeType);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "objectKey", result.objectKey(),
                "deduplicated", result.deduplicated()));
    }

    // -------------------------------------------------------------------------
    // Server-side upload (backward compat — prefer presigned upload for high traffic)
    // -------------------------------------------------------------------------

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && idempotencyService.exists(idempotencyKey)) {
            return ResponseEntity.ok(idempotencyService.get(idempotencyKey));
        }

        try {
            String filename = storageService.store(file);
            Map<String, Object> response = Map.of("success", true, "filename", filename);
            if (idempotencyKey != null) {
                idempotencyService.store(idempotencyKey, response);
            }
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to store file"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        try {
            List<ImageMetadata> images = storageService.listImages();
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list images"));
        }
    }

    @GetMapping("/list-paginated")
    public ResponseEntity<?> listPaginated(@RequestParam(defaultValue = "0") int page) {
        try {
            List<ImageMetadata> images = storageService.listImagesPaginated(page);
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list images"));
        }
    }

    /**
     * Proxy endpoint — streams image bytes through the app.
     * Kept for backward compatibility. Prefer presigned download URLs for high-traffic deployments.
     */
    @GetMapping("/photos/{filename:.+}")
    public ResponseEntity<?> getPhoto(@PathVariable String filename) {
        try {
            byte[] data = storageService.loadImage(filename);
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
    public ResponseEntity<?> delete(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        String filename = body.get("filename");
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Filename is required"));
        }

        if (idempotencyKey != null && idempotencyService.exists(idempotencyKey)) {
            return ResponseEntity.ok(idempotencyService.get(idempotencyKey));
        }

        try {
            storageService.deleteImage(filename);
            Map<String, Object> response = Map.of("success", true, "message", "File deleted successfully");
            if (idempotencyKey != null) {
                idempotencyService.store(idempotencyKey, response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete file"));
        }
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<?> deleteAll(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null && idempotencyService.exists(idempotencyKey)) {
            return ResponseEntity.ok(idempotencyService.get(idempotencyKey));
        }

        try {
            int count = storageService.deleteAll();
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Deleted " + count + " file(s) successfully",
                    "deletedCount", count);
            if (idempotencyKey != null) {
                idempotencyService.store(idempotencyKey, response);
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete files"));
        }
    }
}
