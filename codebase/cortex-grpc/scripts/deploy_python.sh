#!/usr/bin/env bash

set -e

if [ -z "$PYTHON_ARTIFACTS_S3_FOLDER" ]; then
    echo "PYTHON_ARTIFACTS_S3_FOLDER is not provided"
    exit 1
fi

echo "Publishing version: " `python setup.py --version`
s3pypi --bucket artifacts.deepcortex.ai --secret $PYTHON_ARTIFACTS_S3_FOLDER --force --no-sdist --private
