import { useFlow, type ActivityComponentType } from '@stackflow/react'
import { FaceRecognitionScreen } from '../screens/FaceRecognitionScreen'
import { KioskActivityFrame } from './KioskActivityFrame'

export const FaceRecognitionActivity: ActivityComponentType<'FaceRecognition'> =
  () => {
    const { push } = useFlow()

    return (
      <KioskActivityFrame>
        <FaceRecognitionScreen
          onRecognize={() => push('RecognitionConfirmation', {})}
        />
      </KioskActivityFrame>
    )
  }
