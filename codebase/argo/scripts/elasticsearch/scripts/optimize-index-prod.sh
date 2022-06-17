#!/bin/sh

es_index=<YOUR_INDEX>_prod
es_host=localhost
es_port=9201
number_of_replicas=1
protocol="http://"

echo
cluster_url=${protocol}${es_host}:${es_port}
index_url=${protocol}${es_host}:${es_port}/${es_index}

read -p "Will optimize $index_url index (Yy): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo

    echo "Optimizing index..."
    curl -XPUT ${index_url}/_settings -d '{"index": {"refresh_interval": "1s"}}'
    echo
    curl -XPUT ${index_url}/_settings -d "{"index": {"number_of_replicas": ${number_of_replicas}}}"
    echo; echo

    # report out on settings and mappings
    echo "Cluster settings:";
    curl -XGET ${index_url}/_settings;echo;echo

    echo "Cluster health:";
    curl -XGET ${cluster_url}/_cat/health;echo

    echo "DONE"
fi
