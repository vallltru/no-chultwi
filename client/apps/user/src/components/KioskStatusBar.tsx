import { useEffect, useState } from 'react'
import { HStack, Text } from '@seed-design/react'
import './KioskStatusBar.css'

const CLASSROOM_NAME = '37-나601'

const seoulDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

const formatCurrentTime = (date: Date) => {
  const parts = Object.fromEntries(
    seoulDateFormatter.formatToParts(date).map(({ type, value }) => [type, value]),
  )

  return `${parts.month}월 ${parts.day}일 (${parts.weekday}) ${parts.hour}:${parts.minute}:${parts.second}`
}

export function KioskStatusBar() {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    let timeoutId: number

    const updateTime = () => {
      setNow(new Date())
      timeoutId = window.setTimeout(updateTime, 1000 - Date.now() % 1000)
    }

    timeoutId = window.setTimeout(updateTime, 1000 - Date.now() % 1000)

    return () => window.clearTimeout(timeoutId)
  }, [])

  return (
    <HStack
      as="header"
      className="kiosk-status-bar"
      align="center"
      justify="space-between"
      gap="x2"
    >
      <Text as="span" textStyle="t4Regular" color="fg.neutralMuted">
        {CLASSROOM_NAME}
      </Text>
      <Text as="span" className="kiosk-status-bar__time" textStyle="t4Regular" color="fg.neutralMuted">
        {formatCurrentTime(now)}
      </Text>
    </HStack>
  )
}
