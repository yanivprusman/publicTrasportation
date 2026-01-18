#!/bin/bash

# Default values
DEFAULT_URL="http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json?Key=YP719171&MonitoringRef=26472"

# Check if IP is provided as argument, otherwise check environment variable
if [ -n "$1" ]; then
    PROXY_IP="$1"
elif [ -n "$KAMATERA_IP" ]; then
    PROXY_IP="$KAMATERA_IP"
else
    echo "Error: IP address not specified."
    echo "Usage: $0 <PROXY_IP> or set KAMATERA_IP environment variable"
    exit 1
fi

echo "Testing connection via $PROXY_IP..."
ssh root@"$PROXY_IP" "curl -k -X GET \"$DEFAULT_URL\""
