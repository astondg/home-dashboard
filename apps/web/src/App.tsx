import { useState } from 'react'
import './App.css'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { Home } from './components/home/Home'
import { MsalProvider } from '@azure/msal-react'
import ProvideAppContext from './hooks/useAppContext'
import { IPublicClientApplication } from '@azure/msal-browser'

function App({
  pca
}: {
  pca: IPublicClientApplication
}) {
  const [count, setCount] = useState(0)

  return (
    <MsalProvider instance={ pca }>
      <ProvideAppContext>
        <div className="App h-full">
          <Home />
        </div>
      </ProvideAppContext>
    </MsalProvider>
  )
}

export default App
