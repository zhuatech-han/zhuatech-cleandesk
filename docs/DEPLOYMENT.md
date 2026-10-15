# CleanDesk 部署与恢复 / Deployment
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 安装和升级

从README依赖版本开始，先创建私有`.env`再运行Compose。默认仅Web绑定本机8174，数据库不暴露宿主端口。首次初始化数据库卷可能较慢，先查看所选项目MySQL日志；`readiness`检查浏览器实际访问的Nginx→后端→数据库路径，不能只依据前端静态HTML的200判断完成。

```bash
docker compose -p cleandesk-local config --quiet
docker compose -p cleandesk-local up --build -d --wait --wait-timeout 3600
python3 scripts/readiness.py --base http://127.0.0.1:8174
docker compose -p cleandesk-local ps
```

端口冲突时先在本项目`.env`更换`WEB_PORT`，不要停止其他项目。日志仅供内部检查，不公开其中业务数据。Compose中的数据库URL和账号由服务环境注入；切换数据库凭据须按数据库自身流程执行，不是只改`.env`。

升级前备份，在隔离环境恢复验证，核对迁移兼容性再更新。保留数据卷，新增Flyway版本，不能改旧迁移。数据库回滚不是简单切回镜像；涉及结构变化须准备经过验证的备份恢复方案。`docker compose down`不加`-v`保留卷，`-v`会删除选定项目数据，仅能清理确认可丢弃的QA环境。

## HTTPS

在Nginx Web入口前部署受信任证书的HTTPS反向代理，`.env`设置`COOKIE_SECURE=true`。根据实际网络决定BIND_ADDRESS，防火墙只开放必要端口。不把开发服务器、MySQL、Docker socket或管理凭据公开。若本机HTTP无法登录，核对Secure cookie是否错误设为true。

## 备份

```bash
python3 scripts/backup.py --project cleandesk-local --output /private/backup/cleandesk.zip
```

脚本核对项目名和运行状态，暂停所选后端、导出单事务SQL（包含十六进制照片BLOB）、写校验和与0600归档，最后启动原后端。不覆盖已有归档，不处理其他项目。每成员最大1GiB；大数据需自行设计更大规模的备份与恢复。

## 空库恢复演练

```bash
python3 scripts/restore-env.py --source .env --output output/restore.env --port 8176
python3 scripts/restore.py /private/backup/cleandesk.zip --project cleandesk-restore --env-file output/restore.env
```

目标必须是尚不存在的独立容器和卷、本机绑定、不同端口。先验证归档结构和校验和，再启动空MySQL；在应用启动创建账号前导入SQL。恢复账号和密码哈希来自原库；不会重置管理员。先核对健康、登录、订单/照片/资金与人员结算，再决定是否替换业务环境。原环境保持不变。

备份不是公开源码附件，不能上传GitHub。QA恢复后可用`verify-persistence.py`复核10项；只适用于对应`quality.py`私有状态文件，不应用真实客户资料。

## 故障排查

| 现象 | 处理 |
| --- | --- |
| MySQL尚未健康 | 查看本项目日志与磁盘，等待初始化；不删除已有业务卷 |
| 页面可开但API失败 | 检查完整readiness与后端日志、数据库连接 |
| 迁移校验失败 | 不修改旧迁移或禁用校验；核对代码版本及迁移历史 |
| 401 | 检查账号/组织停用、密码重置或会话过期，重新登录 |
| 409 | 查看状态、版本、人员时段或重复凭证提示，刷新核对 |
| 照片超限 | 核对PNG/JPEG、8MB/4096维度、每轮12张和组织256MB上限 |

English: initialize a private environment, build with tests enabled, and verify gateway-to-database readiness. Keep database ports private and use HTTPS with Secure cookies. Back up before upgrades. Restore only trusted archives into a new isolated localhost deployment; original data remains untouched. SQL includes protected image BLOBs, and all backups remain private.
