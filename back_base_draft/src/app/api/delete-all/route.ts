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
	const photosDir = path.join(process.cwd(), 'photos');

	try {
		// Check if directory exists
		await fs.stat(photosDir);

		// Read all files in the directory
		const files = await fs.readdir(photosDir);

		// Delete all files
		for (const file of files) {
			const filePath = path.join(photosDir, file);
			await fs.unlink(filePath);
		}

		return NextResponse.json(
			{ success: true, message: `Deleted ${files.length} file(s) successfully`, deletedCount: files.length },
			{ headers: corsHeaders }
		);
	} catch (error) {
		console.error('Error deleting all files:', error);
		return NextResponse.json({ error: 'Failed to delete files' }, { status: 500, headers: corsHeaders });
	}
}
