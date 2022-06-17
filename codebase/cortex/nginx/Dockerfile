FROM nginx:1.9

COPY default.conf /etc/nginx/conf.d/default.conf

ARG BUILD_DATE=""
ARG VCS_REF=""
ARG IMAGE_VERSION=""

LABEL maintainer="acdifran@gmail.com" \
      org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Cortex-Nginx" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.version=$IMAGE_VERSION \
      org.label-schema.vcs-url="https://github.com/deepcortex/cortex" \
      org.label-schema.vendor="DeepCortex" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.docker.cmd="docker run --rm -it deepcortex/cortex-nginx R"
