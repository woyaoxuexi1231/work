# Nginx 日志采集器 - Python 方案

## 📋 简介

本方案使用 Python 脚本读取 Nginx 日志文件，然后写入 MySQL 数据库。适用于标准 Nginx（不支持 Lua）或不想安装 OpenResty 的场景。

## 📁 文件说明

| 文件 | 作用 | 是否必需 |
|------|------|----------|
| `nginx.conf` | Nginx 配置文件 | ✅ 必需 |
| `nginx_log_collector_direct.py` | Python 日志采集脚本 | ✅ 必需 |
| `config.json` | 配置文件（数据库连接等） | ✅ 必需 |
| `start_collector.sh` | Linux 启动脚本 | ✅ 必需 |
| `nginx-log-collector.service` | systemd 服务配置 | ✅ 必需 |
| `README.md` | 本说明文档 | 📖 参考 |

## 🚀 安装步骤（适用于源码安装的 Nginx）

### 如果你已经有 Nginx（从源码安装）

假设你已经将 Nginx 源码解压到 `/home/hulei/nginx/nginx-1.28.3`：

#### 第一步：编译安装 Nginx

```bash
# 进入源码目录
cd /home/hulei/nginx/nginx-1.28.3

# 安装依赖
sudo apt update
sudo apt install -y build-essential libpcre3 libpcre3-dev zlib1g zlib1g-dev openssl libssl-dev

# 配置编译选项（指定安装目录）
./configure --prefix=/opt/nginx \
            --with-http_ssl_module \
            --with-http_v2_module \
            --with-http_realip_module \
            --with-http_stub_status_module \
            --with-threads

# 编译安装
make -j$(nproc)
sudo make install

# 验证安装
/opt/nginx/sbin/nginx -v
```

#### 第二步：创建必要目录

```bash
# 创建日志和运行目录
sudo mkdir -p /var/log/nginx
sudo mkdir -p /var/run/nginx

# 创建前端文件目录
sudo mkdir -p /opt/nginx/html/let-it-cook-admin
sudo mkdir -p /opt/nginx/html/let-it-cook-client
sudo cp -r /home/hulei/nginx/admin/* /opt/nginx/html/let-it-cook-admin/
sudo cp -r /home/hulei/nginx/client/* /opt/nginx/html/let-it-cook-client/

# 设置权限
sudo chown -R www-data:www-data /opt/nginx
sudo chown -R www-data:www-data /var/log/nginx
```

#### 第三步：复制配置文件

假设你把本项目下载到了 `/home/hulei/nginx` 目录：

```bash
sudo cp /home/hulei/nginx/ip-widget.css /opt/nginx/html
sudo cp /home/hulei/nginx/ip-widget.js /opt/nginx/html

# 复制 Nginx 配置（覆盖默认配置）
sudo cp /home/hulei/nginx/log-py/nginx.conf /opt/nginx/conf/nginx.conf

# 复制 Python 脚本和配置
sudo cp /home/hulei/nginx/log-py/nginx_log_collector_direct.py /opt/nginx/conf/
sudo cp /home/hulei/nginx/log-py/config.json /opt/nginx/conf/

# 复制启动脚本
sudo cp /home/hulei/nginx/log-py/start_collector.sh /opt/nginx/conf/

# 复制 systemd 服务文件
sudo cp /home/hulei/nginx/log-py/nginx-log-collector.service /etc/systemd/system/

# 复制数据库建表SQL
sudo cp /home/hulei/nginx/create_nginx_access_log_table.sql /opt/nginx/conf/
```

#### 第四步：配置数据库连接

编辑配置文件，修改数据库连接信息：

```bash
sudo nano /opt/nginx/conf/config.json
```

修改以下内容为你的数据库信息：

```json
{
  "mysql": {
    "host": "192.168.3.100",    // 改为你的 MySQL 地址
    "port": 3306,
    "user": "root",              // 改为你的用户名
    "password": "123456",        // 改为你的密码
    "database": "test"           // 改为你的数据库名
  }
}
```

按 `Ctrl+X`，然后按 `Y` 保存，按 `Enter` 退出。

#### 第五步：创建数据库表

```bash
# 登录 MySQL 创建表
mysql -u root -p123456 test < /opt/nginx/conf/create_nginx_access_log_table.sql

# 如果提示密码错误，用这个命令（会提示输入密码）
mysql -u root -p test < /opt/nginx/conf/create_nginx_access_log_table.sql
```

#### 第六步：创建 systemd 服务

**创建 Nginx 服务文件：**

```bash
sudo vim /etc/systemd/system/nginx.service
```

粘贴以下内容：

```ini
[Unit]
Description=Let-It-Cook Nginx Service
After=network.target mysql.service

[Service]
Type=forking
User=root
Group=root
WorkingDirectory=/opt/nginx
ExecStart=/opt/nginx/sbin/nginx -c /opt/nginx/conf/nginx.conf -p /opt/nginx
ExecReload=/opt/nginx/sbin/nginx -s reload
ExecStop=/opt/nginx/sbin/nginx -s stop
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

按 `Ctrl+X`，然后按 `Y` 保存，按 `Enter` 退出。

**启动服务：**

```bash
# 安装 Python MySQL 库
sudo apt install -y python3 python3-pip
pip3 install pymysql

