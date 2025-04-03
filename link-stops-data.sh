#!/bin/bash

# Create symbolic link for the stops data
ln -sf /home/yaniv/101_coding/publicTransportation/api/stops-data.php /home/yaniv/1Iz3UBgvtNDVfVo/api/stops-data.php

echo "Created stops data API link. Access at: http://localhost/api/stops-data.php?line=60"
