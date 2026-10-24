import { useCallback, useEffect, useRef, useState } from 'react'
import { useActivity, useFlow, type ActivityComponentType } from '@stackflow/react'
import { useCameraPreview } from '../hooks/useCameraPreview'
import { FaceRecognitionScreen } from '../screens/FaceRecognitionScreen'
import type { FaceRecognitionResult } from '../types/faceRecognition'
import { KioskActivityFrame } from './KioskActivityFrame'
import { useReturnHome } from './useReturnHome'

const ENABLE_MOCK_RECOGNITION = true
const MOCK_RECOGNITION_RESULT: Extract<
  FaceRecognitionResult,
  'success' | 'not-recognized'
> = 'success'
const RESULT_DISPLAY_DURATION_MS = 1600

export const FaceRecognitionActivity: ActivityComponentType<'FaceRecognition'> =
  () => {
    const { isTop, transitionState } = useActivity()
    const { push } = useFlow()
    const returnHome = useReturnHome()
    const isActive = isTop && transitionState === 'enter-done'
    const { videoRef, status: cameraStatus, retry: retryCamera } = useCameraPreview(isActive)
    const [result, setResult] = useState<FaceRecognitionResult | null>(null)
    const resultRef = useRef<FaceRecognitionResult | null>(null)
    const navigatedRef = useRef(false)

    useEffect(() => {
      if (!isActive) return

      resultRef.current = null
      navigatedRef.current = false
      setResult(null)
    }, [isActive])

    const finishRecognition = useCallback((nextResult: FaceRecognitionResult) => {
      if (resultRef.current !== null) return

      resultRef.current = nextResult
      setResult(nextResult)
    }, [])

    const retryRecognition = useCallback(() => {
      resultRef.current = null
      navigatedRef.current = false
      setResult(null)
      retryCamera()
    }, [retryCamera])

    const goToConfirmation = useCallback(() => {
      if (navigatedRef.current) return

      navigatedRef.current = true
      push('RecognitionConfirmation', {})
    }, [push])

    useEffect(() => {
      if (!isActive) return

      if (cameraStatus === 'denied') finishRecognition('camera-denied')
      if (cameraStatus === 'unavailable') finishRecognition('camera-unavailable')
      if (cameraStatus === 'error') finishRecognition('camera-error')
    }, [cameraStatus, finishRecognition, isActive])

    useEffect(() => {
      if (!ENABLE_MOCK_RECOGNITION || !isActive || cameraStatus !== 'ready' || result) {
        return
      }

      const timer = window.setTimeout(() => {
        finishRecognition(MOCK_RECOGNITION_RESULT)
      }, 3_000)

      return () => window.clearTimeout(timer)
    }, [cameraStatus, finishRecognition, isActive, result])

    useEffect(() => {
      if (
        !isActive ||
        result !== 'success' ||
        resultRef.current !== result ||
        navigatedRef.current
      ) {
        return
      }

      const timer = window.setTimeout(() => {
        goToConfirmation()
      }, RESULT_DISPLAY_DURATION_MS)

      return () => window.clearTimeout(timer)
    }, [goToConfirmation, isActive, result])

    return (
      <KioskActivityFrame edgeToEdge lightOnly>
        <FaceRecognitionScreen
          isActive={isActive}
          videoRef={videoRef}
          cameraStatus={cameraStatus}
          result={result}
          onRetry={retryRecognition}
          onReturnHome={returnHome}
          onForceNext={goToConfirmation}
        />
      </KioskActivityFrame>
    )
  }
