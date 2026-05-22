package com.example.backbase.service;

import com.example.backbase.model.CursorPage;
import com.example.backbase.model.ImageMetadata;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction over storage backends (local filesystem or S3-compatible).
 * Controllers depend on this interface; the active backend is selected via storage.backend property.
 */
public interface StorageService {

    /** Store file and return the generated object key / filename. */
    String store(MultipartFile file) throws IOException;

    List<ImageMetadata> listImages() throws IOException;

    /**
     * Cursor-based pagination. cursor=null → first page.
     * Returns CursorPage with nextCursor for subsequent requests.
     */
    CursorPage<ImageMetadata> listImagesCursor(String cursor, int size) throws IOException;

    /** Offset pagination kept for backward compatibility. Prefer listImagesCursor. */
    List<ImageMetadata> listImagesPaginated(int page) throws IOException;

    /** Load raw bytes — kept for backward compat; prefer presigned URLs for high-traffic. */
    byte[] loadImage(String filename) throws IOException;

    void deleteImage(String filename) throws IOException;

    int deleteAll() throws IOException;

    /**
     * Generate a presigned PUT URL so the client uploads directly to object storage.
     * Returns null for local-filesystem backend (not applicable).
     */
    default String generatePresignedUploadUrl(String objectKey, String contentType) {
        return null;
    }

    /**
     * Generate a presigned GET URL so the client fetches directly from object storage / CDN.
     * Returns null for local-filesystem backend (not applicable).
     */
    default String generatePresignedDownloadUrl(String objectKey) {
        return null;
    }
}

