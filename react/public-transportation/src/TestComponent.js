import React from 'react';

const TestComponent = () => {
  return (
    <div style={{ 
      position: 'fixed', 
      top: 0, 
      left: 0, 
      right: 0,
      backgroundColor: 'red',
      color: 'white',
      padding: '20px',
      zIndex: 9999,
      fontSize: '24px',
      fontWeight: 'bold',
      textAlign: 'center'
    }}>
      TEST COMPONENT - THIS SHOULD BE VISIBLE
    </div>
  );
};

export default TestComponent;
