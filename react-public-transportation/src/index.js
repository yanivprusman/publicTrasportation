import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import TransportationMap from './TransportationMap';
import CombinedTransportApp from './CombinedTransportApp';
import reportWebVitals from './reportWebVitals';

// Use the combined application that has both transportation data and map
const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  // Remove StrictMode to prevent double rendering in development
  <CombinedTransportApp />
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
