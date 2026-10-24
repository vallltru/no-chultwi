import { useEffect, useRef, useState } from 'react'
import { useActivity, type ActivityComponentType } from '@stackflow/react'
import { AttendanceCompleteScreen } from '../screens/AttendanceCompleteScreen'
import { KioskActivityFrame } from './KioskActivityFrame'
import { useReturnHome } from './useReturnHome'

const AUTO_RETURN_DELAY_MS = 5000

const formatCompletionTime = (date: Date) =>
    `${date.getHours()}시 ${String(date.getMinutes()).padStart(2, '0')}분`

export const AttendanceCompleteActivity: ActivityComponentType<'AttendanceComplete'> =
    () => {
        const { isTop, transitionState } = useActivity()
        const returnHome = useReturnHome()
        const returnHomeRef = useRef(returnHome)
        const [secondsRemaining, setSecondsRemaining] = useState(5)
        const [completedTimeLabel, setCompletedTimeLabel] = useState(() => formatCompletionTime(new Date()))
        const isActive = isTop && transitionState === 'enter-done'

        useEffect(() => {
            returnHomeRef.current = returnHome
        }, [returnHome])

        useEffect(() => {
            if (!isActive) return

            setSecondsRemaining(5)
            setCompletedTimeLabel(formatCompletionTime(new Date()))

            const secondTimer = window.setTimeout(() => setSecondsRemaining(4), 1000)
            const thirdTimer = window.setTimeout(() => setSecondsRemaining(3), 2000)
            const fourthTimer = window.setTimeout(() => setSecondsRemaining(2), 3000)
            const fifthTimer = window.setTimeout(() => setSecondsRemaining(1), 4000)
            const returnTimer = window.setTimeout(() => returnHomeRef.current(), AUTO_RETURN_DELAY_MS)

            return () => {
                window.clearTimeout(secondTimer)
                window.clearTimeout(thirdTimer)
                window.clearTimeout(fourthTimer)
                window.clearTimeout(fifthTimer)
                window.clearTimeout(returnTimer)
            }
        }, [isActive])

        return (
            <KioskActivityFrame lightOnly>
                <AttendanceCompleteScreen
                    completedTimeLabel={completedTimeLabel}
                    secondsRemaining={secondsRemaining}
                    onConfirm={returnHome}
                />
            </KioskActivityFrame>
        )
    }
