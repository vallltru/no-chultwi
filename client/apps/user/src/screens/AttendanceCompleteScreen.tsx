import { IconCheckmarkCircleFill } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, Icon, Text, VStack } from '@seed-design/react'
import './AttendanceCompleteScreen.css'

type AttendanceCompleteScreenProps = {
  completedTimeLabel: string
  secondsRemaining: number
  onConfirm: () => void
}

export const AttendanceCompleteScreen = ({
  completedTimeLabel,
  secondsRemaining,
  onConfirm,
}: AttendanceCompleteScreenProps) => {
  return (
    <VStack className="attendance-complete" height="full" width="full">
      <Box flexGrow={2} />

      <VStack width="full" align="center" gap="x8">
        <Box className="attendance-complete__icon" width="80px" height="80px" aria-hidden="true">
          <Box className="attendance-complete__checkmark" width="full" height="full">
            <Icon svg={<IconCheckmarkCircleFill />} size="full" color="fg.brand" />
          </Box>
        </Box>
        <VStack align="center" gap="x2">
          <Text as="h1" textStyle="t12Bold" color="fg.neutral" align="center">
            출결 확인 완료
          </Text>
          <Text as="p" textStyle="t6Regular" color="fg.neutralSubtle" align="center">
            {completedTimeLabel}으로 출결 확인을 완료했어요
          </Text>
        </VStack>
      </VStack>

      <Box flexGrow={3} />

      <VStack width="full" gap="x4">
        <Text as="p" textStyle="t5Regular" color="fg.neutralSubtle" align="center" aria-live="polite">
          {secondsRemaining}초 뒤에 닫혀요
        </Text>
        <ActionButton type="button" size="large" variant="brandSolid" onClick={onConfirm}>
          확인
        </ActionButton>
      </VStack>
    </VStack>
  )
}
