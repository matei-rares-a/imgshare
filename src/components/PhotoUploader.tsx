import { useState, useRef } from 'react'
import { generateRandomFileName } from '../utils/fileUtils'
import { saveImage, generateImageId } from '../utils/imageStorage'
import '../styles/PhotoUploader.css'

interface PhotoUploaderProps {
  onImageSaved?: () => void
}

function PhotoUploader({ onImageSaved }: PhotoUploaderProps) {
  const [uploadedPhoto, setUploadedPhoto] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string>('')
  const [fileSize, setFileSize] = useState<string>('')
  const [originalFileName, setOriginalFileName] = useState<string>('')
  const [uploadTime, setUploadTime] = useState<string>('')
  const [isSaving, setIsSaving] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      // Validate file is an image
      if (!file.type.startsWith('image/')) {
        alert('Please select an image file')
        return
      }

      // Store original file name
      setOriginalFileName(file.name)

      // Generate random filename
      const randomFileName = generateRandomFileName(file.type)
      setFileName(randomFileName)

      // Store file size
      const sizeInKB = (file.size / 1024).toFixed(2)
      setFileSize(`${sizeInKB} KB`)

      // Set current upload time
      const now = new Date()
      setUploadTime(now.toLocaleString())

      // Create preview
      const reader = new FileReader()
      reader.onload = (e) => {
        setUploadedPhoto(e.target?.result as string)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleUpload = async () => {
    if (uploadedPhoto && fileName) {
      setIsSaving(true)
      try {
        // Save to localStorage
        saveImage({
          id: generateImageId(),
          dataUrl: uploadedPhoto,
          fileName: fileName,
          originalFileName: originalFileName,
          uploadedAt: Date.now(),
          fileSize: fileSize,
        })

        // Notify parent component
        onImageSaved?.()

        // Reset after successful save
        setTimeout(() => {
          handleReset()
          setIsSaving(false)
        }, 500)
      } catch (error) {
        console.error('Error saving image:', error)
        alert('Error saving image. Please try again.')
        setIsSaving(false)
      }
    }
  }

  const handleReset = () => {
    setUploadedPhoto(null)
    setFileName('')
    setFileSize('')
    setOriginalFileName('')
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
              capture="environment"
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
