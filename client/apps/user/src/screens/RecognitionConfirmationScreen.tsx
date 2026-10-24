import { ActionButton, Avatar, Box, HStack, IdentityPlaceholder, Text, VStack } from '@seed-design/react'

type RecognitionConfirmationScreenProps = {
  student: {
    name: string
    department: string
    admissionYear: string
  }
  onReject: () => void
  onConfirm: () => void
}

export const RecognitionConfirmationScreen = ({
  student,
  onReject,
  onConfirm,
}: RecognitionConfirmationScreenProps) => {
  return (
    <VStack height="full" width="full">
      <Box flexGrow={1} />

      <VStack width="full" align="center" gap="x6">
        <Text as="h1" textStyle="screenTitle" color="fg.neutral" align="center">
          {student.name} 학생으로<br />출결 확인 할까요?
        </Text>

        <HStack
          as="section"
          aria-label="인식된 학생 정보"
          alignSelf="stretch"
          align="center"
          gap="x5"
          marginX="x2"
          padding="x6"
          bg="bg.neutralWeak"
          borderRadius="r4"
        >
          <Avatar.Root size="64" aria-hidden="true" style={{ flexShrink: 0 }}>
            <Avatar.Fallback>
              <IdentityPlaceholder.Root identity="person">
                <IdentityPlaceholder.Image />
              </IdentityPlaceholder.Root>
            </Avatar.Fallback>
          </Avatar.Root>
          <VStack gap="x1">
            <Text textStyle="t7Bold" color="fg.neutral">
              {student.name}
            </Text>
            <Text textStyle="t5Regular" color="fg.neutral">
              {student.department} {student.admissionYear}
            </Text>
          </VStack>
        </HStack>
      </VStack>

      <Box flexGrow={4} />

      <HStack width="full" gap="x3">
        <ActionButton type="button" size="large" variant="neutralWeak" style={{ flex: 1 }} onClick={onReject}>
          제가 아니에요
        </ActionButton>
        <ActionButton type="button" size="large" variant="brandSolid" style={{ flex: 1 }} onClick={onConfirm}>
          전자출결
        </ActionButton>
      </HStack>
    </VStack>
  )
}
