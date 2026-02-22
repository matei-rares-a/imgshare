import { useState, useEffect } from 'react'
import { getBackendApiUrl, getBackendUrl } from '../utils/apiConfig'
import '../styles/AdminPanel.css'

interface AdminPanelProps {
  onLogout: () => void
}

interface PhotoItem {
  filename: string
  url: string
  uploadedAt: number
  fileSize: string
}

function AdminPanel({ onLogout }: AdminPanelProps) {
  const [images, setImages] = useState<PhotoItem[]>([])
  const [selectedFilenames, setSelectedFilenames] = useState<Set<string>>(new Set())
  const [isDeleting, setIsDeleting] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadImages()
  }, [])

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
      setSelectedFilenames(new Set())
    } catch (e) {
      console.error('Error loading images:', e)
      setImages([])
    }
    setLoading(false)
  }

  const handleSelectImage = (filename: string) => {
    const newSelected = new Set(selectedFilenames)
    if (newSelected.has(filename)) {
      newSelected.delete(filename)
    } else {
      newSelected.add(filename)
    }
    setSelectedFilenames(newSelected)
  }

  const handleSelectAll = () => {
    if (selectedFilenames.size === images.length) {
      setSelectedFilenames(new Set())
    } else {
      setSelectedFilenames(new Set(images.map((img) => img.filename)))
    }
  }

  const handleDeleteSelected = async () => {
    if (selectedFilenames.size === 0) {
      alert('Please select at least one image to delete')
      return
    }

    if (
      window.confirm(
        `Are you sure you want to delete ${selectedFilenames.size} selected image(s)? This cannot be undone.`
      )
    ) {
      setIsDeleting(true)
      try {
        for (const filename of selectedFilenames) {
          const res = await fetch(getBackendApiUrl('/api/delete'), {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filename }),
          })
          if (!res.ok) {
            throw new Error(`Failed to delete ${filename}`)
          }
        }
        await loadImages()
      } catch (error) {
        console.error('Error deleting files:', error)
        alert('Error deleting some files. Please try again.')
        await loadImages()
      } finally {
        setIsDeleting(false)
      }
    }
  }

  const handleDeleteAll = async () => {
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
      try {
        const res = await fetch(getBackendApiUrl('/api/delete-all'), {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
        })
        if (!res.ok) throw new Error('Failed to delete all images')
        await loadImages()
      } catch (error) {
        console.error('Error deleting all files:', error)
        alert('Error deleting files. Please try again.')
        await loadImages()
      } finally {
        setIsDeleting(false)
      }
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

      {loading ? (
        <div className="empty-admin">
          <div className="empty-icon">⏳</div>
          <p>Loading images...</p>
        </div>
      ) : images.length === 0 ? (
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
                  checked={selectedFilenames.size === images.length && images.length > 0}
                  onChange={handleSelectAll}
                  disabled={isDeleting}
                />
                <span>
                  {selectedFilenames.size === images.length && images.length > 0
                    ? 'Deselect All'
                    : 'Select All'}
                </span>
              </label>
              <span className="selection-count">
                {selectedFilenames.size} selected
              </span>
            </div>

            <div className="action-group">
              <button
                onClick={handleDeleteSelected}
                className="btn btn-danger"
                disabled={selectedFilenames.size === 0 || isDeleting}
              >
                🗑️ Delete Selected ({selectedFilenames.size})
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
              <div className="col-uploaded">Uploaded</div>
              <div className="col-size">Size</div>
            </div>

            <div className="table-body">
              {images.map((image) => (
                <div
                  key={image.filename}
                  className={`table-row ${selectedFilenames.has(image.filename) ? 'selected' : ''}`}
                >
                  <div className="col-check">
                    <input
                      type="checkbox"
                      checked={selectedFilenames.has(image.filename)}
                      onChange={() => handleSelectImage(image.filename)}
                      disabled={isDeleting}
                    />
                  </div>
                  <div className="col-preview">
                    <img src={image.url} alt={image.filename} />
                  </div>
                  <div className="col-filename">{image.filename}</div>
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
