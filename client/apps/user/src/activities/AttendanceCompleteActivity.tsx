import { useEffect } from 'react'
import { useActivity, type ActivityComponentType } from '@stackflow/react'
import { AttendanceCompleteScreen } from '../screens/AttendanceCompleteScreen'
import { KioskActivityFrame } from './KioskActivityFrame'
import { useReturnHome } from './useReturnHome'

export const AttendanceCompleteActivity: ActivityComponentType<'AttendanceComplete'> =
  () => {
    const { isTop, transitionState } = useActivity()
    const returnHome = useReturnHome()

    useEffect(() => {
      if (!isTop || transitionState !== 'enter-done') return

      const timer = window.setTimeout(returnHome, 3000)
      return () => window.clearTimeout(timer)
    }, [isTop, transitionState, returnHome])

    return (
      <KioskActivityFrame>
        <AttendanceCompleteScreen onConfirm={returnHome} />
      </KioskActivityFrame>
    )
  }
