#!/usr/bin/env bash
SERVER=http://localhost:9000
CONTEXT=api/um-service/v0.1
curl -XPOST $SERVER/$CONTEXT/token --data "grant_type=client_credentials&client_id=baile_app_id&client_secret=baileDevSecret"
