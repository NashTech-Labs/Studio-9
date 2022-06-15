#!/usr/bin/env bash
# Pre-requisites:
# npm install mongodb -g
# npm install east east-mongo -g

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

APP_NAME=$1

export APP_NAME=$APP_NAME

envsubst < $DIR/mongodb.json.template > $DIR/mongodb.json

east --config $DIR/mongodb.json --dir $DIR/$APP_NAME/migrations migrate
