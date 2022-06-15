#!/bin/bash

export AWS_DEFAULT_REGION=${AWS_REGION}
IFS=$',' read -rd '' -a MONGODB_HOST_ARR <<< "${MONGODB_HOSTS}"

MONGODB_HOST_STRING="rs/"

for MONGODB_HOST in "${MONGODB_HOST_ARR[@]}"; do
    MONGODB_HOST_STRING=$( echo "$MONGODB_HOST_STRING$MONGODB_HOST:$MONGODB_PORT," | tr -d '[:space:]')
done

MONGODB_HOST_STRING=${MONGODB_HOST_STRING%?}

IFS=$',' read -rd '' -a DATABASES <<< "${DBS}"

function backupMongo () {
    for DATABASE in "${DATABASES[@]}"; do

        mongodump -h $MONGODB_HOST_STRING -d $DATABASE -u admin -p $MONGODB_ROOTADMIN_PASSWORD --authenticationDatabase admin --out dump$DATABASE --quiet

        DATE=`date +%Y_%m_%d`

        DUMP=$(echo "dump$DATABASE" | tr -d '\n')

        tar -czf $DUMP.tar.gz $DUMP
        aws s3 cp $DUMP.tar.gz s3://$AWS_S3_BUCKET/mongo_backups/$DATE/
    done
}


echo "=> Backup started"
    if backupMongo ;then
        echo "   > Backup succeeded"
    else
        echo "   > Backup failed"
    fi
echo "=> Done"