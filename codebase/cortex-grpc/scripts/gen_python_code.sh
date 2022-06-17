#!/usr/bin/env bash

set -e

find src/main/proto -type f -exec python -m grpc_tools.protoc --python_out=. --mypy_out=. -Isrc/main/proto {} \;
find cortex -type d -exec touch {}/__init__.py {}/__init__.pyi \;
