#!/usr/bin/env bash
TOKEN=$1
SERVER_URL=https://um-service-dev.sentrana.com/
echo Creating app on dev um-service, using token $TOKEN
curl -k -XPOST "$SERVER_URL/api/um-service/v0.1/apps?access_token=$TOKEN" -H "Content-Type: application/json" --data "{\"name\": \"baile\", \"desc\": \"Baile\", \"url\": \"https://baile-dev.sentrana.com\"}"
