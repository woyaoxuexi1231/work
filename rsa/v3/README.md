# v3：在 v2 基础上引入 IV（AES-CBC），并对 (iv + 密文) 签名

## 目标

v2 的 AES-ECB 为了好理解牺牲了安全性；v3 用 AES-CBC 把 “IV” 这个概念补齐：  
- 请求加密：客户端随机生成 iv（16 字节），用 AES-CBC 加密业务数据  
- 响应加密：服务端同样生成新的 iv，用 AES-CBC 加密响应  
- 签名：签名范围升级为 `(ivBytes + ciphertextBytes)`，避免 iv 被篡改

## 运行

```bash
cd d:\project\work\rsa\v3\backend
mvn spring-boot:run
```

打开：`http://localhost:8103/`

## API

- `GET /api/key`
  - 返回 `publicKey`（PEM）与 `algorithm`（说明字符串）
- `POST /api/secure/echo`
  - 请求体（JSON）：  
    - `encryptedKey`：Base64，RSA 公钥加密后的 AES key（RSA/ECB/PKCS1Padding）  
    - `iv`：Base64，AES-CBC 的 iv（16 字节）  
    - `encryptedData`：Base64，AES-CBC 加密后的业务数据（AES/CBC/PKCS5Padding）
  - 返回体（JSON）：  
    - `iv`：Base64，服务端响应的 iv（16 字节）  
    - `encryptedData`：Base64，服务端 AES-CBC 加密后的响应数据  
    - `signature`：Base64，服务端对 `(ivBytes + encryptedBytes)` 做 SHA256withRSA 签名

## iv 是什么（只讲必须的）

AES-CBC 每次加密都要一个随机 iv，让“同样的明文 + 同一个 key”不会每次加密出相同密文。  
iv 不是秘密，可以和密文一起传，但必须参与完整性校验（否则被篡改会导致解密结果不可控）。

## 签名/验签口径（非常重要）

v3 的签名内容是二进制拼接，不是字符串拼接：  
- 服务端：`toSign = concat(ivBytes, encryptedBytes)`，对 `toSign` 签名  
- 前端：Base64 解出 iv/ciphertext 的字节后，按同样方式拼接再验签

对应实现见： [CryptoService.java](file:///d:/project/work/rsa/v3/backend/src/main/java/com/example/rsa/service/CryptoService.java)

## 关键代码入口

- 控制器与接口： [RsaController.java](file:///d:/project/work/rsa/v3/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- 加解密与签名： [CryptoService.java](file:///d:/project/work/rsa/v3/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 前端 AES-CBC + 验签： [index.html](file:///d:/project/work/rsa/v3/backend/src/main/resources/static/index.html)

