#!/bin/bash

# Create the scripts directory if it doesn't exist
mkdir -p /home/yaniv/101_coding/publicTransportation/scripts

# Copy the PHP script if it doesn't exist
if [ ! -f "/home/yaniv/101_coding/publicTransportation/scripts/line-stops.php" ]; then
  echo "Creating line-stops.php script..."
  cat > "/home/yaniv/101_coding/publicTransportation/scripts/line-stops.php" << 'EOF'
<?php
// PHP code is placed here by another script
EOF
fi

# Create a symbolic link in the web directory
ln -sf /home/yaniv/101_coding/publicTransportation/scripts/line-stops.php /home/yaniv/1Iz3UBgvtNDVfVo/line-stops.php

echo "Setup complete! Access the stops viewer at: http://localhost/line-stops.php?line=60"
