#!/usr/bin/env bash
# Pre-requisites:
# npm install mongodb -g
# npm install east east-mongo -g

#10.10.20.128 schema: um-service-t/ user name: um_service_t/ password: wSLXegS.Ci/n}31YQpr8asU!Ugm0AWPKVU@z
east --config east/eastrc-test.json --dir migrations migrate


