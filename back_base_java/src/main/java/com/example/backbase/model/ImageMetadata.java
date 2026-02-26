package com.example.backbase.model;

public class ImageMetadata {
    private String filename;
    private String url;
    private long uploadedAt;
    private String fileSize;

    public ImageMetadata() {}

    public ImageMetadata(String filename, String url, long uploadedAt, String fileSize) {
        this.filename = filename;
        this.url = url;
        this.uploadedAt = uploadedAt;
        this.fileSize = fileSize;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(long uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }
}