import { useState, useRef } from 'react'
import { generateRandomFileName } from '../utils/fileUtils'
import { compressDataUrl } from '../utils/imageCompress'
import { getBackendApiUrl } from '../utils/apiConfig'
import '../styles/PhotoUploader.css'

interface PhotoUploaderProps {
  onImageSaved?: () => void
}

function PhotoUploader({ onImageSaved }: PhotoUploaderProps) {
  const [uploadedPhoto, setUploadedPhoto] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [fileSize, setFileSize] = useState<string>('')
  const [uploadTime, setUploadTime] = useState<string>('')
  const [isSaving, setIsSaving] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      if (!file.type.startsWith('image/')) {
        alert('Please select an image file')
        return
      }
      setFile(file)
      setFileSize(`${(file.size / 1024).toFixed(2)} KB`)
      setUploadTime(new Date().toLocaleString())
      const reader = new FileReader()
      reader.onload = (e) => {
        setUploadedPhoto(e.target?.result as string)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleUpload = async () => {
    if (file) {
      setIsSaving(true)
      try {
        const formData = new FormData()
        formData.append('file', file)
        const res = await fetch(getBackendApiUrl('/api/upload'), {
          method: 'POST',
          body: formData
        })
        if (!res.ok) throw new Error('Failed to upload image')
        onImageSaved?.()
        setTimeout(() => {
          handleReset()
          setIsSaving(false)
        }, 500)
      } catch (error) {
        console.error('Error uploading image:', error)
        alert('Error uploading image. Please try again.')
        setIsSaving(false)
      }
    }
  }

  const handleReset = () => {
    setUploadedPhoto(null)
    setFile(null)
    setFileSize('')
    setUploadTime('')
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  return (
    <div className="photo-uploader">
      {!uploadedPhoto ? (
        <div className="upload-section">
          <label htmlFor="photo-input" className="upload-box">
            <div className="upload-icon">📷</div>
            <p className="upload-title">Upload a Photo</p>
            <p className="upload-subtitle">
              Click to select from camera or gallery
            </p>
            <input
              ref={fileInputRef}
              id="photo-input"
              type="file"
              accept="image/*"
              onChange={handleFileSelect}
              className="hidden-input"
            />
          </label>
        </div>
      ) : (
        <div className="preview-section">
          <div className="preview-container">
            <img src={uploadedPhoto} alt="Uploaded" className="preview-image" />
          </div>

          <div className="file-info">
            <div className="info-item">
              <span className="label">Uploaded At:</span>
              <span className="value">{uploadTime}</span>
            </div>
            <div className="info-item">
              <span className="label">File Size:</span>
              <span className="value">{fileSize}</span>
            </div>
          </div>

          <div className="action-buttons">
            <button 
              onClick={handleUpload} 
              className="btn btn-primary"
              disabled={isSaving}
            >
              {isSaving ? '⏳ Uploading...' : '📤 Upload'}
            </button>
            <button 
              onClick={handleReset} 
              className="btn btn-secondary"
              disabled={isSaving}
            >
              ↻ Choose Another
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default PhotoUploader