# 重载 systemd
sudo systemctl daemon-reload

# 启动 Nginx 服务并设置开机自启
sudo systemctl start nginx
sudo systemctl enable nginx

sudo systemctl restart nginx

# 启动 Python 采集器服务并设置开机自启
sudo systemctl start nginx-log-collector
sudo systemctl enable nginx-log-collector



# 重启采集器服务
sudo systemctl restart nginx-log-collector

# 查看服务状态
sudo systemctl status nginx-log-collector

# 查看日志
journalctl -u nginx-log-collector -f
```

#### 第七步：验证安装

```bash
# 检查 Nginx 状态
sudo systemctl status nginx

# 检查 Python 采集器状态
sudo systemctl status nginx-log-collector

# 测试 Nginx 配置
/opt/nginx/sbin/nginx -t -c /opt/nginx/conf/nginx.conf -p /opt/nginx

# 访问测试页面（打开浏览器访问 http://你的服务器IP:9567）
curl http://localhost:9567
```

#### 第八步：放入前端代码

把前端打包好的文件放到以下目录：

```bash
sudo cp -r /home/hulei/nginx/index.html /opt/nginx/html/

# Admin 管理端
# 把 admin/dist 目录的内容复制到这里
sudo cp -r /home/hulei/nginx/admin/* /opt/nginx/html/let-it-cook-admin/

# Client 客户端
# 把 client/dist 目录的内容复制到这里
sudo cp -r /home/hulei/nginx/client/* /opt/nginx/html/let-it-cook-client/
```

---

### 如果你还没有 Nginx（使用 apt 安装，适合新手）

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Nginx 和 Python
sudo apt install -y nginx python3 python3-pip

# 安装 Python MySQL 库
pip3 install pymysql

# 创建目录
sudo mkdir -p /opt/nginx/{conf,html}
sudo mkdir -p /var/log/nginx

# 设置权限
sudo chown -R www-data:www-data /opt/nginx
```

然后继续上面的**第三步：复制配置文件**开始操作。

---

## ✅ 验证日志入库

```bash
# 查看最新的日志记录
mysql -u root -p123456 test -e "SELECT * FROM nginx_access_log ORDER BY create_time DESC LIMIT 5;"

# 查看今日访问量
mysql -u root -p123456 test -e "SELECT COUNT(*) FROM nginx_access_log WHERE DATE(create_time) = CURDATE();"
```

## 🔧 常用命令

```bash
# 启动服务
sudo systemctl start nginx
sudo systemctl start nginx-log-collector

# 停止服务
sudo systemctl stop nginx
sudo systemctl stop nginx-log-collector

# 重启服务
sudo systemctl restart nginx
sudo systemctl restart nginx-log-collector

# 查看状态
sudo systemctl status nginx
sudo systemctl status nginx-log-collector

# 查看日志
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/access_json.log
journalctl -u nginx-log-collector -f
```

## ❌ 常见问题

### 问题1：服务启动失败

```bash
# 查看错误日志
journalctl -u nginx-log-collector -n 20

# 检查配置文件
cat /opt/nginx/conf/config.json

# 测试数据库连接
mysql -h 192.168.3.100 -u root -p123456 -e "SELECT 1;"
```

### 问题2：日志没有入库

```bash
# 检查日志文件是否有内容
tail -f /var/log/nginx/access_json.log

# 检查采集器是否在运行
ps aux | grep nginx_log_collector

# 查看采集器日志
journalctl -u nginx-log-collector -f
```

### 问题3：Nginx 配置错误

```bash
# 测试配置
/opt/nginx/sbin/nginx -t -c /opt/nginx/conf/nginx.conf -p /opt/nginx

# 重新加载配置
sudo systemctl reload nginx
```

### 问题4：找不到 nginx 命令

```bash
# 添加到 PATH（临时）
export PATH=/opt/nginx/sbin:$PATH

# 添加到 PATH（永久）
echo 'export PATH=/opt/nginx/sbin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

## 📂 目录结构

```
/opt/nginx/
├── sbin/
│   └── nginx                    # Nginx 可执行文件
├── conf/
│   ├── nginx.conf                    # Nginx 配置
│   ├── nginx_log_collector_direct.py # Python 脚本
│   ├── config.json                   # 配置文件
│   └── start_collector.sh            # 启动脚本
└── html/
    ├── let-it-cook-admin/            # Admin 前端
    └── let-it-cook-client/           # Client 前端
```

## 📝 注意事项

1. 确保 MySQL 服务已启动：`sudo systemctl start mysql`
2. 确保防火墙开放 9567 端口：`sudo ufw allow 9567/tcp`
3. 数据库用户需要有插入权限
4. 定期清理旧日志避免磁盘空间不足
