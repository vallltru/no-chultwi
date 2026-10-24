import { ActionButton, Flex } from '@seed-design/react'

type RecognitionConfirmationScreenProps = {
  onConfirm: () => void
}

export const RecognitionConfirmationScreen = ({
  onConfirm,
}: RecognitionConfirmationScreenProps) => {
  return (
    <Flex height="full" align="center" justify="center">
      <ActionButton
        type="button"
        size="large"
        variant="brandSolid"
        onClick={onConfirm}
      >
        완료 화면으로
      </ActionButton>
    </Flex>
  )
}
