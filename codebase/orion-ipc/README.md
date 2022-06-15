[![Build Status](https://travis-ci.com/deepcortex/orion-ipc.svg?token=pvwDNvw6P8fj9zJxpA1p&branch=master)](https://travis-ci.com/deepcortex/orion-ipc)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3f5dbfe31e864bd5ab3b99904541350c)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/orion-ipc&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/3f5dbfe31e864bd5ab3b99904541350c)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/orion-ipc&amp;utm_campaign=Badge_Coverage)

# orion-ipc

Set of libraries to provide IPC and RPC

## RPC:

Remote procedure call (RPC) is an Inter-process communication technology that allows a computer program to cause a subroutine or procedure to execute in another address space (commonly on another computer on a shared network) without the programmer explicitly coding the details for this remote interaction.

## IPC:

Inter-process communication (IPC) is a set of techniques for the exchange of data among multiple threads in one or more processes. Processes may be running on one or more computers connected by a network.

#### So, RPC is just one kind of IPC

### To resolve dependencies from "DeepCortex Internal Repository" maven repository

##### For SBT

Add these lines to your ~/.bash_profile

```
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID_ARTIFACTS_DEEPCORTEX_AI=AKIAIQBIZHXAHKOCU3FA
export AWS_SECRET_KEY_ARTIFACTS_DEEPCORTEX_AI=/5r0v26+hWTyZttryuhC1XvwewvZ2kxsKyDwillO
```

##### For IntelliJ

Create file ~/.sbt/.s3credentials

```
accessKey = AKIAIQBIZHXAHKOCU3FA
secretKey = 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
