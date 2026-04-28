#!/usr/bin/env bash
# Next steps for Google Sign-In (Web + Android OAuth clients).
# Run from repo root: bash scripts/google-oauth-next-steps.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "=========================================="
echo "  Google Sign-In — manual steps (console)"
echo "=========================================="
echo ""
echo "Your debug keystore SHA-1 (must match Android OAuth client):"
(cd "$ROOT/MyBus" && ./gradlew :app:signingReport --no-daemon -q 2>/dev/null) | grep -A 20 "Variant: debug" | grep "SHA1:" | head -1 || echo "  (run: cd MyBus && ./gradlew :app:signingReport)"
echo ""
echo "Package name: com.bolguru.balajisevak"
echo ""
echo "1) Open Google Cloud → APIs & Services → Credentials"
echo "   https://console.cloud.google.com/apis/credentials"
if command -v open >/dev/null 2>&1; then
  open "https://console.cloud.google.com/apis/credentials" 2>/dev/null || true
  echo "   (Opened in browser on macOS.)"
fi
echo ""
echo "2) Create OAuth client ID → Application type: Web application"
echo "   - Name: e.g. BalajiSevak backend"
echo "   - Copy the Client ID (ends with .apps.googleusercontent.com)"
echo ""
echo "3) Put that Web client ID in BOTH places (same string):"
echo "   - $ROOT/.env"
echo "       GOOGLE_CLIENT_ID=<paste Web client ID here>"
echo "   - $ROOT/MyBus/local.properties"
echo "       google.web.client.id=<paste Web client ID here>"
echo ""
echo "4) Keep your Android OAuth client (or create one) with:"
echo "   - Package: com.bolguru.balajisevak"
echo "   - SHA-1: (see above, from signingReport)"
echo ""
echo "5) Rebuild and install app, restart Node API:"
echo "   cd $ROOT/MyBus && ./gradlew installDebug"
echo "   cd $ROOT && npm start   # or your server command"
echo ""
echo "Note: The Android-only client ID is NOT a substitute for the Web client"
echo "      when using requestIdToken + server verification."
echo "=========================================="
