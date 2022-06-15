 ### studio9 infrastructure deployment
  ```bash
  export CONFIG=
  export AWS_ACCESS_KEY_ID=
  export AWS_SECRET_ACCESS_KEY=
  export DCOS_USERNAME=
  export DCOS_PASSWORD=
  ```
  
   ```bash
   docker run \
     -v /path/to/environments:/opt/deploy/environments \
     -v /path/to/.ssh:/root/.ssh \
     -e CONFIG=${CONFIG} \
     -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
     -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
     -e DCOS_USERNAME=${DCOS_USERNAME} \
     -e DCOS_PASSWORD=${DCOS_PASSWORD} \
     -e DOCKER_EMAIL_LOGIN=${DOCKER_EMAIL_LOGIN} \
     -e DOCKER_REGISTRY_AUTH_TOKEN=${DOCKER_REGISTRY_AUTH_TOKEN} \
     cloud.canister.io:5000/knoldususa/scorpius:TAG
    ```