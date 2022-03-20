# NATPoked: 跨平台P2P内网穿透工具

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