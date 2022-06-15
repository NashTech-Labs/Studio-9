Cortex-GRPC
======

[![Build Status](https://travis-ci.com/deepcortex/cortex-grpc.svg?token=dYQ8y9WVPQU8KZMpENtE&branch=master)](https://travis-ci.com/deepcortex/cortex-grpc)

This project describes interface between Job Master and Baile in form of protocol buffers (protobuf) messages.

Protobuf definitions are located in
[src/main/proto](src/main/proto).

A general example of how to serialize/deserialize protobuf messages can be found in
[Sample.scala](src/main/scala/cortex/api/job/Sample.scala).

Messages are semantically divided.

| Job Type | Protobuf | Docs |
|---|---|---|
| General | [job.proto](src/main/proto/cortex/api/job/job.proto) ||
| Album (common) | [common.proto](src/main/proto/cortex/api/job/album/common.proto) | [Common.md](docs/album/Common.md) |
| Album Uploading | [uploading.proto](src/main/proto/cortex/api/job/album/uploading.proto) | [Uploading.md](docs/album/Uploading.md) |
| Album Data Augmentation | [augmentation.proto](src/main/proto/cortex/api/job/album/augmentation.proto) | [Augmentation.md](docs/album/Augmentation.md) |
| Computer Vision | [computervision.proto](src/main/proto/cortex/api/job/computervision.proto) | [ComputerVision.md](docs/ComputerVision.md) |
| Table (common + uploading) | [table.proto](src/main/proto/cortex/api/job/table.proto) | [Table.md](docs/Table.md) |
| Tabular Models | [tabular.proto](src/main/proto/cortex/api/job/tabular.proto) | [Tabular.md](docs/Tabular.md) |
| Online prediction | [online.prediction.proto](src/main/proto/cortex/api/job/online.prediction.proto) | [Online.prediction.md](docs/Online.prediction.md) |

Fixture messages generator
------
To create protobuf request fixtures(to use in e2e tests, usually), use [MessageGenerator.scala](src/test/scala/cortex/api/job/MessageGenerator.scala).
It will save messages as file to the specified location.

This class requires [typesafe config](https://github.com/lightbend/config) to be provided.
```scala
val conf: Config = ...
val messageGenerator = new MessageGenerator(conf)
```

For the structure of the config file, refer to example configuration in [default.conf](src/test/resources/default.conf).
The semantics of the values in the configuration file is pretty much there same as in protobufs themselves
(since they are used to build those), so see [docs](docs) for detailed explanations on each of those.

MessageGenerator public methods should be self-explanatory in which message they generate.
For each of the **generate\<message name\>** method, absolute output file path should be provided.
```scala
val outputPath = "/tmp/deepcortex/e2e-jobs/20171208/1a98d16f-586f-4fb0-bd98-c0c79c436393/params.dat"
messageGenerator.generateCVTrain(outputPath)
```

To generate bunch of messages straight from the command line,
using default inputs from [default.conf](src/test/resources/default.conf), use sbt.

First, provide AWS settings to access S3:
```bash
export AWS_BUCKET_NAME=...
export AWS_ACCESS_KEY=...
export AWS_SECRET_KEY=...
export AWS_REGION=... #us-east-1 by default
```
Then run the generator:
```bash
sbt clean compile
sbt "test:runMain cortex.api.job.MessageGenerator"
```
In this case, messages will be saved using the following output template:
```/tmp/deepcortex/e2e-jobs/<year><month><day>/<UUID>/params.dat```


Publishing
------

To build python package locally:
```
make build
```

To publish package to S3 repository:

```
make publish
```

To publish only python package to S3 repository:

```
make publish-python
```

To publish only scala package to S3 repository:

```
make publish-scala
```
