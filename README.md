# Local Deals Platform

本地生活服务后端，提供商铺浏览、附近商铺查询、探店笔记、关注动态、用户签到和优惠券秒杀等功能。

## 技术栈

- Java 8、Spring Boot 2.3、Maven
- MySQL、MyBatis-Plus
- Redis、Redisson、Lua
- Spring Kafka

## 本地运行

1. 启动 MySQL、Redis 和 Kafka。默认 Redis 地址为 `127.0.0.1:6379`，Kafka 地址为 `localhost:9092`。
2. 创建 MySQL 数据库 `hmdp`，导入 [初始化 SQL](src/main/resources/db/hmdp.sql)。
3. 按本机环境调整 [application.yaml](src/main/resources/application.yaml) 中的连接配置。也可以通过环境变量 `SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD` 指定数据库账号和密码。如需使用图片上传，需将 `SystemConstants.IMAGE_UPLOAD_DIR` 改为本机可写的目录。
4. 在项目根目录运行以下命令。服务默认监听 `8081` 端口。

   ```sh
   mvn spring-boot:run
   ```

## 功能实现

- 商铺查询使用 Redis 缓存，附近商铺查询使用 Redis GEO。
- 关注动态通过 Redis 收件箱分发，用户签到通过 Bitmap 记录。
- 秒杀请求使用 Redis Lua 脚本检查库存和重复下单，订单通过 Kafka 异步处理，并包含库存回滚逻辑。
