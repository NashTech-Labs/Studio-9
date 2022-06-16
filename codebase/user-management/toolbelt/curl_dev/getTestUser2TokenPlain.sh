#!/usr/bin/env bash
SERVER_URL=https://um-service-dev.sentrana.com/
curl -k -XPOST $SERVER_URL/api/um-service/v0.1/token --data "grant_type=password&username=intTest_user2&password=guessMe"
