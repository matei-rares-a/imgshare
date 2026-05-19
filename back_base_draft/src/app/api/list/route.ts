import { NextResponse } from 'next/server';
import { promises as fs } from 'fs';
import path from 'path';

const headers = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'GET, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type',
};

export async function OPTIONS() {
	return new NextResponse(null, { status: 200, headers });
}

export async function GET() {
	const photosDir = path.join(process.cwd(), 'photos');
	let files: string[] = [];
	try {
		files = await fs.readdir(photosDir);
	} catch (e) {
		// Directory may not exist yet
		return NextResponse.json({ images: [] }, { headers });
	}

	// Return list of images with metadata
	const images = await Promise.all(
		files.map(async (file) => {
			try {
				const filePath = path.join(photosDir, file);
				const stats = await fs.stat(filePath);
				const timestamp = parseInt(file.split('-')[0]) || stats.birthtimeMs;
				return {
					filename: file,
					url: `/api/photos/${file}`,
					uploadedAt: timestamp,
					fileSize: `${(stats.size / 1024).toFixed(2)} KB`
				};
			} catch (e) {
				return null;
			}
		})
	);

	return NextResponse.json({ 
		images: images.filter(img => img !== null).sort((a, b) => b.uploadedAt - a.uploadedAt)
	}, { headers });
}