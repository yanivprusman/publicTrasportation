#!/bin/bash

# Create API directory if it doesn't exist
mkdir -p /home/yaniv/1Iz3UBgvtNDVfVo/api

# Create symbolic link for the diagnostics script
ln -sf /home/yaniv/101_coding/publicTransportation/api/api-diagnostics.php /home/yaniv/1Iz3UBgvtNDVfVo/api/api-diagnostics.php

echo "Created diagnostics tool link."
echo "Access the API diagnostics at: http://localhost/api/api-diagnostics.php"
