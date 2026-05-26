# Docker MySQL 主从架构配置指南

## 一、环境说明

| 项目 | 值 |
|------|-----|
| MySQL 版本 | 8.0.35 |
| 主库端口 | 3307 |
| 从库端口 | 3308 |
| Root 密码 | 123456 |
| 主库 Server ID | 1 |
| 从库 Server ID | 2 |

---

## 二、快速启动

```bash
# 进入脚本目录
cd /home/hulei/work/src/main/java/work/N3mysql

# 全新安装（清除旧数据）
sudo bash mysql_simple_start.sh clean

# 正常启动（保留数据）
sudo bash mysql_simple_start.sh
```

---

## 三、验证 MySQL 运行状态

```bash
# 查看容器状态
docker ps | grep mysql

# 查看主库日志
docker logs mysql-master

# 查看从库日志
docker logs mysql-slave
```

---

## 四、手动配置主从复制

### 4.1 连接到主库

```bash
docker exec -it mysql-master mysql -uroot -p123456
```

### 4.2 在主库上执行以下 SQL

```sql
-- 1. 创建复制用户
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED WITH mysql_native_password BY 'repl_pass123';

-- 2. 授予复制权限
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl_user'@'%';

-- 3. 刷新权限
FLUSH PRIVILEGES;

-- 4. 查看主库状态（记录 File 和 Position）
SHOW MASTER STATUS;
```

**执行结果示例：**

```
+---------------+----------+--------------+------------------+-------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+---------------+----------+--------------+------------------+-------------------+
| mysql-bin.000 |  1234    |              |                  |                   |
+---------------+----------+--------------+------------------+-------------------+
```
> ⚠️ **重要**：记录下 `File` 和 `Position` 的值，后面配置从库时需要用到

### 4.3 连接到从库

```bash
docker exec -it mysql-slave mysql -uroot -p123456
```

### 4.4 在从库上执行以下 SQL

```sql
-- 1. 停止从库复制
STOP SLAVE;

-- 2. 配置主库连接信息（根据实际值修改）
-- MASTER_LOG_FILE: 填入主库的 File 值
-- MASTER_LOG_POS: 填入主库的 Position 值
CHANGE MASTER TO
  MASTER_HOST='192.168.2.59',
  MASTER_PORT=3307,
  MASTER_USER='repl_user',
  MASTER_PASSWORD='repl_pass123',
  MASTER_LOG_FILE='mysql-bin.000003',
  MASTER_LOG_POS=871;

-- 3. 启动从库复制
START SLAVE;

-- 4. 查看从库状态
SHOW SLAVE STATUS\G
```

### 4.5 检查主从同步状态

```sql
-- 在从库执行
SHOW SLAVE STATUS\G
```

**正常状态的关键指标：**
```
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
```
> 如果 `Slave_IO_Running` 或 `Slave_SQL_Running` 不是 `Yes`，查看 `Last_Error` 字段

---

## 五、验证主从同步

### 5.1 在主库创建测试数据

```bash
docker exec -it mysql-master mysql -uroot -p123456 -e "
CREATE DATABASE IF NOT EXISTS test_db;
USE test_db;
CREATE TABLE IF NOT EXISTS user (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO user (name) VALUES ('张三'), ('李四'), ('王五');
SELECT * FROM user;
"
```

### 5.2 在从库验证数据同步

```bash
docker exec -it mysql-slave mysql -uroot -p123456 -e "
USE test_db;
SELECT * FROM user;
"
```

应该能看到主库插入的三条数据。

---

## 六、常用管理命令

### 6.1 主库操作

```bash
# 连接主库
docker exec -it mysql-master mysql -uroot -p123456

# 查看从库列表
SHOW SLAVE HOSTS;

# 查看二进制日志
SHOW BINARY LOGS;

# 查看主库状态
SHOW MASTER STATUS;
```

### 6.2 从库操作

```bash
# 连接从库
docker exec -it mysql-slave mysql -uroot -p123456

# 查看从库状态
SHOW SLAVE STATUS\G

# 停止从库复制
STOP SLAVE;

# 启动从库复制
START SLAVE;

# 重置从库配置
RESET SLAVE ALL;
```

### 6.3 主从切换（从库升级为主库）

```sql
-- 在从库执行
STOP SLAVE;
RESET SLAVE ALL;

-- 在新主库开启 binlog：
SET GLOBAL read_only = OFF;
FLUSH PRIVILEGES;
```

---

## 七、故障排除

### 问题1：从库连接不上主库

```sql
-- 检查主库是否创建了复制用户
SELECT user, host FROM mysql.user WHERE user='repl_user';
```

### 问题2：主从数据不一致

```sql
-- 在从库跳过错误（谨慎使用）
STOP SLAVE;
SET GLOBAL sql_slave_skip_counter = 1;
START SLAVE;
```

### 问题3：Slave_IO_Running 为 Connecting

```bash
# 检查网络连通性
docker exec mysql-slave ping host.docker.internal

# 或使用主库 IP
docker inspect mysql-master | grep IPAddress
```

然后重新配置：
```sql
STOP SLAVE;
CHANGE MASTER TO MASTER_HOST='172.17.0.2';  -- 替换为实际 IP
START SLAVE;
```

### 问题4：重置主从关系

```sql
-- 在从库执行
STOP SLAVE;
RESET SLAVE ALL;

-- 在主库执行
RESET MASTER;
```

然后重新执行第四步的配置。

---

## 八、安全建议

1. **修改 Root 密码**
   ```sql
   ALTER USER 'root'@'%' IDENTIFIED BY 'YourStrongPassword123!';
   ```

2. **删除测试用户**
   ```sql
   DELETE FROM mysql.user WHERE User='';
   FLUSH PRIVILEGES;
   ```

3. **限制复制用户 IP**
   ```sql
   -- 将 '%' 改为具体 IP
   DROP USER 'repl_user'@'%';
   CREATE USER 'repl_user'@'172.17.0.%' IDENTIFIED BY 'repl_pass123';
   GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl_user'@'172.17.0.%';
   FLUSH PRIVILEGES;
   ```

---

## 九、一键配置脚本

将以下内容保存为 `setup_replication.sh`，执行后自动配置主从：

```bash
#!/bin/bash
# 主从复制一键配置脚本

MASTER_FILE=$(docker exec mysql-master mysql -uroot -p123456 -N -e "SHOW MASTER STATUS" | awk '{print $1}')
MASTER_POS=$(docker exec mysql-master mysql -uroot -p123456 -N -e "SHOW MASTER STATUS" | awk '{print $2}')

echo "主库 File: $MASTER_FILE"
echo "主库 Position: $MASTER_POS"

docker exec mysql-slave mysql -uroot -p123456 -e "
STOP SLAVE;
CHANGE MASTER TO
  MASTER_HOST='host.docker.internal',
  MASTER_PORT=3307,
  MASTER_USER='repl_user',
  MASTER_PASSWORD='repl_pass123',
  MASTER_LOG_FILE='$MASTER_FILE',
  MASTER_LOG_POS=$MASTER_POS;
START SLAVE;
"

echo "主从配置完成，检查状态："
docker exec mysql-slave mysql -uroot -p123456 -e "SHOW SLAVE STATUS\G" | grep -E "Slave_IO_Running|Slave_SQL_Running"
```
