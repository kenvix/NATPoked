# NATPoked: A Cross-platform Peer-To-Peer NAT Traversal Toolkit

[🇨🇳 中文文档 🇨🇳](README.zh.md)

**WARNING**: THIS PROJECT IS STILL IN DEVELOPMENT AND CANNOT BE USED IN PRODUCTION. PLEASE WATCH and STAR IT AND WAIT A RELEASE BEFORE USING IT.

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