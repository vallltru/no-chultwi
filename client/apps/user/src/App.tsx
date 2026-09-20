import { Text, VStack } from '@seed-design/react'
import { ActionButton } from 'seed-design/ui/action-button'
import './App.css'

function App() {
  return (
    <main className="app">
      <VStack className="app__content" align="center" gap="x4">
        <VStack align="center" gap="x2">
          <Text as="h1" textStyle="t7Bold" color="fg.neutral">
            No Chultwi
          </Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            기본 설정 끝~~
          </Text>
        </VStack>
        <ActionButton size="large" variant="brandSolid">
          시작하기
        </ActionButton>
      </VStack>
    </main>
  )
}

export default App
