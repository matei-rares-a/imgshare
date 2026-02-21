import { useState, useEffect } from 'react'
import { getStoredImages, deleteImage, clearAllImages, StoredImage } from '../utils/imageStorage'
import '../styles/AdminPanel.css'

interface AdminPanelProps {
  onLogout: () => void
}

function AdminPanel({ onLogout }: AdminPanelProps) {
  const [images, setImages] = useState<StoredImage[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    loadImages()
  }, [])

  const loadImages = () => {
    setImages(getStoredImages())
    setSelectedIds(new Set())
  }

  const handleSelectImage = (id: string) => {
    const newSelected = new Set(selectedIds)
    if (newSelected.has(id)) {
      newSelected.delete(id)
    } else {
      newSelected.add(id)
    }
    setSelectedIds(newSelected)
  }

  const handleSelectAll = () => {
    if (selectedIds.size === images.length) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(images.map((img) => img.id)))
    }
  }

  const handleDeleteSelected = () => {
    if (selectedIds.size === 0) {
      alert('Please select at least one image to delete')
      return
    }

    if (
      window.confirm(
        `Are you sure you want to delete ${selectedIds.size} selected image(s)? This cannot be undone.`
      )
    ) {
      setIsDeleting(true)
      setTimeout(() => {
        selectedIds.forEach((id) => {
          deleteImage(id)
        })
        loadImages()
        setIsDeleting(false)
      }, 300)
    }
  }

  const handleDeleteAll = () => {
    if (images.length === 0) {
      alert('No images to delete')
      return
    }

    if (
      window.confirm(
        `Are you sure you want to delete ALL ${images.length} images? This action cannot be undone.`
      )
    ) {
      setIsDeleting(true)
      setTimeout(() => {
        clearAllImages()
        loadImages()
        setIsDeleting(false)
      }, 300)
    }
  }

  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp)
    return date.toLocaleString()
  }

  return (
    <div className="admin-panel">
      <div className="admin-header">
        <div className="admin-title-section">
          <h2>🔐 Admin Dashboard</h2>
          <span className="image-count">{images.length} images</span>
        </div>
        <button onClick={onLogout} className="btn-logout">
          🚪 Logout
        </button>
      </div>

      {images.length === 0 ? (
        <div className="empty-admin">
          <div className="empty-icon">📸</div>
          <p>No images to manage</p>
        </div>
      ) : (
        <>
          <div className="admin-controls">
            <div className="control-group">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={selectedIds.size === images.length && images.length > 0}
                  onChange={handleSelectAll}
                  disabled={isDeleting}
                />
                <span>
                  {selectedIds.size === images.length && images.length > 0
                    ? 'Deselect All'
                    : 'Select All'}
                </span>
              </label>
              <span className="selection-count">
                {selectedIds.size} selected
              </span>
            </div>

            <div className="action-group">
              <button
                onClick={handleDeleteSelected}
                className="btn btn-danger"
                disabled={selectedIds.size === 0 || isDeleting}
              >
                🗑️ Delete Selected ({selectedIds.size})
              </button>
              <button
                onClick={handleDeleteAll}
                className="btn btn-danger-all"
                disabled={isDeleting}
              >
                🔥 Delete All
              </button>
            </div>
          </div>

          <div className="admin-table">
            <div className="table-header">
              <div className="col-check">
                <input type="checkbox" disabled />
              </div>
              <div className="col-preview">Preview</div>
              <div className="col-filename">Filename</div>
              <div className="col-original">Original Name</div>
              <div className="col-uploaded">Uploaded</div>
              <div className="col-size">Size</div>
            </div>

            <div className="table-body">
              {images.map((image) => (
                <div
                  key={image.id}
                  className={`table-row ${selectedIds.has(image.id) ? 'selected' : ''}`}
                >
                  <div className="col-check">
                    <input
                      type="checkbox"
                      checked={selectedIds.has(image.id)}
                      onChange={() => handleSelectImage(image.id)}
                      disabled={isDeleting}
                    />
                  </div>
                  <div className="col-preview">
                    <img src={image.dataUrl} alt={image.fileName} />
                  </div>
                  <div className="col-filename">{image.fileName}</div>
                  <div className="col-original">{image.originalFileName}</div>
                  <div className="col-uploaded">{formatDate(image.uploadedAt)}</div>
                  <div className="col-size">{image.fileSize}</div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default AdminPanel
