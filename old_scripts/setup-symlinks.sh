#!/bin/bash

# Create symlinks for PHP scripts in the web root
ln -sf /home/yaniv/101_coding/publicTransportation/api/transport.php /home/yaniv/1Iz3UBgvtNDVfVo/transport.php
ln -sf /home/yaniv/101_coding/publicTransportation/api/gtfs-api.php /home/yaniv/1Iz3UBgvtNDVfVo/api/gtfs-api.php

echo "Symlinks created successfully!"

./setup-symlinks.sh
