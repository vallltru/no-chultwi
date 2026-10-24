import type { RefCallback } from 'react'
import {
  IconCameraFill,
  IconCheckmarkCircleFill,
  IconExclamationmarkCircleFill, IconHouseLine,
} from '@karrotmarket/react-monochrome-icon'
import {ActionButton, Box, HStack, Icon, Text, VStack} from '@seed-design/react'
import type { CameraPreviewStatus } from '../hooks/useCameraPreview'
import type { FaceRecognitionResult } from '../types/faceRecognition'
import './FaceRecognitionScreen.css'

type FaceRecognitionScreenProps = {
  isActive: boolean
  videoRef: RefCallback<HTMLVideoElement>
  cameraStatus: CameraPreviewStatus
  result: FaceRecognitionResult | null
  onRetry: () => void
  onReturnHome: () => void
}

const resultMessages: Record<FaceRecognitionResult, { title: string; description: string }> = {
  success: {
    title: '인식완료!',
    description: '',
  },
  'not-recognized': {
    title: '얼굴을 인식하지 못했어요',
    description: '얼굴이 잘 보이도록 한 뒤 다시 시도해 주세요',
  },
  'camera-denied': {
    title: '카메라 권한이 필요해요',
    description: '브라우저에서 카메라 사용을 허용해 주세요',
  },
  'camera-unavailable': {
    title: '카메라를 확인해 주세요',
    description: '카메라 연결과 다른 앱의 사용 여부를 확인해 주세요',
  },
  'camera-error': {
    title: '카메라를 시작하지 못했어요',
    description: '잠시 후 다시 시도해 주세요',
  },
}

export const FaceRecognitionScreen = ({
  isActive,
  videoRef,
  cameraStatus,
  result,
  onRetry,
  onReturnHome,
}: FaceRecognitionScreenProps) => {
  const resultMessage = result ? resultMessages[result] : null

  return (
    <VStack
      className="face-recognition"
      data-active={isActive}
      data-phase={result ? 'result' : 'scanning'}
      height="full"
      width="full"
      align="center"
      bg="bg.layerDefault"
    >
      <Box className="face-recognition__atmosphere" aria-hidden="true">
        <Box className="face-recognition__wash" />
        <Box className="face-recognition__scan-field">
          <Box className="face-recognition__wave face-recognition__wave--scanning" />
        </Box>
        {result && (
          <Box key={result} className="face-recognition__result-field">
            <Box className="face-recognition__wave face-recognition__wave--result" />
          </Box>
        )}
      </Box>

      <VStack
        className="face-recognition__content"
        align="center"
        gap="x8"
        paddingX="spacingX.globalGutter"
      >
        <Box className="face-recognition__hero">
          <Box
            className="face-recognition__camera"
            data-ready={cameraStatus === 'ready'}
            aria-hidden={!!result}
          >
            <video
              ref={videoRef}
              className="face-recognition__video"
              autoPlay
              muted
              playsInline
              aria-label="얼굴 인식용 카메라 미리보기"
            />
            {cameraStatus !== 'ready' && (
              <VStack
                className="face-recognition__camera-placeholder"
                align="center"
                justify="center"
                gap="x3"
              >
                <Icon svg={<IconCameraFill />} size="x10" color="fg.brand" />
              </VStack>
            )}
          </Box>
          {result && (
            <Box key={result} className="face-recognition__result-icon" aria-hidden="true">
              <Icon
                svg={result === 'success'
                  ? <IconCheckmarkCircleFill />
                  : <IconExclamationmarkCircleFill />}
                size="full"
                color="fg.brand"
              />
            </Box>
          )}
        </Box>

        <VStack
          className="face-recognition__message"
          align="center"
          gap="x6"
        >
          <VStack
            width="full"
            align="center"
            gap="x3"
            aria-live="polite"
            aria-atomic="true"
            aria-busy={!result}
          >
            <Text as="h1" textStyle="t10Bold" color="fg.neutral" className="face-recognition__title">
              <Text as="span" className="face-recognition__scanning-copy" aria-hidden={!!result}>
                얼굴 확인중
                <Box as="span" className="face-recognition__dots" aria-hidden="true">
                  <Box as="span" className="face-recognition__dot">.</Box>
                  <Box as="span" className="face-recognition__dot">.</Box>
                  <Box as="span" className="face-recognition__dot">.</Box>
                </Box>
              </Text>
              {resultMessage && (
                <Text as="span" color="fg.brand" className="face-recognition__result-copy">
                  {resultMessage.title}
                </Text>
              )}
            </Text>
            <Text
              as="p"
              textStyle="t5Regular"
              color="fg.neutralSubtle"
              className="face-recognition__description"
            >
              <Text as="span" className="face-recognition__scanning-copy" aria-hidden={!!result}>
                동그라미 안에 얼굴을 맞춰 주세요
              </Text>
              {resultMessage && (
                <Text as="span" className="face-recognition__result-copy">
                  {resultMessage.description}
                </Text>
              )}
            </Text>
          </VStack>
          {result && result !== 'success' && (
            <HStack gap="spacingX.betweenChips">
              <ActionButton layout="iconOnly" aria-label="홈으로 이동하기" variant="neutralWeak" onClick={onReturnHome}>
                <Icon svg={<IconHouseLine />} />
              </ActionButton>
              <ActionButton type="button" variant="neutralSolid" onClick={onRetry}>
                다시 시도하기
              </ActionButton>
            </HStack>
          )}
        </VStack>
      </VStack>
    </VStack>
  )
}
