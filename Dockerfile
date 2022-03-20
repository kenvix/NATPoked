# syntax=docker/dockerfile:experimental
FROM ubuntu:21.10
LABEL maintainer="kenvix <i@kenvix.com>"
LABEL description="NATPoked official docker image"
LABEL homepage="https://kenvix.com"

RUN --mount=type=bind,target=/root/resources,id=resources,source=resources,ro \
    echo "Copying resources..." \
    && cp -r /root/resources /root/build \
    && echo "Begin docker image build ..." \
    && apt-get update \
    && apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    software-properties-common \
    openjdk-17-jre-headless \
    mosquitto \
    && cd /root/build \
    && rm -rf ./out ./build \
    && ./gradlew shadowJar \
    && mkdir -p /data/app \
    && mkdir -p /data/config \
    && mv ./build/output/*.jar /data/app/app.jar \
    && echo "Cleaning build cache ..." \
    && cd /data/config \
    && apt-get clean \
    && rm -rf "/root/.gradle/" \
    && rm -rf /tmp/* \
    && rm -rf /root/build \
    && echo "Dockerfile build success"

WORKDIR /data/config
ENV LANG en_US.UTF-8

CMD ["java", "-jar", "/data/app/app.jar"]