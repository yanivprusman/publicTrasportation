#!/bin/bash

# Install Laravel via Composer
cd /home/yaniv/101_coding/publicTransportation
composer create-project laravel/laravel laravel

# Set proper permissions
chmod -R 755 laravel/storage
chmod -R 755 laravel/bootstrap/cache

echo "Laravel project created at /home/yaniv/101_coding/publicTransportation/laravel"
