# NATPoked: A Cross-platform Peer-To-Peer NAT Traversal Toolkit

[🇨🇳 中文文档 🇨🇳](README.zh.md)

**WARNING**: THIS PROJECT IS STILL IN DEVELOPMENT AND CANNOT BE USED IN PRODUCTION. PLEASE WATCH and STAR IT AND WAIT A RELEASE BEFORE USING IT.

## Features

It can traverse complex NAT networks such as symmetric NAT, and use Poisson sampling and Mean method to quickly predict ports, which may be the most advanced NAT traversal toolkit

## Install

### Linux

It is strongly to use [Docker Image](https://hub.docker.com/r/kenvix/natpoked) for the installation.

```shell
docker pull kenvix/natpoked
```

## Build

To ensure compatibility, it should be compiled with Java11, but should run on the Java17 platform, as written in the `Dockerfile`.

### Normal Build
```shell
git submodule update --init --recursive
chmod +x ./gradlew
./gradlew shadowJar
```

### Docker Build
```shell
git submodule update --init --recursive
chmod +x ./DockerBuild.sh
./DockerBuild.sh
```