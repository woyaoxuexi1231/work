# Docker 部署 Windows 10（使用本地 ISO，不联网下载）

## 1. 删除旧容器

```bash
docker rm -f win10
```

---

## 2. 准备 ISO

假设 ISO 文件位于：

```text
C:\Users\15434\Desktop\iso\Win10_22H2_Chinese_x64.iso
```

Git Bash：

```bash
mkdir -p /c/Users/15434/Desktop/iso
```

将 ISO 放入该目录。

---

## 3. 创建并启动容器



powerShell

```powershell
docker run -d `
  --name win10 `
  --privileged `
  -e VERSION=10 `
  -e RAM_SIZE=1536M `
  -e CPU_CORES=1 `
  -e DISK_SIZE=40G `
  -p 3389:3389 `
  -p 8006:8006 `
  -v "C:\Users\15434\Desktop\iso\Windows-ch-origin.iso:/boot.iso" `
  -v "C:\Users\15434\Desktop\share:/shared" `
  dockurr/windows
```



---

## 4. 查看安装日志

```bash
docker logs -f win10
```

等待出现：

```text
Windows is ready
```

按：

```text
Ctrl + C
```

退出日志查看。

---

## 5. 使用 RDP 连接

运行：

```text
mstsc
```

连接地址：

```text
127.0.0.1:3389
```

用户名：

```text
admin
```

密码：

```text
Admin@123
```

---

## 6. 使用网页桌面

浏览器打开：

```text
http://127.0.0.1:8006
```

用户名：

```text
admin
```

密码：

```text
Admin@123
```

---

## 7. 常用运维命令

停止：

```bash
docker stop win10
```

启动：

```bash
docker start win10
```

重启：

```bash
docker restart win10
```

查看日志：

```bash
docker logs -f win10
```

删除：

```bash
docker rm -f win10
```

查看状态：

```bash
docker ps -a
```

---

## 8. ISO 目录检查

进入容器：

```bash
docker exec -it win10 bash
```

查看：

```bash
ls -lh /storage
```

应看到：

```text
Win10_22H2_Chinese_x64.iso
```

否则说明挂载路径错误。

---

## 9. 局域网访问

如果宿主机 IP 为：

```text
192.168.3.100
```

则其它电脑可连接：

RDP：

```text
192.168.3.100:3389
```

网页桌面：

```text
http://192.168.3.100:8006
```
