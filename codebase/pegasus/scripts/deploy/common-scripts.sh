#!/bin/bash

function abort() {
    set -e
    exit 1
}

function validateStackParameter() {
    eval stackVariable=\$$1
    [[ -z "$stackVariable" ]] && {
        printf "\n*** ERROR ***\nCannot assign value to [$1] parameter.\n"
        printf "$2\n\n"
        abort
    }
}
