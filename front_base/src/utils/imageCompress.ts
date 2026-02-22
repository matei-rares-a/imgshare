export async function compressDataUrl(
  dataUrl: string,
  maxWidth = 1024,
  quality = 0.7
): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      try {
        const ratio = img.width / img.height
        let targetWidth = img.width
        let targetHeight = img.height
        if (img.width > maxWidth) {
          targetWidth = maxWidth
          targetHeight = Math.round(maxWidth / ratio)
        }

        const canvas = document.createElement('canvas')
        canvas.width = targetWidth
        canvas.height = targetHeight
        const ctx = canvas.getContext('2d')
        if (!ctx) return reject(new Error('Canvas not supported'))
        // Draw white background for images with transparency to avoid black background after JPEG conversion
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(0, 0, targetWidth, targetHeight)
        ctx.drawImage(img, 0, 0, targetWidth, targetHeight)
        const compressed = canvas.toDataURL('image/jpeg', quality)
        resolve(compressed)
      } catch (err) {
        reject(err)
      }
    }
    img.onerror = (e) => reject(new Error('Failed to load image for compression'))
    img.src = dataUrl
  })
}
