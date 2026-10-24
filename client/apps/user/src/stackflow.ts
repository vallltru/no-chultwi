import { seedPlugin } from '@seed-design/stackflow'
import { basicRendererPlugin } from '@stackflow/plugin-renderer-basic'
import { stackflow } from '@stackflow/react'
import { AttendanceCompleteScreen } from './screens/AttendanceCompleteScreen'
import { FaceRecognitionScreen } from './screens/FaceRecognitionScreen'
import { GuideScreen } from './screens/GuideScreen'
import { HomeScreen } from './screens/HomeScreen'
import { RecognitionConfirmationScreen } from './screens/RecognitionConfirmationScreen'
import { config } from './stackflow.config'

export const { Stack } = stackflow({
  config,
  components: {
    Home: HomeScreen,
    FaceRecognition: FaceRecognitionScreen,
    RecognitionConfirmation: RecognitionConfirmationScreen,
    AttendanceComplete: AttendanceCompleteScreen,
    Guide: GuideScreen,
  },
  plugins: [basicRendererPlugin(), seedPlugin({ theme: 'android' })],
})
