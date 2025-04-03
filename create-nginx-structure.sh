#!/bin/bash

# Create proper Nginx directories
sudo mkdir -p /etc/nginx/sites-available
sudo mkdir -p /etc/nginx/sites-enabled
sudo mkdir -p /etc/nginx/snippets/custom

echo "Created Nginx directory structure"

# Copy existing server blocks to separate files for future organization
# These won't be used yet, but will be ready for future refactoring

# Extract existing server blocks
sudo grep -n "^server {" /etc/nginx/conf.d/101.conf | while read -r line; do
    line_num=$(echo "$line" | cut -d: -f1)
    server_name=$(sudo grep -A 5 "^server {" /etc/nginx/conf.d/101.conf | grep "server_name" | head -n 1 | sed 's/.*server_name\s*\([^;]*\);.*/\1/' | tr -d ' ' | head -n 1)
    
    if [ -n "$server_name" ]; then
        echo "Found server block at line $line_num for $server_name"
        
        # Create config file (not linked yet)
        sudo touch "/etc/nginx/sites-available/$server_name.conf"
    fi
done

echo "Created placeholder configuration files"
echo
echo "Next steps:"
echo "1. Update nginx.conf to include sites-enabled directory"
echo "2. Extract server blocks to separate files"
echo "3. Enable sites using symbolic links"
echo
echo "For now, your existing configuration in 101.conf will continue to work"
