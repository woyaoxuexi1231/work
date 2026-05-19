# v1：最小闭环（RSA-PKCS1 + AES-ECB，无签名）

## 目标

用最少的概念把“客户端加密上报 → 服务端解密处理 → 服务端加密回包”跑通：  
客户端每次随机生成 AES key，用 AES 加密业务数据；再用 RSA 公钥加密 AES key（Hybrid Encryption 的经典做法）。

> v1 为了好理解，AES 选了 ECB（不需要 IV），这在生产中不安全，仅用于学习跑通流程。

## 运行

```bash
cd d:\project\work\rsa\v1\backend
mvn spring-boot:run
```

打开：`http://localhost:8101/`

## API

- `GET /api/key`
  - 返回 `publicKey`（PEM）与 `algorithm`（说明字符串）
- `POST /api/secure/echo`
  - 请求体（JSON）：  
    - `encryptedKey`：Base64，RSA 公钥加密后的 AES key（RSA/ECB/PKCS1Padding）  
    - `encryptedData`：Base64，AES-ECB 加密后的业务数据（AES/ECB/PKCS5Padding）
  - 返回体（JSON）：  
    - `encryptedData`：Base64，服务端用同一个 AES key 加密的响应数据  
    - `signature`：空字符串（v1 不签名）

## 数据流（按请求顺序）

1. 前端请求公钥：`GET /api/key`
2. 前端随机生成 AES key（16/24/32 字节）
3. 前端用 AES-ECB 加密业务明文，得到 `encryptedData(Base64)`
4. 前端用 RSA 公钥（PKCS#1 v1.5 padding）加密 AES key，得到 `encryptedKey(Base64)`
5. 前端 POST 到 `/api/secure/echo`
6. 服务端：
   - RSA 私钥解 `encryptedKey` → AES key
   - AES-ECB 解 `encryptedData` → 明文
   - 业务处理后，再用 AES-ECB 加密响应明文 → 返回 `encryptedData`

## 关键代码入口

- 控制器与接口： [RsaController.java](file:///d:/project/work/rsa/v1/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 加解密实现： [CryptoService.java](file:///d:/project/work/rsa/v1/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- RSA 密钥生成： [KeyManager.java](file:///d:/project/work/rsa/v1/backend/src/main/java/com/example/rsa/service/KeyManager.java)
- 前端演示页： [index.html](file:///d:/project/work/rsa/v1/backend/src/main/resources/static/index.html)

