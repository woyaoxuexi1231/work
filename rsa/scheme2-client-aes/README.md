# 方案 B：前端每次随机 AES（上报/返回都走 AES），RSA 只负责“包 AES Key”

这个项目实现了你描述的工作方案：**前端每次请求随机生成 AES Key**，用 AES 加密业务数据；再用服务端 RSA 公钥加密 AES Key 发给服务端。服务端解密出 AES Key 后解密业务数据，并用同一把 AES Key 加密响应数据返回；同时对响应做 RSA 私钥签名，前端用公钥验签，保证响应未被篡改。

## 第一大部分：用到的技术（新手版）

### 1) AES（对称加密）
- 同一把 key 既能加密也能解密。
- 优点：快，适合加密业务数据（长文本/JSON）。

### 2) AES-GCM（AES + 防篡改）
- 除了密文，还会产生一个 Tag（认证标签）。
- 密文或 Tag 任何一位被改，解密都会失败（这就是“防篡改”）。

### 3) RSA（非对称加密）
- 公钥加密、私钥解密。
- 优点：适合“安全传递 AES Key”；缺点：慢、长度有限，不适合直接加密业务数据。

### 4) RSA-OAEP（更安全的 RSA 填充）
- OAEP 是 RSA 的一种更安全的填充方式。
- 跨语言时必须确保两端 OAEP 参数一致（尤其是 MGF1 的 hash）。本项目后端显式指定 `SHA-256 + MGF1(SHA-256)`，避免解密报 `BadPaddingException`。

### 5) RSA 私钥在响应里做什么：签名（不是为了让前端“用公钥解密”）
- “私钥加密让前端解密”这个说法在加密语义上不成立（前端没有私钥）。
- 正确做法是：服务端对响应做“签名”，前端用公钥验签：证明响应来自服务端且未被篡改。

## 第二大部分：一条请求怎么走（数据流）

### Step 0：前端获取服务端公钥
接口：`GET /api/key`

返回（示例）：
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----...",
  "algorithm": "RSA-OAEP(SHA-256)+AES-GCM"
}
```

### Step 1：前端生成 AES Key + IV，并用 AES-GCM 加密业务数据
前端做：
- 生成 `aesKey`（16 bytes）
- 生成 `iv`（12 bytes，GCM 推荐）
- AES-GCM 加密明文得到：`ciphertext` 和 `tag(16 bytes)`
- 发送时把 `ciphertext + tag` 拼在一起再 Base64（Java 侧解密最省事）

### Step 2：前端用 RSA 公钥加密 AES Key
前端做：
- `encryptedKey = RSA-OAEP(publicKey, aesKey)`

### Step 3：前端上报
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
1) 用 RSA 私钥解密 `encryptedKey` 得到 `aesKey`
2) 用 `AES-GCM(aesKey, iv)` 解密 `encryptedData` 得到业务明文

### Step 5：服务端加密响应 + 签名
服务端做：
1) 用 **同一把 aesKey** 加密响应明文，得到 `encryptedDataResp`
2) 对 `(ivResp + encryptedDataResp)` 做 RSA 私钥签名，得到 `signature`

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
1) 公钥验签：校验 `(iv + encryptedData)` 是否被服务端私钥签过
2) 验签通过后，用同一把 `aesKey` 解密响应密文

## 代码入口（按学习顺序）
- 前端：加密/验签/解密：[index.html](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/resources/static/index.html)
- 后端接口入口：[RsaController.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 后端加解密核心：[CryptoService.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- RSA 密钥生成/下发公钥：[KeyManager.java](file:///d:/project/work/rsa/scheme2-client-aes/backend/src/main/java/com/example/rsa/service/KeyManager.java)

## 如何运行
```bash
cd d:\project\work\rsa\scheme2-client-aes\backend
mvn spring-boot:run
```
浏览器打开：`http://localhost:8085/`

