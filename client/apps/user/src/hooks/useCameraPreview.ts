import { useCallback, useEffect, useState } from 'react'

export type CameraPreviewStatus =
  | 'idle'
  | 'starting'
  | 'ready'
  | 'denied'
  | 'unavailable'
  | 'error'

function cameraErrorStatus(error: unknown): CameraPreviewStatus {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
      return 'denied'
    }

    if (
      error.name === 'NotFoundError' ||
      error.name === 'NotReadableError' ||
      error.name === 'OverconstrainedError'
    ) {
      return 'unavailable'
    }
  }

  return 'error'
}

export function useCameraPreview(enabled: boolean) {
  const [videoElement, setVideoElement] = useState<HTMLVideoElement | null>(null)
  const [status, setStatus] = useState<CameraPreviewStatus>('starting')
  const [request, setRequest] = useState(0)

  const videoRef = useCallback((element: HTMLVideoElement | null) => {
    setVideoElement(element)
  }, [])

  const retry = useCallback(() => {
    setStatus('starting')
    setRequest((value) => value + 1)
  }, [])

  useEffect(() => {
    if (!enabled || !videoElement) return
    const video = videoElement

    let cancelled = false
    let stream: MediaStream | null = null

    const stopPreview = () => {
      if (!stream) return

      for (const track of stream.getTracks()) {
        track.removeEventListener('ended', handleEnded)
        track.stop()
      }

      if (video.srcObject === stream) {
        video.pause()
        video.srcObject = null
      }

      stream = null
    }

    function handleEnded() {
      if (cancelled) return
      stopPreview()
      setStatus('unavailable')
    }

    async function startPreview() {
      // Let StrictMode finish its initial cleanup before requesting permission.
      await Promise.resolve()
      if (cancelled) return
      setStatus('starting')

      if (!navigator.mediaDevices?.getUserMedia) {
        setStatus('unavailable')
        return
      }

      let mediaStream: MediaStream

      try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: 'user' },
        })
      } catch (error) {
        if (!cancelled) setStatus(cameraErrorStatus(error))
        return
      }

      // Permission can be granted after the activity has already been left.
      if (cancelled) {
        mediaStream.getTracks().forEach((track) => track.stop())
        return
      }

      stream = mediaStream
      stream.getTracks().forEach((track) => {
        track.addEventListener('ended', handleEnded)
      })

      video.muted = true
      video.playsInline = true
      video.autoplay = true
      video.srcObject = stream

      try {
        await video.play()
        if (!cancelled && stream === mediaStream) setStatus('ready')
      } catch {
        if (!cancelled && stream === mediaStream) {
          stopPreview()
          setStatus('error')
        }
      }
    }

    void startPreview()

    return () => {
      cancelled = true
      stopPreview()
      setStatus('starting')
    }
  }, [enabled, request, videoElement])

  return { videoRef, status: enabled ? status : 'idle', retry } as const
}
