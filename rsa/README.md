# RSA + AES 复合加密：新手友好讲解（含完整数据流）

## 项目结构（两个方案，两个独立项目）

你说得对：之前的“版本号防篡改”方案本身就是一个完整解决方案；你后来描述的“前端每次随机 AES，上报/返回都走 AES+RSA”也是另一种常见方案。

因此我在 `d:\project\work\rsa` 下做了**两个独立项目**，互不影响、可分别运行：

- 方案 A：版本号防篡改（Token + 版本锁定 + AAD）  
  - 目录：[scheme1-version-protection](file:///d:/project/work/rsa/scheme1-version-protection)  
  - 后端工程：[scheme1-version-protection/backend](file:///d:/project/work/rsa/scheme1-version-protection/backend)  
  - 端口：8084  
  - 启动：
    - `cd d:\project\work\rsa\scheme1-version-protection\backend`
    - `mvn spring-boot:run`
    - 浏览器打开 `http://localhost:8084/`

- 方案 B：你说的方案（每次请求随机 AES；RSA 加密 AES Key；响应用 AES 加密 + RSA 私钥签名）  
  - 目录：[scheme2-client-aes](file:///d:/project/work/rsa/scheme2-client-aes)  
  - 后端工程：[scheme2-client-aes/backend](file:///d:/project/work/rsa/scheme2-client-aes/backend)  
  - 端口：8085  
  - 启动：
    - `cd d:\project\work\rsa\scheme2-client-aes\backend`
    - `mvn spring-boot:run`
    - 浏览器打开 `http://localhost:8085/`

下面正文先讲 **方案 B**（也就是你描述的那套流程）。

这个项目演示的是工作里非常常见的“敏感数据加密上报 + 加密返回”方案：

- 前端**每次请求随机生成一个 AES Key**
- 前端用 AES Key **加密业务数据**
- 前端用服务端 RSA **公钥加密 AES Key**
- 服务端用 RSA **私钥解密 AES Key**，再用 AES Key 解密业务数据
- 服务端返回响应时：继续用**同一把 AES Key** 加密响应；并用 RSA 私钥对响应做**签名**（前端用公钥验签）

重要提醒（对应你说的“服务端用私钥加密整个东西”）：
- “私钥加密给前端解密”这个说法在加密语义上不成立（前端没有私钥）。
- 你真实想要的是“**签名**”：服务端用私钥签名，前端用公钥验签，保证“响应没被改，确实来自服务端”。

---

## 第一大部分：用到的所有技术（新手能看懂的解释）

### 1) AES（对称加密）
- 可以把 AES 理解成“一把门钥匙”：加密和解密用的是同一把钥匙。
- AES 的特点：**快**，适合加密业务数据（例如手机号、身份证号、订单信息等）。

### 2) AES-GCM（带防篡改能力的 AES）
- 只加密还不够：别人可能把密文偷偷改掉。
- AES-GCM 会多生成一个“防篡改校验值”（Tag）。
- 如果密文被改过，解密会失败（你在前端/后端会看到“解密失败/校验失败”的日志）。

### 3) RSA（非对称加密）
- RSA 有两把钥匙：**公钥**和**私钥**。
- 公钥像一把“锁”：谁都能用它把东西锁起来（加密）。
- 私钥像“唯一钥匙”：只有服务端能用它打开锁（解密）。
- RSA 的特点：**慢**，并且能加密的数据长度有限，所以通常不拿它直接加密业务数据。

### 4) 为什么要 RSA + AES 一起用（复合加密）
一句话：**AES 负责加密业务数据，RSA 负责把 AES Key 安全送到服务端**。

你可以把它想象成：
- 业务数据 = 一车货（很大）
- AES = 货车上的大锁（适合锁大量东西）
- RSA = 给“钥匙”再套一个小锁（把 AES Key 安全送到服务端）

### 5) “服务端用私钥加密”的正确理解：签名（不是为了让前端解密）
- 本项目里服务端用私钥做了一件事：对响应做“签名”。
- 签名的意义：前端收到响应后，用公钥就能验证“响应是否被篡改、是否确实来自服务端”。

---

## 第二大部分：整个流程怎么走？一条请求从开始到结束的数据流

下面按真实请求顺序走一遍，你可以对照浏览器日志 + 后端日志一起看。

### Step 0：前端先拿公钥
接口：`GET /api/key`

返回（示例）：
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----...",
  "algorithm": "RSA-OAEP(SHA-256)+AES-GCM"
}
```

### Step 1：前端生成 AES Key，并加密业务数据
前端做：
- 随机生成 `aesKey`（16字节）
- 随机生成 `iv`（12字节，GCM 推荐）
- 用 `AES-GCM(aesKey, iv)` 加密业务明文，得到：
  - `ciphertext`（密文）
  - `tag`（GCM 校验标签，16字节）

为了让 Java 端直接解密，我们把 `ciphertext + tag` 拼到一起再 Base64。

### Step 2：前端用 RSA 公钥加密 AES Key
前端做：
- `encryptedKey = RSA-OAEP(publicKey, aesKey)`

注意：RSA 在这里**只加密 AES Key**，不加密业务数据。

### Step 3：前端把加密包发给服务端
接口：`POST /api/secure/echo`

请求体（示例）：
```json
{
  "encryptedKey": "Base64(...)",
  "iv": "Base64(12 bytes)",
  "encryptedData": "Base64(ciphertext + tag)"
}
```

### Step 4：服务端解密请求
服务端做：
1. 用 RSA 私钥解密出 `aesKey`
2. 用 `AES-GCM(aesKey, iv)` 解密 `encryptedData` 得到明文

对应代码：
- [RsaController.java](file:///d:/project/work/rsa/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- [CryptoService.java](file:///d:/project/work/rsa/backend/src/main/java/com/example/rsa/service/CryptoService.java)

### Step 5：服务端加密响应（并签名）
服务端做：
1. 用**同一把 aesKey**（来自请求）加密响应明文，得到 `encryptedDataResp`（同样是 ciphertext+tag）
2. 用 RSA 私钥对 `(ivResp + encryptedDataResp)` 做签名，得到 `signature`

响应体（示例）：
```json
{
  "iv": "Base64(12 bytes)",
  "encryptedData": "Base64(ciphertext + tag)",
  "signature": "Base64(signature)"
}
```

### Step 6：前端验签 + 解密响应
前端做：
1. 用公钥验签：
   - 验证 `(iv + encryptedData)` 是否确实被服务端私钥签过
2. 验签通过后，用同一把 `aesKey` 解密响应密文

---

## 建议你从哪里开始看代码（学习路线）
- 前端（加密/验签/解密）：[index.html](file:///d:/project/work/rsa/backend/src/main/resources/static/index.html)
- 后端接口（接收/返回）：[RsaController.java](file:///d:/project/work/rsa/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 后端加解密（核心）：[CryptoService.java](file:///d:/project/work/rsa/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 后端密钥生成与提供公钥：[KeyManager.java](file:///d:/project/work/rsa/backend/src/main/java/com/example/rsa/service/KeyManager.java)
