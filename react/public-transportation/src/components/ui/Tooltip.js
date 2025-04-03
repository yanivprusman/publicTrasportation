import React, { useState } from 'react';
import PropTypes from 'prop-types';
import './Tooltip.css';

const Tooltip = ({ text, children }) => {
  const [show, setShow] = useState(false);

  return (
    <div className="tooltip-container" 
      onMouseEnter={() => setShow(true)}
      onMouseLeave={() => setShow(false)}>
      {children}
      {show && <div className="tooltip-text">{text}</div>}
    </div>
  );
};

Tooltip.propTypes = {
  text: PropTypes.string.isRequired,
  children: PropTypes.node.isRequired,
};

export default Tooltip;
