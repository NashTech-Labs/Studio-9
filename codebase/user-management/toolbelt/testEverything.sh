#!/bin/sh
set -e #exit after first error

echo Running unit tests for um-service
sbt umService/test

echo Running integration tests for um-service
sbt umService/it:test

echo Running unit tests for um-client-sdk-play
sbt umClientSdkPlay/test

echo Publishing um-service to local repository in order to prepare for acceptance tests
sbt publishLocal

echo Running acceptance tests for um-client-sdk-play
sbt acceptance
