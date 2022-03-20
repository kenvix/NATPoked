# CoroutineKCP

基于 beykery/jkcp 的改良纯算法 KCP 实现，增加了 Kotlin 协程包装器并改良数据结构以提升性能

- 不包含任何网络实现
- `KCPARQProvider` 提供了线程安全的协程挂起式的 KCP 读写调用
- 只依赖 Netty ByteBuf，不需要引入其他任何 Netty 组件。