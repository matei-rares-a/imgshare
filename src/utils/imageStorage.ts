export interface StoredImage {
  id: string
  dataUrl: string
  fileName: string
  originalFileName: string
  uploadedAt: number
  fileSize: string
}

const STORAGE_KEY = 'imgshare_images'

/**
 * Get all stored images sorted by upload time (newest first)
 */
export function getStoredImages(): StoredImage[] {
  try {
    const data = localStorage.getItem(STORAGE_KEY)
    const images: StoredImage[] = data ? JSON.parse(data) : []
    // Sort by uploadedAt descending (newest first)
    return images.sort((a, b) => b.uploadedAt - a.uploadedAt)
  } catch (error) {
    console.error('Error retrieving images from storage:', error)
    return []
  }
}

/**
 * Save an image to storage
 */
export function saveImage(image: StoredImage): void {
  try {
    const images = getStoredImages()
    images.unshift(image) // Add to beginning (will be sorted by getStoredImages)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(images))
  } catch (error) {
    console.error('Error saving image to storage:', error)
    throw new Error('Failed to save image')
  }
}

/**
 * Delete an image from storage
 */
export function deleteImage(id: string): void {
  try {
    let images = getStoredImages()
    images = images.filter((img) => img.id !== id)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(images))
  } catch (error) {
    console.error('Error deleting image from storage:', error)
    throw new Error('Failed to delete image')
  }
}

/**
 * Clear all images from storage
 */
export function clearAllImages(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.error('Error clearing storage:', error)
    throw new Error('Failed to clear images')
  }
}

/**
 * Generate unique ID for image
 */
export function generateImageId(): string {
  return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}
