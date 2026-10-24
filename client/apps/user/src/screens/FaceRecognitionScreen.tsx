import { ActionButton, Flex } from '@seed-design/react'

type FaceRecognitionScreenProps = {
  onRecognize: () => void
}

export const FaceRecognitionScreen = ({ onRecognize }: FaceRecognitionScreenProps) => {
  return (
    <Flex height="full" align="center" justify="center">
      <ActionButton
        type="button"
        size="large"
        variant="brandSolid"
        onClick={onRecognize}
      >
        인식 확인 화면으로
      </ActionButton>
    </Flex>
  )
}
