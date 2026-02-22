import { useState, useEffect } from 'react'
import { getBackendApiUrl, getBackendUrl } from '../utils/apiConfig'
import '../styles/Gallery.css'

interface GalleryProps {
  refreshTrigger?: number
}
function Gallery({ refreshTrigger }: GalleryProps) {
  const [images, setImages] = useState<any[]>([])
  const [selectedImage, setSelectedImage] = useState<any | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadImages()
  }, [refreshTrigger])

  const loadImages = async () => {
    setLoading(true)
    try {
      const res = await fetch(getBackendApiUrl('/api/list'))
      if (!res.ok) throw new Error('Failed to fetch images')
      const data = await res.json()
      // Convert relative URLs to absolute URLs
      const imagesWithAbsoluteUrls = (data.images || []).map((img: any) => ({
        ...img,
        url: `${getBackendUrl()}${img.url}`
      }))
      setImages(imagesWithAbsoluteUrls)
    } catch (e) {
      console.error('Error loading images:', e)
      setImages([])
    }
    setLoading(false)
  }

  const handleDownload = async (image: any) => {
    try {
      const res = await fetch(image.url)
      const blob = await res.blob()
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = image.filename || 'photo.jpg'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(link.href)
    } catch (error) {
      console.error('Error downloading image:', error)
      alert('Failed to download image')
    }
  }

  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp)
    return date.toLocaleString()
  }

  const formatTimeAgo = (timestamp: number): string => {
    const now = Date.now()
    const diff = now - timestamp
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (days > 0) return `${days}d ago`
    if (hours > 0) return `${hours}h ago`
    if (minutes > 0) return `${minutes}m ago`
    return 'just now'
  }

  return (
    <div className="gallery">
      <div className="gallery-header">
        <div className="gallery-title-section">
          <h2>📷 Gallery</h2>
          <span className="image-count">{images.length} images</span>
        </div>
      </div>

      {loading ? (
        <div className="empty-gallery">
          <div className="empty-icon">⏳</div>
          <p>Loading images...</p>
        </div>
      ) : images.length === 0 ? (
        <div className="empty-gallery">
          <div className="empty-icon">📸</div>
          <p>No images yet. Upload one to get started!</p>
        </div>
      ) : (
        <>
          <div className="gallery-grid">
            {images.map((image) => (
              <div
                key={image.filename}
                className="gallery-item"
                onClick={() => setSelectedImage(image)}
              >
                <div className="gallery-image-container">
                  <img src={image.url} alt="photo" className="gallery-image" />
                  <div className="gallery-overlay">
                    <button className="btn-view">👁️ View</button>
                  </div>
                </div>
                <div className="gallery-info">
                  <p className="gallery-time">{formatTimeAgo(image.uploadedAt)}</p>
                  <p className="gallery-size">{image.fileSize}</p>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* Modal for full image view */}
      {selectedImage && (
        <div className="modal-overlay" onClick={() => setSelectedImage(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <button className="btn-close" onClick={() => setSelectedImage(null)}>
              ✕
            </button>
            <img src={selectedImage.url} alt="photo" />
            <div className="modal-info">
              <div className="info-row">
                <span className="label">Uploaded:</span>
                <span className="value">{formatDate(selectedImage.uploadedAt)}</span>
              </div>
              <div className="info-row">
                <span className="label">Size:</span>
                <span className="value">{selectedImage.fileSize}</span>
              </div>
            </div>
            <div className="modal-actions">
              <button
                onClick={() => handleDownload(selectedImage)}
                className="btn btn-primary"
              >
                💾 Download
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Gallery
