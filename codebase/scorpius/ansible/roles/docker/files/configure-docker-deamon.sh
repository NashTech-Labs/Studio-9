#!usr/bin/env bash

#script executed on all the nodes of DCOS cluster
#usage: bash configure-docker-deamon.sh registry_port

REGISTRY_PORT=$1

if [ -z "$REGISTRY_PORT" ]
then
	REGISTRY_PORT=80
fi

tee /etc/docker/daemon.json << EOF
{
  "insecure-registries" : ["docker-registry.marathon.l4lb.thisdcos.directory:$REGISTRY_PORT"]
}
EOF

systemctl daemon-reload
systemctl restart docker
