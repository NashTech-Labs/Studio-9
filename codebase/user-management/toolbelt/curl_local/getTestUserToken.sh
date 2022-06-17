#!/usr/bin/env bash
PORT=9000
curl -XPOST localhost:$PORT/api/um-service/v0.1/token --data "grant_type=password&username=inttest_user1&password=inttest_user1"
