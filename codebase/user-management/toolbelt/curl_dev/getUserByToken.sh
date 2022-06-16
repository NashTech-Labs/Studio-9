#!/usr/bin/env bash
TOKEN=$1
SERVER_URL=https://um-service-dev.sentrana.com/
echo retrieving user from dev um-service by token $TOKEN
curl -k -XGET $SERVER_URL/api/um-service/v0.1/token/$TOKEN/user
