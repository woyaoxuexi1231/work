# 方案 B：前端每次随机 AES；RSA 只负责“包 AES Key”；响应加密后再签名

这份 README 只讲“你能跑起来、你能看懂”的内容。

你先记住一句话就够了：
- **AES 加密业务数据**
- **RSA 加密 AES Key**

## 第一大部分：用到的技术（先用最简单的理解）

### 1) AES（对称加密）
- AES 就是一把“同一把钥匙开同一把锁”的锁。
- 你用它加密业务数据；服务端用同一把 key 解密业务数据。
- 本项目为简化理解，使用 AES-ECB（不需要 IV）。

### 2) RSA（非对称加密）
- RSA 有两把钥匙：公钥、私钥。
- **公钥加密**（任何人都能做），**私钥解密**（只有服务端能做）。
- 在这个方案里：RSA 不负责加密业务数据，只负责把 AES Key 安全地送到服务端。

### 3) 服务端“私钥做了啥”（新手版结论）
- **服务端不会用私钥“加密给前端解密”**（那样前端也解不开）。
- 服务端用私钥做的是：**签名**（防止响应被篡改，前端用公钥验签）。

## 第二大部分：一条请求从开始到结束（按本项目的真实接口走）

### Step 0：前端先拿公钥
接口：`GET /api/key`

返回（示例）：
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----...",
  "algorithm": "..."
}
```

### Step 1：前端随机生成 AES Key，然后用 AES 加密业务明文
前端做：
- 生成随机 `aesKey`
- 用 AES 把业务明文加密成 `encryptedData`

### Step 2：前端用 RSA 公钥加密 AES Key
前端做：
- `encryptedKey = RSA(publicKey, aesKey)`

### Step 3：前端把加密包发给服务端
接口：`POST /api/secure/echo`

请求体（示例）：
```json
{
  "encryptedKey": "Base64(...)",
  "encryptedData": "Base64(...)"
}
```

### Step 4：服务端解密请求并拿到明文
服务端做：
1) RSA 私钥解密 `encryptedKey` 得到 `aesKey`
2) 用 `aesKey` 解密 `encryptedData` 得到业务明文

### Step 5：服务端用同一把 AES Key 加密响应，并对响应做签名
服务端做：
1) 用同一把 `aesKey` 加密响应明文，得到 `encryptedDataResp`
2) 用 RSA 私钥对 `encryptedDataResp` 做签名，得到 `signature`

响应体（示例）：
```json
{
  "encryptedData": "Base64(...)",
  "signature": "Base64(...)"
}
```

### Step 6：前端先验签，再解密响应
前端做：
1) 用公钥验证 `signature`（验签通过，才能信这个响应没被改）
2) 用同一把 `aesKey` 解密响应密文，得到响应明文

## 代码从哪里看（按学习顺序）
- 前端全流程（生成 AES、RSA 包 key、验签、解密）：[index.html](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/resources/static/index.html)
- 后端接口入口（收请求/回响应）：[RsaController.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 后端加解密核心（RSA 解 AES Key、AES 解密/加密、签名）：[CryptoService.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 服务端 RSA 密钥生成与公钥下发：[KeyManager.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/service/KeyManager.java)

## 如何运行
```bash
cd d:\project\work\rsa\scheme2-client-aes\backend
mvn spring-boot:run
```
浏览器打开：`http://localhost:8085/`

## 后续升级（先别看）

等你把本项目的基础流程完全吃透之后，我们再单独开一版“升级方案”，再去讲更安全的组合（比如带认证的对称加密、更安全的 RSA 填充等）。
