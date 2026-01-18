#!/bin/bash

# This script will remove the now-redundant individual setup scripts
# since their functionality has been combined into setup-transport.sh

echo "Creating backup directory for old scripts..."
mkdir -p /home/yaniv/101_coding/publicTransportation/old_scripts

echo "Moving old scripts to backup directory..."
mv /home/yaniv/101_coding/publicTransportation/setup-api.sh \
   /home/yaniv/101_coding/publicTransportation/setup-api-directory.sh \
   /home/yaniv/101_coding/publicTransportation/update-gtfs.sh \
   /home/yaniv/101_coding/publicTransportation/setup-symlinks.sh \
   /home/yaniv/101_coding/publicTransportation/old_scripts/ 2>/dev/null || true

echo "Scripts have been moved to /home/yaniv/101_coding/publicTransportation/old_scripts/"
echo "You can now use the unified script: ./setup-transport.sh"
