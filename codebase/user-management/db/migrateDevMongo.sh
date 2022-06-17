#!/usr/bin/env bash
# Pre-requisites:
# npm install mongodb -g
# npm install east east-mongo -g

east --config east/eastrc-dev.json --dir migrations migrate


