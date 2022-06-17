#!/bin/bash

chown splunk: /opt/splunkforwarder/etc/system/local/*
if (( $(ps -ef | grep -v grep | grep 'splunk' | wc -l) > 0 ))
then
    echo `date` ' - splunk is already running'
else
    echo `date` ' - Starting splunk'
    /opt/splunkforwarder/bin/splunk start --accept-license
fi


if (( $(ps -ef | grep -v grep | grep 'nginx: master' | wc -l) > 0 ))
then
	echo `date` ' - nginx is already running'
else
	echo `date` ' - Starting nginx'
	nginx -g 'daemon on;'
fi

if (( $(ps -ef | grep -v grep | grep 'um-service' | wc -l) > 0 ))
then
	echo `date` ' - um-service is already running. Killing it.'
	PID=$(cat /opt/um-service/RUNNING_PID)
	kill $PID
fi

echo `date` ' - Starting um-service'
export _JAVA_OPTS="-Xms1024m -Xmx2048m"

rm -f /opt/um-service/RUNNING_PID
/opt/um-service-dc/bin/um-service -Dconfig.resource=dev.conf -Dhttp.port=9000 > /var/log/startup.log

echo `date` ' - start.sh ending. Container shutting down.'
