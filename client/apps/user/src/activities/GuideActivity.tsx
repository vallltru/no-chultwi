import type { ActivityComponentType } from '@stackflow/react'
import { GuideScreen } from '../screens/GuideScreen'
import { KioskActivityFrame } from './KioskActivityFrame'
import { useReturnHome } from './useReturnHome'

export const GuideActivity: ActivityComponentType<'Guide'> = () => {
  const returnHome = useReturnHome()

  return (
    <KioskActivityFrame>
      <GuideScreen onConfirm={returnHome} />
    </KioskActivityFrame>
  )
}
