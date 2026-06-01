可以，分开来看：

## 1. WSL 配置成桥接模式

### WSL2 默认网络模式

WSL2 默认使用 **NAT 网络**：

```text
Windows
  │
Hyper-V Virtual Switch (NAT)
  │
WSL2
```

特点：

* WSL 能访问外网
* Windows 能访问 WSL
* 局域网其它机器默认不能直接访问 WSL
* WSL IP 重启后可能变化

---

### 桥接模式是否可行

#### 旧方案（已不推荐）

以前有人通过 Hyper-V 的 External Virtual Switch 实现桥接：

```text
路由器
  │
交换机
  │
WSL2(独立IP)
```

但从 WSL 官方设计来看：

* WSL 不属于普通 Hyper-V 虚拟机
* 很多 Hyper-V 网络配置无法直接套用
* 升级后容易失效

---

### 当前推荐方案：Mirrored Networking

从 Windows 11 新版开始，Windows Subsystem for Linux 支持 **Mirrored Networking**。

编辑：

```ini
%UserProfile%\.wslconfig
```

```ini
[wsl2]
networkingMode=mirrored
```

然后：

```powershell
wsl --shutdown
```

重启 WSL。

验证：

```bash
ip addr
```

你会发现：

* WSL 能看到宿主机网卡
* 可以直接监听局域网地址
* 端口映射需求减少
* 网络体验接近桥接

官方实际上更推荐 Mirrored，而不是传统桥接。

---

## 2. 一台 Windows 配置多个 WSL 是否可行

完全可行。

查看已有发行版：

```powershell
wsl -l -v
```

例如：

```text
Ubuntu-22.04
Ubuntu-24.04
Debian
OpenSUSE
```

---

### 安装多个发行版

```powershell
wsl --install Ubuntu-22.04

wsl --install Debian
```

查看：

```powershell
wsl -l -v
```

结果：

```text
NAME            STATE     VERSION
Ubuntu-22.04    Running   2
Debian          Stopped   2
```

---

### 同一个发行版安装多份

例如要：

```text
Ubuntu-Dev
Ubuntu-Test
Ubuntu-Prod
```

可以导出再导入：

导出：

```powershell
wsl --export Ubuntu ubuntu.tar
```

导入：

```powershell
wsl --import Ubuntu-Dev D:\WSL\Ubuntu-Dev ubuntu.tar
wsl --import Ubuntu-Test D:\WSL\Ubuntu-Test ubuntu.tar
wsl --import Ubuntu-Prod D:\WSL\Ubuntu-Prod ubuntu.tar
```

查看：

```powershell
wsl -l -v
```

结果：

```text
Ubuntu
Ubuntu-Dev
Ubuntu-Test
Ubuntu-Prod
```

每个实例：

* 独立文件系统
* 独立用户
* 独立软件环境
* 独立 Docker 配置

类似多台 Linux 虚拟机。

---

## 3. 多个 WSL 共存时的网络

### NAT 模式

每个 WSL 会获得不同 IP：

```text
Ubuntu-Dev   172.28.1.10
Ubuntu-Test  172.28.1.11
Ubuntu-Prod  172.28.1.12
```

通过：

```bash
ip addr
```

查看。

---

### Mirrored 模式

配置：

```ini
[wsl2]
networkingMode=mirrored
```

后：

* 多个 WSL 共用宿主机网络环境
* 可以直接访问局域网资源
* 网络行为更接近真实 Linux 主机

---



## %UserProfile%\.wslconfig 不存在

如果你的目的是：

* Spring Boot 开发
* Docker 开发
* Kubernetes 学习
* 多环境隔离（dev/test/prod）

推荐：

```text
Windows 11
 ├── Ubuntu-Dev
 ├── Ubuntu-Test
 ├── Ubuntu-K8S
 └── Ubuntu-Docker
```

并开启：

```ini
[wsl2]
networkingMode=mirrored
```

这样比折腾传统桥接模式稳定得多，也更符合当前 WSL 的官方推荐用法。







