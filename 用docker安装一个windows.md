下面给你一个**直接能用、带桌面、能随便传文件、支持 RDP + 网页访问、带 7890 代理**的 Windows 容器方案，一步到位。

---

## 一、用的镜像：**dockur/windows**（带完整桌面，像虚拟机）
特点：
- ✅ 真 Windows 10/11 桌面
- ✅ 支持 Web 桌面（浏览器直接用）
- ✅ 支持 RDP 远程桌面
- ✅ 支持 **宿主机文件夹直接挂载**（传文件最方便）
- ✅ 支持你要的 **7890 代理**

---

## 二、一键启动（带文件共享 + 代理）
### 1）先建一个本地共享文件夹（用来双向传文件）
比如在你电脑 D 盘建：
```
D:\docker-share
```

### 2）直接复制下面命令到 PowerShell 执行
```powershell
docker run -d `
  --name=win11 `
  --privileged `
  -e RAM=4G `
  -e CPU=2 `
  -e DISK_SIZE=60G `
  -e HTTP_PROXY=http://127.0.0.1:7890 `
  -e HTTPS_PROXY=http://127.0.0.1:7890 `
  -p 8006:8006 `
  -p 3389:3389 `
  -v D:\docker-share:/host-share `
  dockur/windows:11
```

解释：
- `-v D:\docker-share:/host-share`：把你电脑 D:\docker-share **直接映射到 Windows 容器里的 Z 盘或 /host-share**，**丢文件进去双方都能看见**
- 自动带 7890 代理
- 内存 4G、硬盘 60G、2 核 CPU（日常够用）

---

## 三、怎么进桌面
### 方式 1：浏览器直接用（最简单）
打开：
```
http://localhost:8006
```
进去就是 Windows 11 桌面。

### 方式 2：用 RDP 远程桌面
地址：`localhost:3389`  
账号密码默认都是：`administrator`

---

## 四、**传文件（三种方法，推荐前两种）**
### ✅ 方法 A：共享文件夹（最爽，双向实时）
- 你电脑：把文件丢进 **D:\docker-share**
- 容器 Windows：打开 **文件资源管理器 → Z 盘（或 \\host-share）**，直接看到文件
- 反过来也能拖回去

### ✅ 方法 B：docker cp（临时传单个文件）
```powershell
# 主机 → 容器
docker cp D:\test.txt win11:C:\Users\Administrator\Desktop\

# 容器 → 主机
docker cp win11:C:\Users\Administrator\Desktop\test.txt D:\
```

### ✅ 方法 C：网页桌面直接上传
在 http://localhost:8006 里，点右上角文件上传，直接传进桌面。

---

## 五、网络代理（7890）
容器里的 Windows 已经自动配置好：
```
HTTP_PROXY=http://127.0.0.1:7890
HTTPS_PROXY=http://127.0.0.1:7890
```
打开浏览器就能走代理。

---

## 六、如果要 Win10 而不是 Win11
把上面命令最后一行改成：
```
dockur/windows:10
```

---

要不要我直接给你写一个 **docker-compose.yml**，以后启动/停止/删除只需要一条命令，还能把共享目录、内存、代理都固定好？