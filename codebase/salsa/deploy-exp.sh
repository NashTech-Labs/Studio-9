#!/bin/sh

set -e

BRANCH=$(git rev-parse --abbrev-ref HEAD)
BRANCH_LOWER=$(echo "$BRANCH" | tr '[:upper:]' '[:lower:]')

echo $BRANCH_LOWER

npm run build:prod:mock

# see https://aws.amazon.com/cli/
# see http://docs.aws.amazon.com/cli/latest/userguide/cli-config-files.html

aws s3 sync dist/ s3://dev.studio9.ai/static-content/exp/origin/$BRANCH_LOWER/ --delete
