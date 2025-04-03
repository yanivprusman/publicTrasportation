#!/bin/bash

echo "Removing unused files..."

# App2Map.js is not needed anymore
if [ -f "/home/yaniv/101_coding/publicTransportation/react/public-transportation/src/App2Map.js" ]; then
    echo "Removing App2Map.js..."
    rm "/home/yaniv/101_coding/publicTransportation/react/public-transportation/src/App2Map.js"
fi

echo "Cleanup completed!"
