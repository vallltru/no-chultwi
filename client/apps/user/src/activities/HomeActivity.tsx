import { useActivity, useFlow, type ActivityComponentType } from '@stackflow/react'
import { HomeScreen } from '../screens/HomeScreen'
import { KioskActivityFrame } from './KioskActivityFrame'

export const HomeActivity: ActivityComponentType<'Home'> = () => {
  const { isTop, transitionState } = useActivity()
  const { push } = useFlow()

  return (
    <KioskActivityFrame lightOnly>
      <HomeScreen
        isActive={isTop && transitionState === 'enter-done'}
        onStart={() => push('FaceRecognition', {})}
      />
    </KioskActivityFrame>
  )
}
