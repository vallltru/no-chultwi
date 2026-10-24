import { VStack } from '@seed-design/react'
import { AppScreen } from '@seed-design/stackflow'
import type { ReactNode } from 'react'

interface KioskActivityFrameProps {
  children: ReactNode
  lightOnly?: boolean
  edgeToEdge?: boolean
}

export function KioskActivityFrame({
  children,
  lightOnly = false,
  edgeToEdge = false,
}: KioskActivityFrameProps) {
  return (
    <AppScreen.Root
      transitionStyle="fadeIn"
      layerOffsetTop="none"
      data-seed-color-mode={lightOnly ? 'light-only' : undefined}
    >
      <AppScreen.Dim />
      <AppScreen.Layer>
        <VStack
          height="full"
          paddingX={edgeToEdge ? 0 : 'x5'}
          paddingY={edgeToEdge ? 0 : 'x5'}
          style={{ boxSizing: 'border-box' }}
        >
          {children}
        </VStack>
      </AppScreen.Layer>
    </AppScreen.Root>
  )
}
