#!/bin/bash

# Create a symbolic link to the GTFS examiner in the web directory
ln -sf /home/yaniv/101_coding/publicTransportation/examine-gtfs.php /home/yaniv/1Iz3UBgvtNDVfVo/gtfs-examiner.php

echo "GTFS Examiner is now available at: http://localhost/gtfs-examiner.php"
echo "You can examine specific files by using the URL parameters:"
echo "- http://localhost/gtfs-examiner.php?file=trips.txt"
echo "- http://localhost/gtfs-examiner.php?file=stops.txt"
echo "- http://localhost/gtfs-examiner.php?file=routes.txt"
echo "- http://localhost/gtfs-examiner.php?file=stop_times.txt"
