#!/usr/bin/env bash
#usage: ./createUser.sh username password adminUserToken
#You can get adminUserToken with getRootToken.sh
TOKEN=$1
USERNAME=$2
EMAIL=$USERNAME@sentrana.com
FIRST_NAME=$USERNAME
LAST_NAME=Test
PASSWORD=$3
ORG_ID=orgs_self_service
SERVER_URL=https://um-service-dev.sentrana.com/
echo Creating user $USERNAME in organization $ORG_ID on dev um-service, using token $TOKEN
curl -k -XPOST "$SERVER_URL/api/um-service/v0.1/orgs/$ORG_ID/users?access_token=$TOKEN" -H "Content-Type: application/json" --data "{\"username\": \"$USERNAME\", \"email\": \"$EMAIL\", \"password\": \"$PASSWORD\", \"firstName\": \"$FIRST_NAME\", \"lastName\": \"$LAST_NAME\", \"groupIds\": []}"
