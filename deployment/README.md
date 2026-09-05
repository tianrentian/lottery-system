# 云服务器部署说明

这份 Compose 面向一台 2 核 2G 的低访问量演示服务器，同时运行：

- 抽奖系统（Spring Boot）
- AI 活动策划与通知文案服务（FastAPI、LangChain、LangGraph）
- 五子棋（Spring Boot）
- MySQL、Redis、RabbitMQ

两个项目共用一个 MySQL 容器，但分别使用 `lottery_system`、`java_gobang` 数据库和各自的数据库账号。Redis 与 RabbitMQ 只由抽奖系统使用。

## 1. 服务器准备

服务器只需安装 Docker 和 Docker Compose 插件，不需要另外安装 Java、Maven、Python、MySQL、Redis 或 RabbitMQ。

2G 内存建议增加 1G Swap，避免构建镜像或偶发峰值时直接触发系统内存不足。先用 `swapon --show` 检查；没有 Swap 时只执行一次：

```bash
sudo fallocate -l 1G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

云服务器安全组只需放行：

- `22`：SSH，建议仅允许自己的公网 IP
- `8080`：抽奖系统
- `8081`：五子棋

不要对公网开放 `3306`、`6379`、`5672`、`8090` 或 `15672`。

## 2. 放置项目

两个仓库必须放在同一层目录，因为 Compose 会从相邻目录构建五子棋镜像：

```text
/opt/lottery-stack/
├── lottery-system/
└── java_gobang/
```

例如：

```bash
sudo mkdir -p /opt/lottery-stack
sudo chown "$USER":"$USER" /opt/lottery-stack
cd /opt/lottery-stack
git clone https://github.com/tianrentian/lottery-system.git
git clone https://github.com/tianrentian/java_gobang.git
```

## 3. 配置环境变量

```bash
cd /opt/lottery-stack/lottery-system/deployment
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，至少填写：

- `MYSQL_ROOT_PASSWORD`
- `LOTTERY_DB_PASSWORD`
- `GOBANG_DB_PASSWORD`
- `RABBITMQ_PASSWORD`
- `DEEPSEEK_API_KEY`

AI Planner 与五子棋共用同一个 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL` 和 `DEEPSEEK_MODEL_NAME`。抽奖 Java 服务不直接访问 DeepSeek，而是通过内部网络调用 AI Planner。真实 `.env` 已被 Git 忽略，不能提交到仓库。

抽奖通知的可选配置：

- 邮件：`MAIL_USERNAME` 与 `MAIL_AUTH_CODE`
- 钉钉：`DINGTALK_WEBHOOK_URL`

不配置这些通知渠道不会影响活动创建和抽奖主流程，但对应的外部通知无法送达。

## 4. 启动与验收

先检查配置，再构建和启动：

```bash
cd /opt/lottery-stack/lottery-system/deployment
docker compose config --quiet
COMPOSE_PARALLEL_LIMIT=1 docker compose build
docker compose up -d
docker compose ps
```

`COMPOSE_PARALLEL_LIMIT=1` 会让低内存服务器按顺序构建镜像，避免两个 Java 项目同时执行 Maven。所有长期运行服务应显示 `Up`，带健康检查的服务最终应显示 `healthy`。首次构建会下载镜像与依赖，2 核 2G 服务器可能需要十几分钟。

访问地址：

```text
http://服务器公网IP:8080  抽奖系统
http://服务器公网IP:8081  五子棋
```

抽奖系统首次演示建议按下面顺序检查：

1. 使用管理员账号 `test@example.com`、密码 `123456` 登录。
2. 奖品列表应有 12 个奖品，价格从 19 元到 3999 元且图片可见。
3. 创建活动时应能圈选 20 名演示参与人员。
4. 使用 AI 活动策划助手生成并回填方案。
5. 圈选人数不少于奖品总份数，然后创建并完成抽奖。

演示参与人员使用 `example.com` 保留域名，不会收到真实邮件。需要验证邮件发送时，请在页面额外注册一个自己的真实邮箱；钉钉通知可通过真实 webhook 单独验证。

## 5. 演示数据与持久化

全新 MySQL 数据卷第一次启动时自动创建两个数据库，并向抽奖系统写入：

- 1 个管理员账号
- 20 名演示参与人员
- 12 个低、中、高价位奖品

`prize-images-init` 会把演示图片复制到 `prize_images` 持久化卷。之后管理员上传的图片也保存在该卷中。

MySQL 初始化脚本只在空数据卷第一次启动时执行。普通更新不要执行 `docker compose down -v`，否则会删除数据库、Redis、RabbitMQ 和奖品图片数据卷。

## 6. 日常命令

查看状态和资源：

```bash
docker compose ps
docker stats
```

查看日志：

```bash
docker compose logs -f --tail=200 lottery-app
docker compose logs -f --tail=200 ai-planner
docker compose logs -f --tail=200 gobang-app
```

更新代码并重建：

```bash
cd /opt/lottery-stack/lottery-system
git pull
cd ../java_gobang
git pull
cd ../lottery-system/deployment
COMPOSE_PARALLEL_LIMIT=1 docker compose build
docker compose up -d
```

停止服务但保留数据：

```bash
docker compose down
```

## 7. 备份数据库

更新部署配置或迁移服务器前先备份：

```bash
cd /opt/lottery-stack/lottery-system/deployment
mkdir -p backups
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases lottery_system java_gobang' > "backups/databases-$(date +%Y%m%d-%H%M%S).sql"
```

备份文件会留在服务器的 `deployment/backups` 目录。不要把包含真实用户数据的备份提交到 Git。

## 8. 2 核 2G 资源策略

Compose 给抽奖 Java 容器设置 576M 上限、五子棋 Java 容器设置 512M 上限，并把两个 JVM 的最大堆都限制为 256M，为元空间、线程栈和本地库保留余量。容器达到内存上限时不会自动“重新部署”；如果持续超限，Docker 可能终止该容器，`restart: unless-stopped` 随后会尝试重新启动。因此上线后应使用 `docker stats` 观察：若某个长期运行服务经常超过其上限的 80%，再针对它调高限制或减少同时运行的服务。

抽奖系统的生产线程池通过 Compose 设置为核心 2、最大 4、队列 20，与 2 核服务器匹配；本地 IDEA 配置保持不变。日志按单文件 10M、最多 3 个文件轮转，避免长期演示把磁盘写满。
