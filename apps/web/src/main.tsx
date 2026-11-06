import React from 'react';
import ReactDOM from 'react-dom/client';
import '@fontsource-variable/manrope';
import '@fontsource-variable/space-grotesk';
import './styles/tokens.css';
import './styles/base.css';
import './styles/components.css';
import './styles/layout.css';
import './styles/pages.css';
import './styles/detail.css';
import './styles/planner.css';
import './styles/auth.css';
import './styles/settings.css';
import './styles/onboarding.css';
import './styles/refine.css';
import './styles/responsive.css';
import './styles/polish.css';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js').catch(() => {
      // Offline caching is an enhancement; the app still runs without it.
    });
  });
}
