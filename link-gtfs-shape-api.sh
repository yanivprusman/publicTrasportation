#!/bin/bash

# Create symbolic link for the GTFS shape API
ln -sf /home/yaniv/101_coding/publicTransportation/api/gtfs-shape-api.php /home/yaniv/1Iz3UBgvtNDVfVo/api/gtfs-shape-api.php

echo "GTFS shape API link created. Access it at: http://localhost/api/gtfs-shape-api.php?line=60"
