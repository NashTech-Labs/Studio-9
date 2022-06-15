#!/bin/bash

# fix for deprecation warning
export PIP_FORMAT=legacy

if ! pip list | grep -w es2csv &>/dev/null; then
  sudo pip install https://${S3_ENDPOINT}/${ARTIFACTS_S3_BUCKET}/packages/pip/es2csv-5.5.2.tar.gz
fi

day() {
  date -d "@$(($1 / 1000))"
}

while true; do
    read -p "How many days worth of logs would you like to export? " days
    if ! [[ "$days" =~ ^[0-9]+$ ]] ; 
        then exec >&2; echo "error: Not an integer"; echo "Please enter an integer."
    else
        currentMillis=$(date +%s000)
        pastMillis=$(($currentMillis - 864*100000*$days))
        while true; do
            echo "Logs will be exported between $(day $pastMillis) and $(day $currentMillis)."
            read -p "Is this correct? [y/n] " yn
            case $yn in
                [Yy]* ) echo "Using provided dates."; break 2;;
                [Nn]* ) break;;
                * ) echo "Please provide a yes or no answer."
            esac
        done
    fi
done    

while true; do
    read -p "Enter the JobId for the job you would like to pull logs for: " jobId
    if ! [[ "$jobId" =~ [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12} ]] ; 
        then exec >&2; echo "error: Not a valid UUID"; echo "Please enter a UUID."
    else
        while true; do
            echo "Logs will be exported for Job: $jobId."
            read -p "Is this correct? [y/n] " yn
            case $yn in
                [Yy]* ) echo "Using provided JobId."; break 2;;
                [Nn]* ) break;;
                * ) echo "Please provide a yes or no answer."
            esac
        done
    fi
done

echo "{
  \"query\": {
    \"bool\": {
      \"must\": [
        {
          \"query_string\": {
            \"query\": "\"$jobId\"",
            \"analyze_wildcard\": true
          }
        },
        {
          \"range\": {
            \"@timestamp\": {
              \"gte\": $pastMillis,
              \"lte\": $currentMillis,
              \"format\": \"epoch_millis\"
            }
          }
        }
      ],
      \"must_not\": []
    }
  }
}" > "request_$jobId.json"

while true; do
    read -p "Enter a name for the output csv file (do not include a file extension in the name): " ouput_csv
    if ! [[ "$jobId" =~ ^[a-zA-Z0-9_.-]*$ ]] ; 
        then exec >&2; echo "error: Not a valid string"; echo "Please enter a string."
    else
        while true; do
            echo "Logs will be exported to $ouput_csv.csv."
            read -p "Is this correct? [y/n] " yn
            case $yn in
                [Yy]* ) echo "Using provided filename."; break 2;;
                [Nn]* ) break;;
                * ) echo "Please provide a yes or no answer."
            esac
        done
    fi
done

echo "Requesting logs..."

export DCOS_MASTER_PRIVATE_IP=$(aws ec2 describe-instances --filter Name=tag-key,Values=Name Name=tag-value,Values=$MASTER_INSTANCE_NAME --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
ssh -i /opt/private_key -o StrictHostKeyChecking=no -f -L 9200:coordinator.elastic.l4lb.thisdcos.directory:9200 deployer@"$DCOS_MASTER_PRIVATE_IP" sleep 10
getLogs=$(es2csv -k -r -q @"request_$jobId.json" -i 'filebeat-*' -o $ouput_csv.csv)

echo "$getLogs"

rm "request_$jobId.json"
