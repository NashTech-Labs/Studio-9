#!/bin/bash

function validateStackParameter() {
    eval stackVariable=\$$1
    [[ -z "$stackVariable" ]] && {
        printf "\n*** ERROR ***\nCannot assign value to [$1] parameter.\n"
        printf "$2\n\n"
        abort
    }
}

configuration_path="s3://artifacts.dev.knoldususa.com/configurations/scorpius/vars.env"

export $(aws s3 cp ${configuration_path} - | xargs)

env_vars=(
    "AWS_ACCESS_KEY_ID"
    "AWS_SECRET_ACCESS_KEY"
    "DCOS_USERNAME"
    "DCOS_PASSWORD"
)

for env_var in "${env_vars[@]}"
do
   :
   validateStackParameter ${env_var} "Check [${configuration_path}] file" &&
done

echo "Common environment variables initialized"