#!/usr/bin/env bash
TOKEN=$1
PORT=9000
echo retrieving user from local um-service by token $TOKEN
curl -XGET localhost:$PORT/api/um-service/v0.1/token/$TOKEN/user
