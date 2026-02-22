import { NextRequest, NextResponse } from 'next/server';
import { promises as fs } from 'fs';
import path from 'path';

const headers = {
	'Access-Control-Allow-Origin': '*',
	'Access-Control-Allow-Methods': 'POST, OPTIONS',
	'Access-Control-Allow-Headers': 'Content-Type',
};

export async function OPTIONS() {
	return new NextResponse(null, { status: 200, headers });
}

export async function POST(req: NextRequest) {
	const formData = await req.formData();
	const file = formData.get('file');
	if (!file || typeof file === 'string') {
		return NextResponse.json({ error: 'No file uploaded' }, { status: 400, headers });
	}

	// Generate random filename
	const ext = (file as Blob).type.split('/')[1] || 'jpg';
	const randomName = `${Date.now()}-${Math.random().toString(36).substring(2, 10)}.${ext}`;
	const photosDir = path.join(process.cwd(), 'photos');

	// Ensure /photos directory exists
	await fs.mkdir(photosDir, { recursive: true });

	// Save file
	const arrayBuffer = await (file as Blob).arrayBuffer();
	await fs.writeFile(path.join(photosDir, randomName), Buffer.from(arrayBuffer));

	return NextResponse.json({ success: true, filename: randomName }, { headers });
}