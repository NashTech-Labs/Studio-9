#!/usr/bin/env bash
# Pre-requisites:
# npm install mongodb -g
# npm install east east-mongo -g

#10.10.20.128 schema: um-service-s/ user name: um_service_s/ password: t7r6YyWfCUgGZgZg#MrMDeyRWTd#ioZX
east --config east/eastrc-staging.json --dir staging-migrations migrate


