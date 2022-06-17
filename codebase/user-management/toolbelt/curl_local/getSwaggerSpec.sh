#!/usr/bin/env bash
TOKEN=$1
SERVER_URL=http://localhost:9000
curl -k -XGET $SERVER_URL/api/um-service/v0.1/docs/swagger.json
