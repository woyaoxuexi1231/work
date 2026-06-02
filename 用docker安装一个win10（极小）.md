# Docker 部署轻量Win10（RDP远程+自定义账号密码、无需挂载目录、宿主机7890代理、仅跑脚本）完整教程
> 环境：Windows + Docker Desktop + GitBash/MINGW64，镜像：dockurr/windows，RDP传文件替代挂载共享文件夹
## 一、前置清理（已有旧容器必执行）
```bash
# 删除之前创建失败的win10容器
docker rm -f win10
```

## 二、一键创建容器命令（直接复制GitBash运行）
```bash
docker run -d \
--name=win10 \
--privileged \
-e VERSION=10 \
-e RAM=2G \
-e CPU=1 \
-e USERNAME=admin \
-e PASSWORD=Admin@123 \
-e LANGUAGE=Chinese \
-p 3389:3389 \
-p 8006:8006 \
dockurr/windows
```


### 参数说明

1. `USERNAME=admin` 远程登录用户名；`PASSWORD=Admin@123` 登录密码（带符号避免系统密码策略拦截）
2. `RAM=2G CPU=1` 低配，满足运行exe/python脚本
3. `host.docker.internal:7890` 容器内访问本机Clash代理
4. 3389=RDP远程桌面端口；8006=网页VNC备用桌面
5. **无-v挂载参数，全程RDP自带磁盘共享传文件**

## 三、等待系统自动部署（关键步骤）
1. 查看部署日志，观察安装进度
```bash
docker logs -f win10
```
2. 出现 `Windows is ready` 字样代表系统安装完成（首次下载+安装约8~15分钟，取决于网速），**没就绪前无法远程连接**
3. 按 `Ctrl+C` 退出日志查看

## 四、两种远程连接方式
### 方式1：Windows自带RDP远程桌面（首选，可双向传文件）
1. Win+R 输入 `mstsc` 打开远程桌面
2. 计算机：`127.0.0.1:3389`
3. 点击【显示选项】→【本地资源】→【更多】
4. 展开驱动器，勾选本机需要共享的磁盘（C盘/桌面所在盘）→确定
5. 点击连接，账号：`admin`，密码：`Admin@123`
6. 进入Win10后，此电脑内出现「来自DESKTOP-xxx的磁盘」，**直接复制粘贴实现本机↔虚拟机双向传文件**

### 方式2：浏览器网页VNC（备用，不用装客户端）
浏览器打开：`http://localhost:8006`，同账号密码登录，网页端支持上传单个文件。

## 五、容器日常运维命令
```bash
# 停止win10
docker stop win10
# 开机启动
docker start win10
# 彻底删除容器（数据清空，重装用）
docker rm -f win10
# 查看实时运行日志
docker logs -f win10
```

## 六、脚本运行使用流程
1. RDP打开远程桌面，通过共享磁盘把exe/python脚本复制到Win10桌面
2. 在容器Win10内直接双击/CMD运行脚本即可
3. 脚本产生的结果文件，再通过共享磁盘拷回本机

## 七、常见报错处理
1. 连不上RDP：查看`docker logs win10`，没出现`Windows is ready`就继续等待部署；
2. 容器内无法走代理：确认本机Clash开启并监听7890端口；
3. 拉取镜像超时：Docker Desktop→设置→Docker Engine配置国内镜像加速器。