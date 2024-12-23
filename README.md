# NATPoked: A Cross-platform Peer-To-Peer NAT Traversal Toolkit

[中文文档](README.zh.md)

**Warning**: Although this project is generally usable, we do not take any responsibility for its bugs or stability issues. Some toolkits included in this software may be considered in violation of cybersecurity regulations by certain organizations. You assume full responsibility for any consequences arising from the use of this tool.

## Features

It can traverse complex NAT networks such as symmetric NAT, and use Poisson sampling and Mean method to quickly predict ports, which may be the most advanced NAT traversal toolkit

1. Implement network penetration where either party directly has a public IP address.  
2. Implement network penetration in environments where either party supports UPnP.  
3. Implement network penetration in environments where either party uses a full-cone NAT.  
4. Make best efforts to penetrate networks where either party uses a restricted NAT or even a symmetric NAT.  
5. After successful penetration, provide reliable stream transmission (STREAM) and unreliable datagram transmission (DGRAM) functionality.  
6. After successful penetration, provide secure transmission capabilities for stream transmissions.  
7. Provide a CLI-based daemon program with TCP and UDP port forwarding functionality.  
    (Although direct TCP hole punching is not feasible, reliable transmission protocols can be run on established UDP connections.)  
8. Cross-platform support for operating systems, with at least support for Linux and Windows, and optionally Android and macOS.  
9. Cross-CPU architecture support, with at least support for x86-64 and AArch64 CPU architectures.  
10. Support both small and large data transmissions, employing different strategies for each. For example, small data transmissions can be sent directly through a relay server before establishing a P2P connection, whereas large data transmissions should wait for the connection to be established before sending.  
11. Support for domestic trusted innovation platforms, enabling deployment on domestic operating systems and CPUs—backed by equipment from Trusted Innovation Industry Academy.  
12. Open-source under GPL, free, and unrestricted.  

This program can also serve as a **decentralized** VPN.

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

# Technical information

## Packet format

### Port redirector (Encrypted)

```text
+------------+--------------------------+------------------+---------------+------------+
| Type code  | Service name (hashcode)  | Destination port | Source port   | Unused     |
+------------+--------------------------+------------------+---------------+------------+
| 2 bytes    | 4 bytes                  | 2 bytes          | 2 bytes       | 6 bytes    |
+------------+--------------------------+------------------+---------------+------------+
```
