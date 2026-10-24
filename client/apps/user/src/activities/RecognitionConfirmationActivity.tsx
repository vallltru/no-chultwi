import { useFlow, type ActivityComponentType } from '@stackflow/react'
import { RecognitionConfirmationScreen } from '../screens/RecognitionConfirmationScreen'
import { KioskActivityFrame } from './KioskActivityFrame'

const MOCK_STUDENT = {
  name: '김민준',
  department: '컴퓨터과학부',
  admissionYear: '26학번',
}

export const RecognitionConfirmationActivity: ActivityComponentType<'RecognitionConfirmation'> =
  () => {
    const { pop, push } = useFlow()

    return (
      <KioskActivityFrame lightOnly>
        <RecognitionConfirmationScreen
          student={MOCK_STUDENT}
          onReject={() => pop()}
          onConfirm={() => push('AttendanceComplete', {})}
        />
      </KioskActivityFrame>
    )
  }
