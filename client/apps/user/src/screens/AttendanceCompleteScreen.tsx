import { ActionButton, Flex } from '@seed-design/react'

type AttendanceCompleteScreenProps = {
  onConfirm: () => void
}

export const AttendanceCompleteScreen = ({ onConfirm }: AttendanceCompleteScreenProps) => {
  return (
    <Flex height="full" align="center" justify="center">
      <ActionButton
        type="button"
        size="large"
        variant="brandSolid"
        onClick={onConfirm}
      >
        확인
      </ActionButton>
    </Flex>
  )
}
