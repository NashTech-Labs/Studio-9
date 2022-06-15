FROM centos:centos7

ENV PACKER_VERSION=1.0.4
ENV PACKER_SHA256SUM=646da085cbcb8c666474d500a44d933df533cf4f1ff286193d67b51372c3c59e

RUN yum install -y epel-release && \
    yum install -y unzip && \
    yum install -y uuid && \
    yum install -y openssh-clients && \
    yum install -y rsync && \
    yum install -y curl && \
    yum install -y python-pip && \
    yum install -y gettext && \
    yum install -y wget

RUN pip install awscli --upgrade

RUN wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_amd64.zip
RUN wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_SHA256SUMS

RUN rm -rf /usr/sbin/packer
RUN sed -i '/.*linux_amd64.zip/!d' packer_${PACKER_VERSION}_SHA256SUMS && \
    sha256sum -c --strict packer_${PACKER_VERSION}_SHA256SUMS && \
    unzip packer_${PACKER_VERSION}_linux_amd64.zip -d /bin && \
    rm -f packer_${PACKER_VERSION}_linux_amd64.zip && \
    echo "packer version - $(packer --version)"

ENV TERRAFORM_VERSION=0.11.0
ENV TERRAFORM_SHA256SUM=402b4333792967986383670134bb52a8948115f83ab6bda35f57fa2c3c9e9279

RUN wget --quiet https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip && \
    echo "${TERRAFORM_SHA256SUM}  terraform_${TERRAFORM_VERSION}_linux_amd64.zip" > terraform_${TERRAFORM_VERSION}_SHA256SUMS && \
    sha256sum -c --strict terraform_${TERRAFORM_VERSION}_SHA256SUMS && \
    unzip terraform_${TERRAFORM_VERSION}_linux_amd64.zip -d /bin && \
    rm -f terraform_${TERRAFORM_VERSION}_linux_amd64.zip && \
    echo "terraform version - $(terraform --version)"

RUN wget --quiet https://releases.hashicorp.com/terraform-provider-aws/1.14.0/terraform-provider-aws_1.14.0_linux_amd64.zip && \
    unzip terraform-provider-aws_1.14.0_linux_amd64.zip -d ~/.terraform.d/plugins && \
    rm -f terraform-provider-aws_1.14.0_linux_amd64.zip && \
    echo "terraform aws provider version 1.14.0 installed"

RUN wget --quiet https://releases.hashicorp.com/terraform-provider-random/1.2.0/terraform-provider-random_1.2.0_linux_amd64.zip && \
    unzip terraform-provider-random_1.2.0_linux_amd64.zip -d ~/.terraform.d/plugins && \
    rm -f terraform-provider-random_1.2.0_linux_amd64.zip && \
    echo "terraform random provider version 1.2.0 installed"

RUN wget --quiet https://releases.hashicorp.com/terraform-provider-template/1.0.0/terraform-provider-template_1.0.0_linux_amd64.zip && \
    unzip terraform-provider-template_1.0.0_linux_amd64.zip -d ~/.terraform.d/plugins && \
    rm -f terraform-provider-template_1.0.0_linux_amd64.zip && \
    echo "terraform template provider version 1.0.0 installed"

RUN curl https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.12/dcos -o dcos && \
    mv dcos /usr/local/bin && \
    chmod +x /usr/local/bin/dcos

WORKDIR /opt/deploy

COPY ansible ansible
COPY packer packer
COPY scripts scripts
COPY terraform terraform
COPY build.sh build.sh
COPY destroy.sh destroy.sh
COPY falcon_deployment falcon_deployment
COPY import_multiple_iam.sh import_multiple_iam.sh
COPY import_singular_iam.sh import_singular_iam.sh
COPY import_s3.sh import_s3.sh
COPY import_vpc.sh import_vpc.sh
COPY suspend_dc.sh suspend_dc.sh
COPY resume_dc.sh resume_dc.sh
COPY set_capacity.sh set_capacity.sh
COPY packer.sh packer.sh
COPY terraform.sh terraform.sh
COPY terraform_init_backend.sh terraform_init_backend.sh
COPY c2s_dockers c2s_dockers

ENV SALSA_VERSION=falcon

RUN wget https://s3.amazonaws.com/artifacts.dev.deepcortex.ai/deployment_downloads/${SALSA_VERSION}/front-end.tar.gz -P ansible/roles/captain/files/dcos_services/
# RUN wget https://s3.amazonaws.com/artifacts.dev.deepcortex.ai/deployment_downloads/Datasets.tar.gz -P ansible/roles/captain/files/dcos_services/

# keeping here if we move to use baked packages
#RUN mkdir -p ansible/galaxy && \
#    wget https://github.com/torian/ansible-role-filebeat/archive/3.0.2.tar.gz -O ansible/galaxy/torian.filebeat.3.0.2.tar.gz && \
#    wget https://github.com/geerlingguy/ansible-role-nodejs/archive/4.1.2.tar.gz -O ansible/galaxy/geerlingguy.nodejs.4.1.2.tar.gz && \
#    wget https://github.com/geerlingguy/ansible-role-java/archive/1.7.4.tar.gz -O ansible/galaxy/geerlingguy.java.1.7.4.tar.gz && \
#    wget https://github.com/geerlingguy/ansible-role-postgresql/archive/1.3.1.tar.gz -O ansible/galaxy/geerlingguy.postgresql.1.3.1.tar.gz

ENTRYPOINT ["/bin/bash", "/opt/deploy/build.sh"]

ARG BUILD_DATE=""
ARG VCS_REF=""
ARG IMAGE_VERSION=""

LABEL maintainer="anthony.difrancesco@sentrana.com" \
      org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="DeepCortex" \
      org.label-schema.description="DeepCortex is the world’s first cloud based, automated platform for doing the entire end-to-end Data Science process" \
      org.label-schema.url="http://www.deepcortex.ai" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.version=$IMAGE_VERSION \
      org.label-schema.vcs-url="https://github.com/deepcortex/scorpius" \
      org.label-schema.vendor="DeepCortex" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.docker.cmd="docker run --rm -it deepcortex/scorpius-deployment /bin/bash"



