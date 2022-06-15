#!/bin/bash
export MONGODB_URL="mongodb://${MONGODB_APP_USER}:${MONGODB_APP_PASSWORD}@${MONGODB_HOSTS}:27017/baile?replicaSet=rs&authSource=admin"
export MONGODB_DB_NAME="baile"
export BAILE_DOCKER_IMAGE_VERSION=${BAILE_DOCKER_IMAGE_VERSION}

