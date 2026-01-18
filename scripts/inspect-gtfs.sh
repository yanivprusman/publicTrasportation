#!/bin/bash

# Configuration
DATA_DIR="/home/yaniv/101_coding/publicTransportation/israel-public-transportation"

# Check if directory exists
if [ ! -d "$DATA_DIR" ]; then
    echo "Error: Directory $DATA_DIR does not exist."
    exit 1
fi

echo "=== GTFS File Analysis ==="
echo

# List all files and their sizes
echo "## Directory Contents ##"
ls -lh "$DATA_DIR" | grep "\.txt"
echo

# Check for important GTFS files
required_files=("agency.txt" "calendar.txt" "routes.txt" "stops.txt" "stop_times.txt" "trips.txt" "shapes.txt")

echo "## Checking Required Files ##"
for file in "${required_files[@]}"; do
    if [ -f "$DATA_DIR/$file" ]; then
        size=$(du -h "$DATA_DIR/$file" | cut -f1)
        lines=$(wc -l < "$DATA_DIR/$file")
        echo "✓ $file - $size, $lines lines"
    else
        echo "✗ $file - MISSING"
    fi
done
echo

# Analyze routes.txt in particular
routes_file="$DATA_DIR/routes.txt"
if [ -f "$routes_file" ]; then
    echo "## Analyzing routes.txt ##"
    echo "Header row:"
    head -n1 "$routes_file"
    echo
    
    echo "Checking for line 60:"
    line_60_count=$(grep -i ",60," "$routes_file" | wc -l)
    if [ $line_60_count -gt 0 ]; then
        echo "Found $line_60_count entries for line 60"
        echo "First few entries:"
        grep -i ",60," "$routes_file" | head -n3
    else
        echo "No entries found for line 60. Checking alternative formats..."
        
        # Try other possible formats
        alt_formats=(",\"60\"," "\"60\"," ",60$" ",\"60\"$")
        for format in "${alt_formats[@]}"; do
            count=$(grep -i "$format" "$routes_file" | wc -l)
            if [ $count -gt 0 ]; then
                echo "Found $count entries with format: $format"
                grep -i "$format" "$routes_file" | head -n2
            fi
        done
    fi
fi

echo
echo "=== Analysis Complete ==="
