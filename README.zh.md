# NATPoked: 跨平台P2P内网穿透工具

**警告**：此项目仍在开发中，不能用于生产环境。请关注 (watch+star) 本项目并等待 Release。

## 特性

1. 实现穿透一切任意一方直接具有公网IP的网络
2. 实现穿透一切任意一方具有UPnP支持的网络环境的NAT
3. 实现穿透一切任意一方为全锥型NAT的网络环境
4. 尽最大努力穿透任意一方为限制型NAT甚至对称型NAT
5. 穿透成功后，提供可靠的流式传输（STREAM）和不可靠的报文传输（DGRAM）功能
6. 穿透成功后，对流式传输提供安全传输功能
7. 提供具有TCP和UDP端口转发功能的CLI的Daemon程序
    （尽管不可能直接进行TCP打洞，但是可以在打通的UDP链路上运行可靠的传输协议）
8. 跨操作系统平台支持，应至少支持Linux、Windows。可选支持 Android和macOS
9. 跨CPU架构支持，应至少支持x86-64和AArch64架构的CPU
10. 同时支持小数据量和大数据量传输，对于不同数据量采取不同的传输策略。例如，小数据量传送可以考虑在P2P链路建立前直接通过中转服务器发出，而大数据量传送则应等待链路建立后再进行传送。
11. 国产信创平台支持，能够部署到国产操作系统和CPU上——信创产业学院设备提供支撑
12. GPL 开源 & 自由 & 免费

本程序也可以当作**去中心化** VPN 使用。

## 安装

### Linux

强烈建议使用 [Docker 镜像](https://hub.docker.com/r/kenvix/natpoked) 一键安装

```shell
docker pull kenvix/natpoked
```

## 构建

为确保兼容性，应使用 Java11 编译，但应在 Java17 平台上运行，就像 `Dockerfile` 里写的一样。

### 普通构建
```shell
git submodule update --init --recursive
chmod +x ./gradlew
./gradlew shadowJar
```

### Docker 构建
```shell
git submodule update --init --recursive
chmod +x ./DockerBuild.sh
./DockerBuild.sh
```