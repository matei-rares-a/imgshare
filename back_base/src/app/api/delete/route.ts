import { NextRequest, NextResponse } from 'next/server';
import { promises as fs } from 'fs';
import path from 'path';

const corsHeaders = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'POST, DELETE, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type',
};

export async function OPTIONS() {
	return new NextResponse(null, { status: 200, headers: corsHeaders });
}

export async function DELETE(req: NextRequest) {
	const { filename } = await req.json();

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

		// Check if file exists
		await fs.stat(filePath);

		// Delete the file
		await fs.unlink(filePath);

		return NextResponse.json({ success: true, message: 'File deleted successfully' }, { headers: corsHeaders });
	} catch (error) {
		console.error('Error deleting file:', error);
		return NextResponse.json({ error: 'Failed to delete file' }, { status: 500, headers: corsHeaders });
	}
}
