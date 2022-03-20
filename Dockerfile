FROM ubuntu:21.10 as intermediate

ADD . /root/build
RUN echo "Begin docker image build ..." \
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


FROM ubuntu:21.10
LABEL maintainer="kenvix <i@kenvix.com>"
LABEL description="NATPoked: A Cross-platform Peer-To-Peer NAT Traversal Toolkit - Official docker image"
LABEL homepage="https://kenvix.com"

COPY --from=intermediate /data /data

RUN echo "Begin docker image build ..." \
    && apt-get update \
    && apt-get install -y \
    ca-certificates \
    openjdk-17-jre-headless \
    mosquitto \
    && echo "Cleaning build cache ..." \
    && cd /data/config \
    && apt-get clean \
    && rm -rf /tmp/* \
    && echo "Dockerfile build success"


WORKDIR /data/config
ENV LANG en_US.UTF-8

CMD ["java", "-jar", "/data/app/app.jar"]