# shortlink-service

基于 Spring Boot 的单机内存短链服务，使用 Java 21、Spring Boot 3.3.5、Maven、Jakarta Validation 和 JUnit 5。

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

## curl 示例

```bash
# 检测单条短链，不改变状态
curl -X POST "http://localhost:8090/api/v1/short-links/abc123/health-check"

# 检测单条短链，全部原始 URL 不可达时自动标记断链
curl -X POST "http://localhost:8090/api/v1/short-links/abc123/health-check?markBroken=true"

# 批量检测；不存在的短码会作为单条失败结果返回
curl -X POST "http://localhost:8090/api/v1/short-links/batch-health-check" -H "Content-Type: application/json" -d '{"shortCodes":["abc123","def456"],"markBroken":false}'
```
