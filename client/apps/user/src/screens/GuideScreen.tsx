import { ActionButton, Flex } from '@seed-design/react'

type GuideScreenProps = {
  onConfirm: () => void
}

export const GuideScreen = ({ onConfirm }: GuideScreenProps) => {
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
