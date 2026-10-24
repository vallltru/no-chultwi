import { useEffect } from 'react'
import { AppScreen } from '@seed-design/stackflow'
import { Flex } from '@seed-design/react'
import { useActivity, type ActivityComponentType } from '@stackflow/react'
import { ActionButton } from 'seed-design/ui/action-button'
import { useReturnHome } from './useReturnHome'

export const AttendanceCompleteScreen: ActivityComponentType<'AttendanceComplete'> = () => {
  const { isTop, transitionState } = useActivity()
  const returnHome = useReturnHome()

  useEffect(() => {
    if (!isTop || transitionState !== 'enter-done') return

    const timer = window.setTimeout(returnHome, 3000)
    return () => window.clearTimeout(timer)
  }, [isTop, transitionState, returnHome])

  return (
    <AppScreen.Root transitionStyle="fadeIn">
      <AppScreen.Dim />
      <AppScreen.Layer>
        <Flex height="full" align="center" justify="center">
          <ActionButton
            type="button"
            size="large"
            variant="brandSolid"
            onClick={returnHome}
          >
            확인
          </ActionButton>
        </Flex>
      </AppScreen.Layer>
    </AppScreen.Root>
  )
}
