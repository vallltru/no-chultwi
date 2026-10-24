import { AppScreen } from '@seed-design/stackflow'
import { Flex } from '@seed-design/react'
import { useFlow, type ActivityComponentType } from '@stackflow/react'
import { ActionButton } from 'seed-design/ui/action-button'

export const RecognitionConfirmationScreen: ActivityComponentType<'RecognitionConfirmation'> =
  () => {
    const { push } = useFlow()

    return (
      <AppScreen.Root transitionStyle="fadeIn">
        <AppScreen.Dim />
        <AppScreen.Layer>
          <Flex height="full" align="center" justify="center">
            <ActionButton
              type="button"
              size="large"
              variant="brandSolid"
              onClick={() => push('AttendanceComplete', {})}
            >
              완료 화면으로
            </ActionButton>
          </Flex>
        </AppScreen.Layer>
      </AppScreen.Root>
    )
  }