这是正常情况。

`%UserProfile%\.wslconfig` **默认并不存在**，需要你自己创建。

### 方法1：资源管理器创建

按：

```text
Win + R
```

输入：

```text
%UserProfile%
```

例如会打开：

```text
C:\Users\你的用户名
```

然后查看是否有：

```text
.wslconfig
```

没有的话：

右键 → 新建 → 文本文档

命名为：

```text
.wslconfig
```

注意：

```text
不是 .wslconfig.txt
```

如果看不到扩展名，先开启：

```text
资源管理器
→ 查看
→ 显示
→ 文件扩展名
```

------

### 方法2：PowerShell 创建（推荐）

打开 PowerShell：

```powershell
notepad $env:USERPROFILE\.wslconfig
```

第一次会提示：

```text
文件不存在，是否创建？
```

选择：

```text
是(Y)
```

然后输入：

```ini
[wsl2]
networkingMode=mirrored
```

保存即可。

------

### 方法3：直接查看是否已经存在

```powershell
dir $env:USERPROFILE\.wslconfig
```

如果显示：

```text
找不到路径
```

说明文件不存在。

------

### 配置完成后

执行：

```powershell
wsl --shutdown
```

再启动 WSL：

```powershell
wsl
```

------

### 先确认你的 WSL 版本

因为 **Mirrored Networking 只支持较新的 WSL 版本**。

执行：

```powershell
wsl --version
```

把输出结果发给我，例如：

```text
WSL version: 2.4.13
Kernel version: ...
Windows version: ...
```

我可以帮你确认：

- 是否支持 `networkingMode=mirrored`
- 是否需要升级 WSL
- 你的 Windows 10/11 是否满足条件。



## 多 wsl ip问题

从你贴出来的信息看，你已经**成功启用了 Mirrored Networking**，而且运行正常。

关键证据：

```text
eth3
inet addr:192.168.3.12
Mask:255.255.255.0
```

如果还是传统 NAT 模式，WSL2 一般会拿到类似：

```text
172.20.x.x
172.28.x.x
172.30.x.x
```

这样的 Hyper-V 私网地址。

而你现在拿到的是：

```text
192.168.3.12
```

这是局域网地址，说明 WSL 已经镜像了宿主机网络。

------

### 验证是不是 Mirrored

Windows 执行：

```powershell
ipconfig
```

看看网卡 IP。

如果 Windows 也是：

```text
192.168.3.x
```

例如：

```text
192.168.3.10
```

而 WSL 是：

```text
192.168.3.12
```

这就是 Mirrored 模式的典型表现。

------

### 再验证局域网访问

在 WSL 中启动一个服务：

```bash
python3 -m http.server 8080
```

或者：

```bash
nc -l 8080
```

然后在另一台电脑访问：

```text
http://192.168.3.12:8080
```

如果能访问：

```text
WSL ←→ 局域网
```

已经完全打通。

------

### 关于你前面问的「多个 WSL」

你现在看到的是：

```text
docker-desktop
```

这实际上也是一个 WSL 发行版。

查看全部：

```powershell
wsl -l -v
```

应该类似：

```text
NAME                   STATE
Ubuntu-24.04           Running
docker-desktop         Running
docker-desktop-data    Running
```

------

### 多个 WSL 是否都能有自己的 IP？

分情况：

#### Mirrored 模式

多个发行版通常会看到相同的网络环境：

```text
Ubuntu           192.168.3.x
Debian           192.168.3.x
docker-desktop   192.168.3.x
```

它们共享宿主机网络栈特性。

因此：

```text
Ubuntu 监听 8080
Debian 也监听 8080
```

会发生端口冲突。

------

#### NAT 模式

每个发行版可能有不同虚拟 IP：

```text
Ubuntu   172.28.10.2
Debian   172.28.10.3
```

但外部无法直接访问。

------

### 如果你的目标是

#### 场景1：开发多个 SpringBoot

例如：

```text
Ubuntu-Dev
Ubuntu-Test
Ubuntu-Prod
```

