
# What is Studio-9?

**Studio9** is an open source platform for doing collaborative Data Management & AI/ML anywhere Whether your data is trapped in silos or you’re generating data at the edge, Studio9 gives you the flexibility to create AI and data engineering pipelines wherever your data is. And you can share your AI, Data, and Pipelines with anyone anywhere. With Studio9, you can achieve newfound agility to effortlessly move between compute environments, while all your data and your work replicates automatically to wherever you want.

Below are described the major components of Studio-9. 

1. *ORION* - A service further consisting of three components namely Job Dispatcher, Job Supervisor and Job Resource Cleaner. Job Dispatcher mainly forwards messages from RabbitMQ to the proper Job Supervisor, instantiating it for each new job request. Job Supervisor is responsible for instantiating job master for each new job which will have a new job supervisor setup. Job Resource Cleaner consumes messages from RabbitMQ and spins a new JobResourcesCleanerWorker for handling each message which then executes tasks for cleaning the resources. 

2. *ARIES* - A microservice that allows read/write access to ElasticSearch. It stores Job Metadata, Heartbeats and Job Results in ElasticSearch as documents. 

3. *TAURUS* - This service works as a message dispatcher using SQS/SNS.

4. *BAILE* - It receives messages from the UI service called Salsa and then sends them to Cortex if its not Online Prediction. In case of Online Prediction, Salsa sends messages to Taurus which then sends them to Cortex.

5. *ARGO* - A service designed to capture all configuration parameters for all job types or services. These parameters are saved by Argo in ElasticSearch. 

6. *PEGASUS* - A prediction storage service that receives messages from Taurus via Orion to upload data to RedShift. The messages contain metadata for online prediction job and CSV file with prediction results. 

# What are use cases?

# What Studio9 can do?
## Reduce Your AI Workload 120x
Studio9 provides a large inventory of building blocks from which you can stitch together custom AI and Data Engineering pipelines. Rapidly assemble and test many different pipelines to create the AI you need. Turn your data into AI with near-zero effort and cost. Since Studio9 is an open platform, newer cutting-edge AI building blocks that are emerging every day are put right at your fingertips.

# How to deploy Studio9 on Local?

We'll be deploying Studio9 on local using a docker-compose file.

### Prerequisites

- Docker should be installed on your local system.
- If you don't have docker installed in your system, kindly refer to this [link](https://docs.docker.com/engine/install/ubuntu/)
- After successfully installing Docker, clone the [Repository](https://github.com/knoldus/Studio-9.git).
- Run the Docker Compose file


## Explanation of Docker Compose

  

# How to deploy Studio9 on Cloud?
