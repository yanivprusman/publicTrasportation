#!/bin/bash

# Set correct permissions for PHP files
chmod 644 /home/yaniv/101_coding/publicTransportation/api/*.php
chmod 755 /home/yaniv/101_coding/publicTransportation/api
chmod 755 /home/yaniv/1Iz3UBgvtNDVfVo/api

# Fix data directory permissions
chmod -R 755 /home/yaniv/101_coding/publicTransportation/israel-public-transportation

# Ensure log directory is writable
mkdir -p /tmp/gtfs_logs
chmod 777 /tmp/gtfs_logs

echo "Permissions fixed."
