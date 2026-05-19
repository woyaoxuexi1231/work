# v2：在 v1 基础上加“响应签名 + 前端验签”

## 目标

在 v1 的“加密上报/解密处理/加密回包”闭环之上，加上最常见的完整性校验：  
服务端对响应密文 `encryptedData` 做 RSA 签名；前端先验签通过，再解密。

> v2 仍然使用 AES-ECB（不需要 IV），目的是先把“签名验签”这个环节讲清楚。

## 运行

```bash
cd d:\project\work\rsa\v2\backend
mvn spring-boot:run
```

打开：`http://localhost:8102/`

## API

- `GET /api/key`
  - 返回 `publicKey`（PEM）与 `algorithm`（说明字符串）
- `POST /api/secure/echo`
  - 请求体（JSON）：  
    - `encryptedKey`：Base64，RSA 公钥加密后的 AES key（RSA/ECB/PKCS1Padding）  
    - `encryptedData`：Base64，AES-ECB 加密后的业务数据（AES/ECB/PKCS5Padding）
  - 返回体（JSON）：  
    - `encryptedData`：Base64，服务端 AES-ECB 加密后的响应数据  
    - `signature`：Base64，服务端用 RSA 私钥对“密文字节”签名（SHA256withRSA）

## 签名/验签口径（非常重要）

v2 的签名内容是“响应 AES 密文的原始字节”，不是 Base64 字符串：  
- 服务端：对 `encrypted` 字节数组签名（`signature.update(encrypted)`）  
- 前端：Base64 解出密文字节后，用公钥验签

## 数据流（按请求顺序）

1. 前端请求公钥：`GET /api/key`
2. 前端生成 AES key；AES-ECB 加密业务明文；RSA 加密 AES key
3. 前端 POST 到 `/api/secure/echo`
4. 服务端解密拿到明文后：
   - AES-ECB 加密响应明文得到密文 `encrypted`
   - RSA 私钥对 `encrypted` 做 `SHA256withRSA` 签名，得到 `signature`
5. 前端收到响应后：
   - 先对 `encrypted` 做验签（失败则不解密）
   - 验签通过才用 AES key 解密

## 关键代码入口

- 控制器与接口： [RsaController.java](file:///d:/project/work/rsa/v2/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 加解密与签名： [CryptoService.java](file:///d:/project/work/rsa/v2/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 前端验签逻辑： [index.html](file:///d:/project/work/rsa/v2/backend/src/main/resources/static/index.html)

