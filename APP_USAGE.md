# Public Transportation App - Usage Guide

## 1. Prerequisites
- **Nginx** configured with PHP support (as per `consolidated-setup.sh`).
- **PHP 8.3** with FPM.
- **Node.js** and **NPM** (for the React frontend).
- **Composer** (for the Laravel backend).

## 2. Start the Proxy
To enable access to the Ministry of Transportation (MOT) servers, you must start the proxy via the daemon.

```bash
# Start the proxy (this establishes a tunnel to 10.0.0.1)
d publicTransportationStartProxy

cat /tmp/pt_proxy_port

# Configure the PT app port (first time setup)
d setPort pt <PORT>  # e.g., d setPort pt 3002

# Open the App
d publicTransportationOpenApp
```
The daemon will dynamically assign a port for the proxy and use the configured port for opening the app. The applications will automatically detect the proxy port.

## 3. Running the Application

### Backend Services
The setup script (`consolidated-setup.sh`) links the PHP files to your web root (`/home/yaniv/1Iz3UBgvtNDVfVo`).
Ensure Nginx is running:
```bash
sudo systemctl status nginx
```

Access the APIs directly via browser or `curl`:
- **Line 60 API**: [http://localhost/api/line60-data.php](http://localhost/api/line60-data.php)
- **Stops Visualization**: [http://localhost/stops-visualization.php](http://localhost/stops-visualization.php)
- **GTFS Examiner**: [http://localhost/gtfs-examiner.php](http://localhost/gtfs-examiner.php)

### React Frontend
To start the React frontend:

1. Navigate to the React directory:
   ```bash
   cd react/public-transportation
   ```
2. Install dependencies (if not already done):
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm start
   ```
   This will open the app at [http://localhost:3000](http://localhost:3000).

### Laravel Backend (Optional)
If using the specific Laravel features:
1. Navigate to the Laravel directory:
   ```bash
   cd laravel
   ```
2. Start the development server (if not using Nginx):
   ```bash
   php artisan serve
   ```

## 4. Updates & Maintenance
- If you change the proxy server IP, remember to use the new IP with `test-proxy.sh`.
- Run `consolidated-setup.sh` to fix permissions or broken symlinks if you move files.