直接：

```text
8081
8082
8083
```

区分端口即可。

这是最简单的方案。

------

#### 场景2：模拟多台 Linux 服务器

例如：

```text
192.168.3.101
192.168.3.102
192.168.3.103
```

那么 WSL 不太适合。

建议：

- Hyper-V VM
- VMware
- VirtualBox
- Proxmox

因为它们支持真正的桥接网卡和独立 IP。

------

你可以先执行：

```powershell
wsl -l -v
```

把结果发出来。

我可以进一步告诉你：

1. 你现在有几个 WSL 实例；
2. Docker Desktop 和 Ubuntu 的网络关系；
3. 多个 WSL 是否会发生端口冲突；
4. 如何让多个 SpringBoot 项目同时运行。





可以，而且对于国内网络环境或者 DNS 有问题的机器，**本地导入安装往往比 `wsl --install` 更稳定**。

你的错误：

```text
WININET_E_NAME_NOT_RESOLVED
无法解析服务器的名称或地址
```

说明 WSL 在访问 GitHub：

```text
https://raw.githubusercontent.com/microsoft/WSL/master/distributions/DistributionInfo.json
```

时 DNS 解析失败，不是 WSL 本身的问题。

------

# 方案1：下载 Ubuntu RootFS 后本地导入（推荐）

这是企业环境最常用的方法。

## 第一步：下载 Ubuntu RootFS

官方镜像：

### Ubuntu 22.04

```text
https://cloud-images.ubuntu.com/wsl/jammy/current/
```

下载：

```text
ubuntu-jammy-wsl-amd64-wsl.rootfs.tar.gz
```

或者：

```text
ubuntu-jammy-wsl-amd64-root.tar.gz
```

------

## 第二步：创建安装目录

例如：

```powershell
mkdir D:\WSL\Ubuntu2204
```

------

## 第三步：导入

假设下载到：

```text
D:\Download\ubuntu-jammy-wsl.rootfs.tar.gz
```

执行：

```powershell
wsl --import Ubuntu-22.04 D:\WSL\Ubuntu2204 D:\Download\ubuntu-jammy-wsl.rootfs.tar.gz
```

成功后：

```powershell
wsl -l -v
```

会出现：

```text
Ubuntu-22.04
```

------

## 第四步：启动

```powershell
wsl -d Ubuntu-22.04
```

第一次进入：

```bash
root@
```

因为导入方式默认登录 root。

------

# 方案2：从已有 WSL 克隆

如果你机器里已经有：

```text
Ubuntu
```

或者同事给你一个 tar 包。

导出：

```powershell
wsl --export Ubuntu ubuntu.tar
```

得到：

```text
ubuntu.tar
```

------

导入：

```powershell
wsl --import Ubuntu-Test D:\WSL\Ubuntu-Test ubuntu.tar
```

这样就得到第二套 Ubuntu。

------

# 方案3：离线安装 Appx

微软商店里的 Ubuntu 本质是一个 Appx 包。

可以下载：

- Ubuntu 20.04
- Ubuntu 22.04
- Ubuntu 24.04

离线包。

例如：

```powershell
Add-AppxPackage Ubuntu2204.appx
```

然后：

```powershell
ubuntu2204.exe
```

初始化即可。

不过现在我更推荐 RootFS 导入，因为：

- 不依赖微软商店
- 不依赖 GitHub
- 不依赖网络
- 可以复制到任何机器

------

# 查看你已有的 WSL

建议先执行：

```powershell
wsl -l -v
```

把结果贴出来。

如果你已经有：

```text
docker-desktop
docker-desktop-data
```

那么实际上你的 WSL2 环境已经完全正常。

我还可以告诉你如何：

1. 从 Docker Desktop 的 Linux 环境快速生成一个 Ubuntu RootFS；
2. 在**完全离线**情况下创建多个 Ubuntu（Dev/Test/Prod）实例；
3. 给每个实例设置不同用户名，而不是默认 root。
