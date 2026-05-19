
# back_base
Summary:
Initialized Next.js backend in back_base with /photos directory. Implemented /api/upload for photo uploads and /api/list for listing photos. All files stored in /photos. Project ready for launch.


This is a Next.js backend for photo uploads.

- Runs on localhost:5100
- /api/upload: Receives photo uploads (multipart/form-data, field 'file'), stores them in /photos
- /api/list: Returns the list of photo filenames in /photos

## Setup

1. Install dependencies:
	npm install

2. Start the server:
	npm run dev

3. Uploaded files are saved in the /photos directory.

## Endpoints

- POST /api/upload: Upload a photo
- GET /api/list: Get list of uploaded photos

