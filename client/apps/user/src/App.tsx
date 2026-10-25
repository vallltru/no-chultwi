import { Stack } from './stackflow'
import './App.css'

function App() {
  return (
    <main className="app" onContextMenu={(event) => event.preventDefault()}>
      <Stack />
    </main>
  )
}

export default App
