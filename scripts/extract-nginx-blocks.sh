#!/bin/bash

# Configuration paths
NGINX_CONF="/etc/nginx/conf.d/101.conf"
SITES_AVAILABLE="/etc/nginx/conf.d/sites-available"

# Backup the original config
echo "Creating backup of original config..."
sudo cp "$NGINX_CONF" "$NGINX_CONF.backup.$(date +%Y%m%d%H%M%S)"

# Extract server blocks using awk
echo "Extracting server blocks..."

# Extract cgnat.ya-niv.com server block
sudo awk '/server {/,/^}/ { 
  if ($0 ~ /server_name cgnat.ya-niv.com/) {
    capture = 1
  }
  if (capture) {
    print $0
    if ($0 ~ /^}/) {
      capture = 0
      exit
    }
  }
}' "$NGINX_CONF" > /tmp/cgnat.ya-niv.com.conf
sudo mv /tmp/cgnat.ya-niv.com.conf "$SITES_AVAILABLE/cgnat.ya-niv.com.conf"
echo "Extracted cgnat.ya-niv.com server block"

# Extract 10.0.0.54 server block
sudo awk '/server {/,/^}/ { 
  if ($0 ~ /server_name 10.0.0.54/) {
    capture = 1
  }
  if (capture) {
    print $0
    if ($0 ~ /^}/) {
      capture = 0
      exit
    }
  }
}' "$NGINX_CONF" > /tmp/10.0.0.54.conf
sudo mv /tmp/10.0.0.54.conf "$SITES_AVAILABLE/10.0.0.54.conf"
echo "Extracted 10.0.0.54 server block"

# Extract localhost server block
sudo awk '/server {/,/^}/ { 
  if ($0 ~ /server_name localhost/) {
    capture = 1
  }
  if (capture) {
    print $0
    if ($0 ~ /^}/) {
      capture = 0
      exit
    }
  }
}' "$NGINX_CONF" > /tmp/localhost.conf
sudo mv /tmp/localhost.conf "$SITES_AVAILABLE/localhost.conf"
echo "Extracted localhost server block"

echo "All server blocks extracted"
