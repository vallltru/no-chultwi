import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@seed-design/css/base.css'
import '@seed-design/css/recipes/app-screen.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
