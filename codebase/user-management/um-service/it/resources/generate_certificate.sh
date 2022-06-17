#!/bin/sh
#Generates self-signed certificate

openssl req -x509 -newkey rsa:1024 -keyout saml_sample_key.pem -out saml_sample_cert.pem -nodes
