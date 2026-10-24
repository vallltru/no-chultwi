import { useCallback, useRef } from 'react'
import { useFlow, useStack } from '@stackflow/react'

export function useReturnHome() {
  const { pop } = useFlow()
  const { activities } = useStack()
  const returningRef = useRef(false)

  return useCallback(() => {
    if (returningRef.current) return

    const count = activities.filter((activity) => !activity.exitedBy).length - 1
    if (count < 1) return

    returningRef.current = true
    pop(count)
  }, [activities, pop])
}
