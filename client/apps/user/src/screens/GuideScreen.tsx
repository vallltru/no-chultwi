import { AppScreen } from '@seed-design/stackflow'
import { Flex } from '@seed-design/react'
import type { ActivityComponentType } from '@stackflow/react'
import { ActionButton } from 'seed-design/ui/action-button'
import { useReturnHome } from './useReturnHome'

export const GuideScreen: ActivityComponentType<'Guide'> = () => {
  const returnHome = useReturnHome()

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
