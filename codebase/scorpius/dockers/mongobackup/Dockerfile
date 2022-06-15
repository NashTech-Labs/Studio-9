FROM mongo:4.1

RUN apt-get update && apt-get -y install awscli
COPY dockers/mongobackup/mongobackup.sh /mongobackup.sh
ENTRYPOINT ["/bin/bash", "/mongobackup.sh"]

LABEL maintainer="sergey.semichev@sentrana.com" \
      org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="DeepCortex" \
      org.label-schema.description="DeepCortex is the world’s first cloud based, automated platform for doing the entire end-to-end Data Science process" \
      org.label-schema.url="http://www.deepcortex.ai" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.version=$IMAGE_VERSION \
      org.label-schema.vcs-url="https://github.com/deepcortex/scorpius" \
      org.label-schema.vendor="DeepCortex" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.docker.cmd="docker run --rm -it deepcortex/scorpius-mongobackup /bin/bash"