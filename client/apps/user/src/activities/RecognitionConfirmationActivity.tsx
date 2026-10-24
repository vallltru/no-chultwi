import { useFlow, type ActivityComponentType } from '@stackflow/react'
import { RecognitionConfirmationScreen } from '../screens/RecognitionConfirmationScreen'
import { KioskActivityFrame } from './KioskActivityFrame'

export const RecognitionConfirmationActivity: ActivityComponentType<'RecognitionConfirmation'> =
  () => {
    const { push } = useFlow()

    return (
      <KioskActivityFrame>
        <RecognitionConfirmationScreen
          onConfirm={() => push('AttendanceComplete', {})}
        />
      </KioskActivityFrame>
    )
  }
