#!/bin/bash

# URL Shortener Backend API - Sample cURL Commands
# This script demonstrates various API operations

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL
BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api/v1"

echo -e "${BLUE}=== URL Shortener Backend - API Examples ===${NC}\n"

# 1. Create a shortened URL
echo -e "${GREEN}1. Creating a shortened URL...${NC}"
SHORT_URL_RESPONSE=$(curl -s -X POST "$API_URL/urls" \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://www.example.com/very/long/url/with/many/parameters?query=value&another=param"
  }')

echo "$SHORT_URL_RESPONSE" | jq '.'
SHORT_CODE=$(echo "$SHORT_URL_RESPONSE" | jq -r '.shortCode')
echo -e "${BLUE}Short code generated: ${GREEN}$SHORT_CODE${NC}\n"

# 2. Get analytics for the shortened URL
echo -e "${GREEN}2. Getting analytics for the shortened URL...${NC}"
curl -s -X GET "$API_URL/urls/$SHORT_CODE" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 3. Access the redirect endpoint (this will simulate a click)
echo -e "${GREEN}3. Accessing redirect endpoint (simulates click)...${NC}"
curl -s -w "\nStatus: %{http_code}\n" -L -X GET "$BASE_URL/$SHORT_CODE" \
  -H "Accept: application/json"
echo -e "\n"

# 4. Check analytics again (click count should increase)
echo -e "${GREEN}4. Getting analytics after redirect (click count updated)...${NC}"
curl -s -X GET "$API_URL/urls/$SHORT_CODE" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 5. Create another shortened URL
echo -e "${GREEN}5. Creating another shortened URL...${NC}"
SECOND_RESPONSE=$(curl -s -X POST "$API_URL/urls" \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://github.com"
  }')
echo "$SECOND_RESPONSE" | jq '.'
SECOND_CODE=$(echo "$SECOND_RESPONSE" | jq -r '.shortCode')
echo ""

# 6. Access the second shortened URL multiple times
echo -e "${GREEN}6. Accessing second URL multiple times (3 times)...${NC}"
for i in {1..3}; do
  echo "Redirect #$i:"
  curl -s -w "Status: %{http_code}\n\n" -L -X GET "$BASE_URL/$SECOND_CODE" > /dev/null
done

# 7. Check analytics for second URL
echo -e "${GREEN}7. Getting analytics for second URL (should show 3 clicks)...${NC}"
curl -s -X GET "$API_URL/urls/$SECOND_CODE" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 8. Health check endpoint
echo -e "${GREEN}8. Health check endpoint...${NC}"
curl -s -X GET "$BASE_URL/actuator/health" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 9. Application info endpoint
echo -e "${GREEN}9. Application info endpoint...${NC}"
curl -s -X GET "$BASE_URL/actuator/info" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 10. Test error handling - invalid URL
echo -e "${GREEN}10. Testing error handling - invalid URL...${NC}"
curl -s -X POST "$API_URL/urls" \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "not-a-valid-url"
  }' | jq '.'
echo ""

# 11. Test error handling - missing URL
echo -e "${GREEN}11. Testing error handling - missing originalUrl...${NC}"
curl -s -X POST "$API_URL/urls" \
  -H "Content-Type: application/json" \
  -d '{}' | jq '.'
echo ""

# 12. Test error handling - not found
echo -e "${GREEN}12. Testing error handling - short code not found...${NC}"
curl -s -X GET "$API_URL/urls/nonexistent" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# 13. Swagger/OpenAPI documentation
echo -e "${GREEN}13. API Documentation:${NC}"
echo -e "Swagger UI: ${BLUE}$BASE_URL/swagger-ui.html${NC}"
echo -e "OpenAPI JSON: ${BLUE}$BASE_URL/v3/api-docs${NC}\n"

echo -e "${GREEN}=== Examples completed ===${NC}"

