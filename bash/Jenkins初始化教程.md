# Jenkins 初始化教程

## 〇、前置条件

运行脚本前，确保 Docker Desktop 已开启 TCP API：

1. Docker Desktop → **Settings** → **General**
2. 勾选 **Expose daemon on tcp://localhost:2375 without TLS**
3. 点 **Apply & Restart**

然后启动 Jenkins：

```bash
bash component_install_jenkins_data_docker.sh
```

脚本会自动安装 Docker CLI、JDK8，并验证 Jenkins → Docker Desktop 通信正常。

---

## 一、解锁 Jenkins

访问 `http://localhost:8080`，输入初始密码。

---

## 一、解锁 Jenkins

首次打开页面会提示"Unlock Jenkins"，需要初始密码。

### 获取密码

**挂载版**（直接读文件）：

```
C:\Users\15434\Desktop\docker-data\jenkins-data\secrets\initialAdminPassword
```

**非挂载版**（进容器读）：
```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

把得到的密码粘贴到输入框，点 **Continue**。

> 1beb90594d304022aaf8d2c651f865b8

---

## 二、安装插件

选择 **Install suggested plugins**（安装推荐插件），等待安装完成。

> 已配置清华大学镜像源，如果个别插件安装失败，点 **Retry** 重试。

---

## 三、创建管理员账户

插件装完后，填写管理员信息：

| 字段 | 填写 |
|------|------|
| 用户名 | `admin` |
| 密码 | 自定义（记好了，别忘） |
| 全名 | `Admin` |
| 邮箱 | 随便填 |

点 **Save and Continue**。

---

## 四、配置 Jenkins URL

确认 Jenkins 地址为 `http://localhost:8080`，点 **Save and Finish**。

---

## 五、初始化完成

看到"Jenkins is ready!"就完成了，点 **Start using Jenkins** 进入主界面。

---

## 六、常用配置（必须）

### 6.1 安装 Maven Integration 插件

1. 左侧菜单 → **Manage Jenkins** → **Plugins** → **Available plugins**
2. 搜索框输入 `Maven Integration`，勾选
3. 点 **Install without restart**
4. 等待安装完成

### 6.2 配置 JDK8 工具

> 脚本已自动安装 JDK8 到 `/usr/lib/jvm/java-8-openjdk-amd64`，这里只需登记。

1. **Manage Jenkins** → **Tools**
2. 找到 **JDK installations**，点 **Add JDK**
3. Name 填 `JDK8`
4. 取消勾选 "Install automatically"
5. JAVA_HOME 填 `/usr/lib/jvm/java-8-openjdk-amd64`
6. 点 **Save**

### 6.3 配置 Maven

1. 同页面找到 **Maven installations**，点 **Add Maven**
2. Name 填 `Maven3`
3. 勾选 **Install automatically**，版本选最新
4. 点 **Save**

---

## 七、验证

1. 左侧菜单 → **New Item**
2. 输入名称 `test`，选择 **Freestyle project**，点 OK
3. 点 **Build Steps** → **Add build step** → **Execute shell**
4. Command 输入 `echo "Jenkins OK"`
5. 点 **Save**，然后点 **Build Now**
6. 左侧 **Build History** 出现 `#1`，点进去 → **Console Output**，看到 `Jenkins OK` 即表示正常

---

## 八、重启/停止命令

```bash
docker restart jenkins    # 重启
docker stop jenkins        # 停止
docker start jenkins       # 启动
```

---

## 九、如果插件安装仍然失败

脚本启动后已自动修改 `hudson.model.UpdateCenter.xml` 指向清华镜像。如果初始化时仍然报错，手动验证：

```bash
docker exec jenkins cat /var/jenkins_home/hudson.model.UpdateCenter.xml
```

确保 URL 是 `https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json`，不是 `updates.jenkins.io`，然后重启：

```bash
docker restart jenkins
```
