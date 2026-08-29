#!/usr/bin/env bash
# Seeds a demo owner account, one widget, and two sample submissions.
set -e
BASE_URL="${BASE_URL:-http://localhost:3000}"
EMAIL="demo-$(date +%s)@example.com"
PASSWORD="DemoPass123!"

echo "Signing up demo owner: $EMAIL"
SIGNUP=$(curl -s -X POST "$BASE_URL/auth/signup" -H "Content-Type: application/json" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$SIGNUP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Signup failed: $SIGNUP"
  exit 1
fi
echo "Owner created, token acquired."

echo "Creating a demo widget..."
WIDGET=$(curl -s -X POST "$BASE_URL/widgets" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "contact",
    "title": "Demo Contact Widget",
    "description": "Seeded for evaluation",
    "fields": [{"name":"email","label":"Email","type":"email","required":true}],
    "buttonText": "Submit"
  }')
WIDGET_ID=$(echo "$WIDGET" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
EMBED=$(echo "$WIDGET" | grep -o '"embedSnippet":"[^"]*"' | cut -d'"' -f4)

if [ -z "$WIDGET_ID" ]; then
  echo "Widget creation failed: $WIDGET"
  exit 1
fi
echo "Widget created: $WIDGET_ID"
echo "Embed snippet: $EMBED"

echo "Submitting two demo leads..."
curl -s -X POST "$BASE_URL/submissions" -H "Content-Type: application/json" \
  -d "{\"widgetId\":\"$WIDGET_ID\",\"fields\":{\"email\":\"lead1@example.com\"}}" > /dev/null
curl -s -X POST "$BASE_URL/submissions" -H "Content-Type: application/json" \
  -d "{\"widgetId\":\"$WIDGET_ID\",\"fields\":{\"email\":\"lead2@example.com\"}}" > /dev/null

echo ""
echo "Seed complete."
echo "Owner email:    $EMAIL"
echo "Owner password: $PASSWORD"
echo "Widget ID:      $WIDGET_ID"
echo "Dashboard:      curl -H \"Authorization: Bearer $TOKEN\" $BASE_URL/dashboard"