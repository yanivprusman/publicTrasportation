#!/bin/bash

echo "Fixing access to find-line60-route.php..."

# Ensure scripts directory exists
mkdir -p /home/yaniv/101_coding/publicTransportation/scripts

# Check if the route finder script exists
if [ ! -f "/home/yaniv/101_coding/publicTransportation/scripts/find-line60-route.php" ]; then
  echo "Error: Source file not found. Cannot create symlink."
  echo "Please ensure the file exists at /home/yaniv/101_coding/publicTransportation/scripts/find-line60-route.php"
else
  # Create symbolic link directly in the web root
  ln -sf /home/yaniv/101_coding/publicTransportation/scripts/find-line60-route.php /home/yaniv/1Iz3UBgvtNDVfVo/find-line60-route.php

  # Set proper permissions
  chmod 644 /home/yaniv/101_coding/publicTransportation/scripts/find-line60-route.php
  chmod 755 /home/yaniv/101_coding/publicTransportation/scripts

  echo "Symlink created and permissions set."
fi

# Test if PHP can access the file
php -l /home/yaniv/101_coding/publicTransportation/scripts/find-line60-route.php
if [ $? -eq 0 ]; then
  echo "PHP syntax check passed."
else
  echo "PHP syntax check failed. Please check the file for errors."
fi

echo "Access should now be fixed. Try accessing http://localhost/find-line60-route.php"
