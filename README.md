# shortlink-service

基于 Spring Boot 的单机内存短链服务，使用 Java 21、Spring Boot 3.3.5、Maven、Jakarta Validation 和 JUnit 5。

## 技术栈和设计概览

- Spring Web、Jakarta Validation、JUnit 5、Spring MockMvc
- `ConcurrentHashMap` 内存存储
- Java 原生 `java.net.http.HttpClient`
- Base62 短码，默认 6 位，支持配置 6、7、8 位
- 普通短链使用 `normalizedUrl + "|" + normalizedChannel` 幂等
- 盲盒使用 `AtomicInteger + CAS` 扣减有效次数
- 访问次数使用 `AtomicLong`

当前基线默认短码为 6 位；7 位是可配置选项，不静默修改默认值。7 位拥有更大的 Base62 编码空间，更适合生产规模。

详细设计见 [docs/design.md](D:/Code/shortlink-service/docs/design.md)。

## 项目结构

```text
src/main/java/com/example/shortlink
├── config        配置、HttpClient 和健康检测线程池
├── controller    HTTP 接口
├── domain        短链领域对象和状态
├── dto           请求和响应 DTO
├── exception     业务异常和统一异常处理
├── generator     Base62 短码生成
├── health        健康检测和 SSRF 策略
├── mapper        领域对象到 DTO 的转换
├── repository    内存 Repository
├── selector      盲盒随机选择
├── service       短链业务和检测编排
└── validator     URL、渠道、短码校验
```

## 已实现能力

- 普通短链创建、详情查询和规范化 URL 校验
- 盲盒短链创建、均匀随机解析和 AtomicInteger CAS 次数扣减
- HTTP 302 短链跳转
- 解析详情查询（会真实解析并消耗次数）
- 主动标记断链和幂等重复标记
- 按短码、渠道、状态、类型过滤，并支持倒序排序和分页
- 原始长链接健康检测、自动标记断链和批量检测
- HEAD 优先、405 降级 GET，默认不自动跟随重定向
- SSRF 防护：拒绝本机、内网、链路本地、组播和非 HTTP(S) 地址
- ConcurrentHashMap 内存存储

## 接口

```text
POST  /api/v1/short-links
POST  /api/v1/short-links/blind-box
GET   /api/v1/short-links/{shortCode}
GET   /api/v1/short-links/{shortCode}/resolve
PATCH /api/v1/short-links/{shortCode}/broken
GET   /api/v1/short-links?shortCode=&channel=&status=&type=&page=0&size=20
POST  /api/v1/short-links/{shortCode}/health-check?markBroken=false
POST  /api/v1/short-links/batch-health-check
GET   /s/{shortCode}
GET   /api/health
```

`GET /api/v1/short-links/{shortCode}` 只查询详情，不增加解析次数。
`GET /api/v1/short-links/{shortCode}/resolve` 和 `GET /s/{shortCode}` 会执行真实解析。

健康检测使用 Java 17 原生 `HttpClient`。单条检测先发送 HEAD，收到 405 时降级为 GET；2xx 和 3xx 视为可达，客户端不自动跟随重定向。默认连接超时为 2 秒、单次请求超时为 3 秒。

批量检测最多接受 100 个短码，使用独立的有界线程池（核心线程 4、最大线程 8、队列 100、CallerRunsPolicy），不会使用 `ForkJoinPool.commonPool` 或 `parallelStream`。

健康检测会在请求前解析域名并拒绝 localhost、回环地址、私有地址、链路本地地址、组播地址、未指定地址及其他受限地址。DNS 解析失败也会作为不可达结果返回，不向客户端暴露服务器网络信息。

## 查询性能说明

查询使用内存全量扫描和分页，复杂度约为 O(n)，适用于笔试和小规模数据。生产环境应使用数据库索引或搜索引擎。

## 配置

