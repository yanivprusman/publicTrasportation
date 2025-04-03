#!/bin/bash

# Create a PHP handling configuration for Nginx
sudo bash -c 'cat > /etc/nginx/snippets/custom/php-handler.conf' << 'EOF'
# PHP handler configuration
location ~ \.php$ {
    # Check if file exists
    try_files $uri =404;
    
    # FastCGI setup
    fastcgi_pass unix:/var/run/php/php8.3-fpm.sock;
    fastcgi_index index.php;
    fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
    
    # Include standard FastCGI parameters
    include fastcgi_params;
    
    # Increased timeout for long-running scripts
    fastcgi_read_timeout 300;
    
    # Improved error handling
    fastcgi_intercept_errors on;
}
EOF

# Update localhost server configuration
sudo bash -c 'cat > /etc/nginx/conf.d/sites-available/localhost.conf' << 'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name localhost 127.0.0.1;
    
    root /home/yaniv/1Iz3UBgvtNDVfVo;
    index index.php index.html;
    
    # Direct access to PHP files
    include snippets/custom/php-handler.conf;
    
    # API endpoints
    location /api/ {
        try_files $uri $uri/ =404;
    }
    
    location / {
        try_files $uri $uri/ =404;
    }
}
EOF

# Test Nginx configuration
echo "Testing Nginx configuration..."
sudo nginx -t

if [ $? -eq 0 ]; then
    echo "Configuration test successful. Reloading Nginx..."
    sudo systemctl reload nginx
    echo "Nginx reloaded with updated configuration."
else
    echo "Nginx configuration test failed. Please check the error messages above."
fi
