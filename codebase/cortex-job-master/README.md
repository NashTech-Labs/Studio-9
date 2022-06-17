[![Build Status](https://travis-ci.com/deepcortex/cortex-job-master.svg?token=wTDKqoqNHb5cAH3pQhun&branch=develop)](https://travis-ci.com/deepcortex/cortex-job-master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/d9c389cc2c174fa7a298ac5cb20acf81)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/cortex-job-master&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/d9c389cc2c174fa7a298ac5cb20acf81)](https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=deepcortex/cortex-job-master&utm_campaign=Badge_Coverage)

# cortex-job-master

<Description>

### Codacy integration
Set environmental variable 
```CORTEX_JOB_MASTER_CODACY_PROJECT_TOKEN=d48267d2a17340bd9912791864ddece7```

In order to "dockerize" the project locally run the following:

```bash
sbt docker:publishLocal
``` 

In order to run only "unit" tests which is sufficient in most cases you should login into dockerhub using "docker login". Ask an administrator for credentials:

```bash
sbt test
 ```
 
In order to test job-master-task locally run the following:

```bash
sbt "project cortex-job-master" "run job --filepath <absolute path of file containing baile's protobuf message> [--version <version of `cortex-job-master-tasks` docker image>]"
```
 
In order to publish the project to DC Dockerhub repository run the following:

```bash
echo $DOCKER_PASSWORD | docker login -u="$DOCKER_USERNAME" --password-stdin
docker push "deepcortex/cortex-job-master:<version>"
```

In order to update the project version which will be used by orion,
the path s3://artifacts.dev.deepcortex.ai/configurations/services/orion-api-rest/development/vars.env
should be updated. Also there can be configured JVM parameters (like JVM heap size factor, Cpus, Memory)
applying to the cortex-job-master application. 

[Optional] In order to run all tests including those ones which can be executed only on Mesos:
 
 1. install mesos (v.1.4.1) with gpu support following this guides:
    - http://mesos.apache.org/documentation/latest/building/
    - http://mesos.apache.org/documentation/latest/gpu-support/
 2. start mesos-master and mesos agents
 3. set up e2e_testenv.sh script using appropriate credentials
 4. export MESOS_NATIVE_JAVA_LIBRARY=<some_absolute_path>/mesos-1.4.1/build/src/.libs/libmesos.so
 5. sbt test-all

### To resolve dependencies from "DeepCortex Internal Repository" maven repository

##### For SBT
 
 Add these lines to your ~/.profile (at the end of file). Ask a system administrator for keys 
 
```
 export AWS_DEFAULT_REGION=<your region>
 export AWS_ACCESS_KEY_ID_ARTIFACTS_DEEPCORTEX_AI=<your_access_key>
 export AWS_SECRET_KEY_ARTIFACTS_DEEPCORTEX_AI=<your_secret_key>
 ```
 
##### For IntelliJ
 
 Create file ~/.sbt/.s3credentials
 
 ```
 accessKey = <your_access_key>
 secretKey = <your_secret_key>
 ```

### Deployment

The service is being run by Orion. To deploy new version of JM one needs to change orion config.
The config needs to be changed both in dc-os running instance and Orion deploy config.

For DC-OS running instance go to DC-OS UI and change corresponding environment variable.

Deploy config is stored on S3. Sample procedure:
```bash
aws s3 cp s3://artifacts.dev.deepcortex.ai/configurations/services/orion-api-rest/development/vars.env orion.env
# change the file
aws s3 cp orion.env s3://artifacts.dev.deepcortex.ai/configurations/services/orion-api-rest/development/vars.env
```