```yaml
short-link:
  domain: http://localhost:8090
  code-length: 6
  health-check:
    connect-timeout-millis: 2000
    request-timeout-millis: 3000
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 100
    keep-alive-seconds: 60
```

短码长度支持 6、7、8 位，应用启动时校验配置。

## 测试和启动

```bash
mvn clean test
mvn package
mvn spring-boot:run
```

默认端口为 `8090`。

项目可以直接使用 `mvn spring-boot:run` 启动；也可以先执行 `mvn package`，再运行 `target/shortlink-service-0.0.1-SNAPSHOT.jar`。

## curl 示例

```bash
# 普通短链生成
curl -X POST "http://localhost:8090/api/v1/short-links" -H "Content-Type: application/json" -d '{"originalUrl":"https://example.com/article/1001","channel":"wechat"}'

# 盲盒短链生成
curl -X POST "http://localhost:8090/api/v1/short-links/blind-box" -H "Content-Type: application/json" -d '{"originalUrls":["https://example.com/a","https://example.com/b"],"channel":"wechat","validTimes":100}'

# 查询详情和执行解析
curl "http://localhost:8090/api/v1/short-links/abc123"
curl "http://localhost:8090/api/v1/short-links/abc123/resolve"

# 短链跳转，Location 在响应头中
curl -i "http://localhost:8090/s/abc123"

# 主动标记断链
curl -X PATCH "http://localhost:8090/api/v1/short-links/abc123/broken" -H "Content-Type: application/json" -d '{"reason":"运营人员主动下线"}'

# 检测单条短链，不改变状态
curl -X POST "http://localhost:8090/api/v1/short-links/abc123/health-check"

# 检测单条短链，全部原始 URL 不可达时自动标记断链
curl -X POST "http://localhost:8090/api/v1/short-links/abc123/health-check?markBroken=true"

# 批量检测；不存在的短码会作为单条失败结果返回
curl -X POST "http://localhost:8090/api/v1/short-links/batch-health-check" -H "Content-Type: application/json" -d '{"shortCodes":["abc123","def456"],"markBroken":false}'

# 条件查询
curl "http://localhost:8090/api/v1/short-links?channel=wechat&status=ACTIVE&type=NORMAL&page=0&size=20"
```

## 并发安全和随机性

- 短码占用使用 `putIfAbsent`，普通业务键使用 `computeIfAbsent`。
- 普通解析计数使用 `AtomicLong.incrementAndGet()`。
- 盲盒只有 CAS 成功扣减后才选择 URL，避免超发和负数。
- 批量检测使用核心线程 4、最大线程 8、容量 100 的有界线程池，并显式传入 `CompletableFuture`。
- 盲盒通过 `ThreadLocalRandom` 在候选下标范围内均匀选择；有限样本不保证完全平均。

## SSRF 安全说明

健康检测只允许 HTTP/HTTPS，并解析 host 后拒绝 localhost、回环、私有、链路本地、site-local、组播、未指定及 IPv4-mapped 私有地址。DNS 失败返回不可达，不暴露服务器网络信息。HttpClient 不自动跟随重定向，3xx 只表示原目标可达。

## 当前局限

- 数据只存在单个 JVM 内存中，重启会丢失数据。
- 条件查询是内存全量扫描，复杂度约为 O(n)。
- 多实例部署无法共享短码和盲盒次数。
- 当前没有认证、授权、限流、监控、告警和审计。
- 健康检测仍需在生产环境进一步处理 DNS rebinding、出口策略和检测审计。
- 当前未引入 Swagger/OpenAPI，README 和设计文档作为接口说明。

## 生产环境演进方案

当前不实际引入复杂组件，生产环境可按需增加：MySQL 持久化元数据、Redis 热点缓存、Snowflake 或号段模式全局 ID、ID 转 Base62、MQ 异步访问日志、ClickHouse/Elasticsearch 分析、定时断链检测、分布式锁或数据库乐观锁、多实例部署、限流监控告警和审计。
