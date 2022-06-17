##Pull the standard docker nginx image
FROM centos:7
MAINTAINER "Sentrana Inc"

RUN yum install -y wget
RUN wget http://nginx.org/packages/centos/7/noarch/RPMS/nginx-release-centos-7-0.el7.ngx.noarch.rpm -P /tmp && rpm -i /tmp/nginx-release-centos-7-0.el7.ngx.noarch.rpm
RUN yum update -y; yum clean all
RUN yum install -y jre
RUN yum install -y nginx
RUN yum install -y glibc-common
RUN yum clean all

##Add our build
ADD files /

##Fix our locale
RUN /usr/bin/localedef -c -i en_US -f UTF-8 en_US.UTF-8
ENV LANG en_US.utf8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.utf8


WORKDIR /opt/um-service
## We want to serve the nginx front end
EXPOSE 80

CMD ["/bin/bash", "/opt/start.sh"]
