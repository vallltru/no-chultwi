import { IconChevronRightFill } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, SuffixIcon, Text, VStack } from '@seed-design/react'

type HomeScreenProps = {
  onStart: () => void
}

export const HomeScreen = ({ onStart }: HomeScreenProps) => {
  return (
    <VStack height="full" justify="space-between">
      <Box flexGrow={1} />

      <VStack
          align="flex-start"
          paddingX="spacingX.globalGutter"
          gap="spacingY.componentDefault"
      >
        <VStack as="h1" m={0} align="flex-start">
          <Text as="span" textStyle="t14Bold" color="fg.neutral">
            여기서
          </Text>
          <Text as="span" textStyle="t14Bold" color="fg.brand">
            전자출결
          </Text>
          <Text as="span" textStyle="t14Bold" color="fg.neutral">
            해주세요
          </Text>
        </VStack>

        <Text as="p" textStyle="t8Regular" color="fg.neutralSubtle">
          창의공학기초설계(30)
        </Text>
      </VStack>

      <Box flexGrow={2} />

      <ActionButton
        type="button"
        size="large"
        variant="brandSolid"
        onClick={onStart}
      >
        얼굴로 출석하기
        <SuffixIcon svg={<IconChevronRightFill />} />
      </ActionButton>
    </VStack>
  )
}
