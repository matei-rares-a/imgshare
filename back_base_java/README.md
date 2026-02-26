# Back Base Spring Boot Service

This module replicates the functionality of the original `back_base` Next.js API using Spring Boot. It provides endpoints for uploading, listing, retrieving, and deleting image files stored in a local `photos` directory.

## Endpoints

- `POST /api/upload` - multipart upload with form field `file`.
- `GET /api/list` - list images with metadata.
- `GET /api/photos/{filename}` - serve an image file.
- `DELETE /api/delete` - delete a single image (JSON body `{ "filename": "..." }`).
- `DELETE /api/delete-all` - remove all images from storage.

CORS is enabled for all origins.

## Building and running

```sh
mvn clean package
java -jar target/back_base-0.0.1-SNAPSHOT.jar
```

The service listens on port 8080 by default and creates a `photos` directory next to the jar if it doesn't exist.