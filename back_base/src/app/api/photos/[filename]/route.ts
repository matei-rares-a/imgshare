import { NextRequest, NextResponse } from 'next/server';
import { promises as fs } from 'fs';
import path from 'path';

const corsHeaders = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'GET, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type',
};

export async function OPTIONS() {
	return new NextResponse(null, { status: 200, headers: corsHeaders });
}

export async function GET(
	req: NextRequest,
	{ params }: { params: Promise<{ filename: string }> | { filename: string } }
) {
	// Handle both Promise and non-Promise params
	const resolvedParams = await Promise.resolve(params);
	const filename = resolvedParams.filename;
	
	if (!filename) {
		return NextResponse.json({ error: 'Filename is required' }, { status: 400, headers: corsHeaders });
	}

	const photosDir = path.join(process.cwd(), 'photos');
	const filePath = path.join(photosDir, filename);

	try {
		// Validate the filename to prevent directory traversal attacks
		if (!filePath.startsWith(photosDir)) {
			return NextResponse.json({ error: 'Invalid file path' }, { status: 400, headers: corsHeaders });
		}

		const fileContent = await fs.readFile(filePath);
		const ext = path.extname(filename).toLowerCase();
		const mimeTypes: { [key: string]: string } = {
			'.jpg': 'image/jpeg',
			'.jpeg': 'image/jpeg',
			'.png': 'image/png',
			'.gif': 'image/gif',
			'.webp': 'image/webp',
			'.svg': 'image/svg+xml',
			'.bmp': 'image/bmp'
		};

		const contentType = mimeTypes[ext] || 'application/octet-stream';

		return new NextResponse(fileContent, {
			headers: {
				'Content-Type': contentType,
				'Cache-Control': 'public, max-age=86400',
				...corsHeaders
			}
		});
	} catch (error) {
		console.error('Error serving image:', error);
		return NextResponse.json({ error: 'File not found' }, { status: 404, headers: corsHeaders });
	}
}
