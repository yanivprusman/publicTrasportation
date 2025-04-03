// filepath: /home/yaniv/101_coding/publicTransportation/react/public-transportation/src/TestComponent.js
import React from 'react';

const TestComponent = () => {
  return (
    <div style={{ 
      position: 'fixed', 
      top: '50%', 
      left: '50%', 
      transform: 'translate(-50%, -50%)', 
      backgroundColor: 'red',
      color: 'white',
      padding: '20px',
      zIndex: 10000,
      fontSize: '24px',
      fontWeight: 'bold',
      borderRadius: '10px'
    }}>
      THIS IS A TEST COMPONENT
    </div>
  );
};

export default TestComponent;