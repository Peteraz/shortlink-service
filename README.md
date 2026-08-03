# shortlink-service

这是一个基于 Spring Boot 的单机内存短链服务。目前已完成项目基础结构和第二阶段的普通短链功能。

## 技术栈

- Java 21
- Spring Boot 3.3.5
- Spring Web
- Jakarta Validation
- Maven
- JUnit 5
- `ConcurrentHashMap` 内存存储
- `SecureRandom` Base62 短码生成

项目不使用 MySQL、Redis、MongoDB、Elasticsearch、消息队列、MyBatis 或 JPA。

## 环境要求

- JDK 21
- Maven 3.9+ 或兼容版本

## 配置

默认配置位于 `src/main/resources/application.yml`：

```yaml
short-link:
  domain: http://localhost:8080
  code-length: 6
```

短码长度只能配置为 6、7 或 8，应用启动时会校验配置。一次应用运行期间使用固定长度，不会在每次生成时随机选择长度。

## 执行测试和打包

```bash
mvn clean test
mvn package
```

## 启动项目

```bash
mvn spring-boot:run
```

默认端口为 `8080`。也可以运行打包后的 Jar：

```bash
java -jar target/shortlink-service-0.0.1-SNAPSHOT.jar
```

## 当前接口

### 创建普通短链

```http
POST /api/v1/short-links
Content-Type: application/json
```

请求示例：

```json
{
  "originalUrl": "https://example.com/article/1001",
  "channel": "wechat"
}
```

相同规范化长链接和相同渠道重复创建时返回已有短链；相同长链接使用不同渠道时生成不同短链。渠道不传或为空时使用 `default`。

### 查询普通短链详情

```http
GET /api/v1/short-links/{shortCode}
```

### 健康检查

```http
GET /api/health
```

响应：

```json
{
  "status": "UP"
}
```

## 当前已完成内容

- Spring Boot 启动类和 Maven 工程配置。
- `ShortLink`、`LinkType`、`LinkStatus` 领域模型。
- URL 校验与协议、Host 大小写规范化。
- 渠道清洗、默认渠道和参数校验。
- 普通短链按“规范化长链接 + 规范化渠道”幂等创建。
- Base62 候选短码生成、全局唯一保存和碰撞重试。
- 短链详情查询和统一响应结构。
- 基于 `ConcurrentHashMap` 的并发安全内存存储。
- 配置长度校验、单元测试、并发测试和 MockMvc 测试。

## 尚未实现

以下功能属于后续阶段，目前未实现：

- 盲盒短链生成和随机解析。
- 短链解析业务、访问次数累加和 HTTP 302 跳转。
- 主动标记断链。
- 原始链接可达性检测和批量检测。
- 根据短码、渠道、状态的条件查询。
