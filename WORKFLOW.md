# Development Workflow

## Current State
✅ **Dev and Prod are in sync**
- Both on commit: `dd06b05f` ("Add transport.php proxy route for production")  
- Port 3002 (Production): Working
- Port 3003 (Development): Working

## Development Workflow

### 1. Develop in Dev Environment
```bash
cd ~/coding/automateLinux/extraApps/publicTransportation

# Make your changes to the code
# Test on port 3003 (development server with hot reload)
```

### 2. Commit and Push Changes
```bash
git add .
git commit -m "Your commit message"
git push
```

### 3. Deploy to Production
```bash
# Run the sync script to update production
~/coding/automateLinux/extraApps/publicTransportation/sync-prod.sh
```

This script will:
- Pull latest code from GitHub
- Install/update dependencies
- Rebuild the React app
- Restart the PM2 process

## Directory Structure
- **Dev**: `/home/yaniv/coding/automateLinux/extraApps/publicTransportation`
- **Prod**: `/home/yaniv/coding/prod/publicTransportation-prod`

## Ports
- **3002**: Production (served via PM2 running `server.js`)
- **3003**: Development (webpack dev server with hot reload)

## Important Notes
- Never edit files directly in the production directory
- The production `.env` and `config.php` files are not in git (they're ignored)
- Make sure to have these files in production:
  - `/home/yaniv/coding/prod/publicTransportation-prod/.env`
  - `/home/yaniv/coding/prod/publicTransportation-prod/backend/php-api/config.php`
