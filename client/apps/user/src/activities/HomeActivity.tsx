import { useFlow, type ActivityComponentType } from '@stackflow/react'
import { HomeScreen } from '../screens/HomeScreen'
import { KioskActivityFrame } from './KioskActivityFrame'

export const HomeActivity: ActivityComponentType<'Home'> = () => {
  const { push } = useFlow()

  return (
    <KioskActivityFrame lightOnly>
      <HomeScreen onStart={() => push('FaceRecognition', {})} />
    </KioskActivityFrame>
  )
}
