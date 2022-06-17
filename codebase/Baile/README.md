# Baile
[![Build Status](https://travis-ci.com/deepcortex/baile.svg?token=dYQ8y9WVPQU8KZMpENtE&branch=develop)](https://travis-ci.com/deepcortex/baile)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/b1f5e62537314389b4cae2de97782127)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=deepcortex/baile&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/b1f5e62537314389b4cae2de97782127)](https://www.codacy.com?utm_source=github.com&utm_medium=referral&utm_content=deepcortex/baile&utm_campaign=Badge_Coverage)

Baile is a REST API service which is responsible for two things:

1. Managing **Deep Cortex** (DC) meta storage, i.e. information about things like models, tables, albums, pictures, flows, etc.
2. Submitting jobs to the **Cortex Job Master** (JM) and handling the result of their processing.

To keep it in that way, keep the following in mind:

+ Baile should always stay lightweight in a sense that it should not do heavy tasks as this should be done by JM only.
If you feel that certain action requires something more complex than just making call to the storage or processing
small file, this is a sign for JM to support new type of job.

+ Baile is the entry point to DC infrastructure, i.e. the only public API. Every endpoint should be designed carefully
as there are no guarantees to that this endpoint is not called by a third person. It means that all internal endpoints
should be protected via some authorization mechanism (basic auth as the most trivial one).

## Running

Baile uses [typesafe (aka lightbend) config](https://github.com/lightbend/config) for configuration. For the reference
see the default dummy configuration [here](src/main/resources/application.conf).

```bash
sbt run
```
This will start http HTTP server on your localhost and 9000 port.

You can replace the configuration with another file in resources directory by setting `config.resource` property:
```bash
sbt -Dconfig.resource=myconfig.conf run
```
