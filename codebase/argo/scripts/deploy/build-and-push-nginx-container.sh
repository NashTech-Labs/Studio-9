#!/bin/bash

ruby $(dirname $0)/../../nginx/scripts/build.rb &&
ruby $(dirname $0)/../../nginx/scripts/deploy.rb