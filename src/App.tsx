import { useState } from 'react'
import logo from './logo.svg'
import './App.css'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { Home } from './components/home/Home'

function App() {
  const [count, setCount] = useState(0)

  return (
    <div className="App h-full">
      <Home />
    </div>
  )
}

export default App
