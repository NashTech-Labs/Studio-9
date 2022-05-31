
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
## Computational Data Core that Automatically Scales and Adapts to You
Imagine never having to worry about how to keep your data organized, keep track of how, when, and where it was manipulated, keep track of where it came from, or keep track of all its meta-data. Now imagine being able to effortlessly and securely share your data and its lineage with your colleagues. Finally, imagine being able to do any Analytics or Machine Learning right where your data is. The Studio9 Computational Data Core makes this all possible.

## The Data Science Replication Engine
Every step you perform in the Analytics & AI Lifecycle results in a valuable asset – a snippet of code, or a data transformation pipeline, or a table of newly engineered data, or an album of images or a new algorithm. Imagine having the power to instantly use any asset anyone creates to build bigger and better AI models that constantly expand your power to generate breakthroughs. Studio9 gives your team the frictionless ability to organize, track, share, and re-use all your Analytics & AI assets.

## Automated Model Governance & Compliance
Studio9 allows Model Risk Management, Regulatory Constraints, and Documentation Policies that your models must abide by to be encoded right into Pipeline and automatically reproduced every time a model is refreshed by Studio9. This includes Model Explainability, Model Fairness & Bias Analytics, Model Uncertainty, and Model Drift analytics – all of which are performed automatically.

We don’t think AI makes machines smarter. It exists to make you smarter.

The easier it is for you to make AI, the greater your ability to make breakthroughs. Whether you have unlimited compute resources in the cloud, or you are limited at the edge, your ability to make breakthroughs should be unencumbered. We are committed to giving you the breakthrough Data Management & AI/ML capabilities you need so you can create the breakthroughs you want – anywhere, anytime, and with anyone.

# What Studio9 can do?

## Reduce Your AI Workload 120x
Studio9 provides a large inventory of building blocks from which you can stitch together custom AI and Data Engineering pipelines. Rapidly assemble and test many different pipelines to create the AI you need. Turn your data into AI with near-zero effort and cost. Since Studio9 is an open platform, newer cutting-edge AI building blocks that are emerging every day are put right at your fingertips.

## Studio9 helps you find the breakthroughs hidden in your data
Studio9 streamlines your burden of wrangling data. With its continuously expanding portfolio of building blocks, Studio9 makes it easier for you to clean, integrate, enrich, and harmonize your data. Do it all within your own infinitely scalable database environment without any of the hassle of managing your own database.

## Push-button Model Deployment
You now have the power to deploy and run your Data Processing pipelines, Models, and AI anywhere – from infinitely scalable Cloud computing infrastructure to your own laptop to ultra-low power edge computing devices – with no additional programming or engineering effort required. We designed Studio9 for deployment flexibility so you can build, train, share, and execute your AI anywhere you want.


# How to deploy Studio9 on Local?

We'll be deploying Studio9 on local using a docker-compose file.

## Prerequisites

- Docker should be installed on your local system.
- If you don't have docker installed in your system, kindly refer to this [link](https://docs.docker.com/engine/install/ubuntu/)
- After successfully installing Docker, clone the [Repository](https://github.com/knoldus/Studio-9.git).
- Run the Docker Compose file


## Explanation of Docker Compose

  

# How to deploy Studio9 on Cloud?
