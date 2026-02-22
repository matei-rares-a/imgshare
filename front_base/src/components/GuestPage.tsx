import { useState } from 'react'
import PhotoUploader from './PhotoUploader'
import Gallery from './Gallery'

function GuestPage() {
  const [refreshGallery, setRefreshGallery] = useState(0)

  const handleImageSaved = () => {
    setRefreshGallery((prev) => prev + 1)
  }

  return (
    <>
      <div className="main-content">
        <div className="uploader-section">
          <PhotoUploader onImageSaved={handleImageSaved} />
        </div>
        <div className="gallery-section">
          <Gallery refreshTrigger={refreshGallery} />
        </div>
      </div>
    </>
  )
}

export default GuestPage
