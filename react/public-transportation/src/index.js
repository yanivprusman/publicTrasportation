import React from 'react';
import ReactDOM from 'react-dom/client';

// Create an extremely simple root element
const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <div style={{
    backgroundColor: 'red',
    color: 'white',
    padding: '30px',
    fontSize: '30px',
    fontWeight: 'bold',
    textAlign: 'center',
    height: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center'
  }}>
    TESTING CHANGES - THIS SHOULD BE VISIBLE
  </div>
);
