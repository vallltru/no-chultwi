import { seedPlugin } from '@seed-design/stackflow'
import { basicRendererPlugin } from '@stackflow/plugin-renderer-basic'
import { stackflow } from '@stackflow/react'
import { AttendanceCompleteActivity } from './activities/AttendanceCompleteActivity'
import { FaceRecognitionActivity } from './activities/FaceRecognitionActivity'
import { GuideActivity } from './activities/GuideActivity'
import { HomeActivity } from './activities/HomeActivity'
import { RecognitionConfirmationActivity } from './activities/RecognitionConfirmationActivity'
import { config } from './stackflow.config'

export const { Stack } = stackflow({
  config,
  components: {
    Home: HomeActivity,
    FaceRecognition: FaceRecognitionActivity,
    RecognitionConfirmation: RecognitionConfirmationActivity,
    AttendanceComplete: AttendanceCompleteActivity,
    Guide: GuideActivity,
  },
  plugins: [basicRendererPlugin(), seedPlugin({ theme: 'android' })],
})
