#!/bin/bash

# Create the necessary directories if they don't exist
sudo mkdir -p /etc/nginx/conf.d/sites-available
sudo mkdir -p /etc/nginx/conf.d/sites-enabled
sudo mkdir -p /etc/nginx/snippets/custom

# Create the required snippets
sudo bash -c 'cat > /etc/nginx/snippets/custom/api-endpoints.conf' << 'EOF'
# API endpoint configuration for GTFS API
location /api/gtfs-api.php {
    # Full path to API script instead of using root directive
    fastcgi_param SCRIPT_FILENAME /home/yaniv/101_coding/publicTransportation/api/gtfs-api.php;
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    include fastcgi_params;
    
    # Add better error handling
    fastcgi_intercept_errors on;
    error_page 404 500 502 503 504 = @error_handler;
}

# Fallback direct access path for GTFS API
location = /gtfs-api.php {
    fastcgi_param SCRIPT_FILENAME /home/yaniv/101_coding/publicTransportation/api/gtfs-api.php;
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    include fastcgi_params;
}

# Error handler for API endpoints
location @error_handler {
    return 500 '{"error":"Nginx encountered an error processing the PHP request"}';
    default_type application/json;
}

# API directory settings
location /api/ {
    root /home/yaniv/1Iz3UBgvtNDVfVo;
    try_files $uri $uri/ /index.php?$query_string;
    
    # PHP files should be processed by FastCGI
    location ~ \.php$ {
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        include fastcgi_params;
    }
}

# Direct access to test-api-local.php
location = /api/test-api-local.php {
    alias /home/yaniv/101_coding/publicTransportation/api/test-api-local.php;
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    fastcgi_param SCRIPT_FILENAME /home/yaniv/101_coding/publicTransportation/api/test-api-local.php;
    include fastcgi_params;
}
EOF

sudo bash -c 'cat > /etc/nginx/snippets/custom/php-handling.conf' << 'EOF'
# Common PHP configuration
location ~ \.php$ {
    include snippets/fastcgi-php.conf;
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
    include fastcgi_params;
}

# Default try_files directive for PHP applications
location / {
    try_files $uri $uri/ /index.php?$query_string;
}

# Security: deny access to .htaccess files
location ~ /\.ht {
    deny all;
}
EOF

# Create the individual site configuration files
sudo bash -c 'cat > /etc/nginx/conf.d/sites-available/cgnat.ya-niv.com.conf' << 'EOF'
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name cgnat.ya-niv.com;
    root /home/yaniv/1Iz3UBgvtNDVfVo;
    
    # SSL configuration
    ssl_certificate /etc/letsencrypt/live/cgnat.ya-niv.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/cgnat.ya-niv.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
    
    index index.php index.html index.htm;

    # WebSocket proxy configuration
    location /ws {
        proxy_pass https://10.0.0.55:443;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Prevent buffering of WebSocket data
        proxy_buffering off;
    }

    # Include common configurations
    include snippets/custom/php-handling.conf;
    include snippets/custom/api-endpoints.conf;
}
EOF

sudo bash -c 'cat > /etc/nginx/conf.d/sites-available/10.0.0.54.conf' << 'EOF'
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name 10.0.0.54;

    # Development mode - SSL is commented out
    # ssl_certificate /etc/nginx/selfsigned.crt;
    # ssl_certificate_key /etc/nginx/selfsigned.key;

    root /home/yaniv/1Iz3UBgvtNDVfVo;
    index index.php index.html index.htm;

    location /html/html/middle-man.js {
        alias /home/yaniv/101_coding/flatBuffers/buildJs/dist/mrd/middle-man.js;
        default_type application/javascript;
        add_header Cache-Control "public, max-age=86400";
    }
    
    location /wss {
        proxy_pass http://localhost:8443;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Include common configurations
    include snippets/custom/php-handling.conf;
    include snippets/custom/api-endpoints.conf;
}
EOF

sudo bash -c 'cat > /etc/nginx/conf.d/sites-available/localhost.conf' << 'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name localhost 127.0.0.1;
    
    # Set root to the folder where your PHP files are
    root /home/yaniv/1Iz3UBgvtNDVfVo;
    index index.php index.html index.htm;
    
    # Include common configurations
    include snippets/custom/php-handling.conf;
    include snippets/custom/api-endpoints.conf;
}
EOF

echo "Nginx configuration files created successfully."
