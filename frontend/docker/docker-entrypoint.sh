#!/bin/sh
set -e

# Generate runtime config from environment variables
# This allows configuration via Kubernetes ConfigMaps without rebuilding the image

echo "Generating runtime configuration..."

# Set defaults for optional variables
export USE_MOCKS="${USE_MOCKS:-false}"
export FIREBASE_VAPID_KEY="${FIREBASE_VAPID_KEY:-}"

# Generate config.js from template
envsubst < /usr/share/nginx/html/config.js.template > /usr/share/nginx/html/config.js

echo "Runtime config generated:"
cat /usr/share/nginx/html/config.js

# Execute the main command (nginx)
exec "$@"
