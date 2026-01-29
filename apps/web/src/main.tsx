import {
  PublicClientApplication,
  EventMessage,
  EventType,
  AuthenticationResult
} from '@azure/msal-browser';
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import config from './config';
import './index.css'

const msalInstance = new PublicClientApplication({
  auth: {
    clientId: config.appId,
    redirectUri: config.redirectUri
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: true
  }
});

const accounts = msalInstance.getAllAccounts();
if (accounts && accounts.length > 0) {
  msalInstance.setActiveAccount(accounts[0]);
}

msalInstance.addEventCallback((event: EventMessage) => {
  if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
    // Set the active account - this simplifies token acquisition
    const authResult = event.payload as AuthenticationResult;
    msalInstance.setActiveAccount(authResult.account);
  }
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App pca={msalInstance} />
  </React.StrictMode>
)
