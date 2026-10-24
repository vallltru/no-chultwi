import { defineConfig } from '@stackflow/config'

declare module '@stackflow/config' {
  interface Register {
    Home: Record<string, never>
    FaceRecognition: Record<string, never>
    RecognitionConfirmation: Record<string, never>
    AttendanceComplete: Record<string, never>
    Guide: Record<string, never>
  }
}

export const config = defineConfig({
  activities: [
    { name: 'Home' },
    { name: 'FaceRecognition' },
    { name: 'RecognitionConfirmation' },
    { name: 'AttendanceComplete' },
    { name: 'Guide' },
  ],
  initialActivity: () => 'Home',
  transitionDuration: 300,
})
