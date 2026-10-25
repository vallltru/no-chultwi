import { IconChevronRightFill } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, SuffixIcon, Text, VStack } from '@seed-design/react'
import { KioskStatusBar } from '../components/KioskStatusBar'
import './HomeScreen.css'

type HomeScreenProps = {
  isActive: boolean
  onStart: () => void
}

export const HomeScreen = ({ isActive, onStart }: HomeScreenProps) => {
  return (
    <VStack height="full" justify="space-between">
      <KioskStatusBar />
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
        className="home-start-button"
        data-attention={isActive}
        type="button"
        size="large"
        variant="brandSolid"
        onClick={onStart}
      >
        <Box as="span" className="home-start-button__ripple" aria-hidden="true" />
        <Box as="span" className="home-start-button__label">
          얼굴로 출석하기
        </Box>
        <SuffixIcon svg={<IconChevronRightFill />} />
      </ActionButton>
    </VStack>
  )
}
