#!/usr/bin/env bash
# Pre-requisites:

migrate=${MIGRATE}
if [[ ${migrate} = false ]]
then
    exit 0
fi

echo "Setting up variables"
sleep 30
apt-get update
apt-get install gettext-base -y
envsubst < east/eastrc-staging.var.json > east/eastrc-staging.json

echo "Migrating"
npm install mongodb -g
npm install east east-mongo -g

east --config east/eastrc-staging.json --dir staging-migrations migrate


