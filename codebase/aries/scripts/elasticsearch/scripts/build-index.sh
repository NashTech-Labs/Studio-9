#!/bin/sh

# build Elasticsearch Index from scratch
[[ "" == "$es_index" ]] && echo "ERROR!!! es_index is empty" && exit
[[ "" == "$es_host" ]] && es_host=localhost
[[ "" == "$es_port" ]] && es_port=9200
[[ "" == "$mapping_path" ]] && echo "ERROR!!! mapping_path is empty" && exit
version="_v1"
idx="$es_index$version"
protocol="http://"

echo
cluster_url=${protocol}${es_host}:${es_port}
index_url=${protocol}${es_host}:${es_port}/${idx}
alias_url=${protocol}${es_host}:${es_port}/_aliases

read -p "Will re-create $index_url index (Yy): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo
    is_index_exist=$(curl -I --head --write-out %{http_code} --silent --output /dev/null ${index_url})
    if [ ${is_index_exist} -eq 200 ]; then
        echo "Deleting index $idx ..."
        curl -XDELETE ${index_url}; echo
        echo "Deleted"; echo
    fi

    echo "Creating index $idx ..."
    FILE="$( cd -P "$( dirname "$0" )/.." && pwd )/${mapping_path}"

    curl -XPUT ${index_url} -d @${FILE}; echo
    
    is_index_exist=$(curl -I --head --write-out %{http_code} --silent --output /dev/null ${index_url})
    if [ ${is_index_exist} -ne 200 ]; then
        echo; echo "ERROR!!! Cannot create $idx index"; echo
        exit;
    fi
    echo "Created"; echo

    echo ${alias_url}
    
    echo "Creating alias $es_index ..."
    curl -XPOST ${alias_url} -d @- << EOF
    {
        "actions" : [
            { "add" : { "index": "${idx}", "alias" : "${es_index}" } }
        ]
    }
EOF
    echo; echo "Created"; echo
    
    echo; echo

    # report out on settings and mappings
    echo "Cluster settings:";
    curl -XGET ${index_url}/_settings;echo;echo

    echo "Cluster health:";
    curl -XGET ${cluster_url}/_cat/health;echo

    echo "DONE"
fi

