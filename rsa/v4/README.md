# v4：更接近生产的形态（多版本密钥 + token 防降级 + 更安全的加密套件）

## 目标

v1~v3 重点是“能看懂、能跑通”。v4 计划把真实业务里常见的安全点补齐：

- 多版本密钥（keyVersion）：支持轮换、废弃、吊销
- 防降级/防篡改：服务端下发 token，把 keyVersion 和指纹绑定，客户端上报时校验
- 更安全的密码套件：RSA-OAEP + AES-GCM（后续实现）

## 当前状态（重要）

v4 目前已经把“多版本密钥 + token 相关的类/DTO”准备好了，但控制器和加解密流程还没完全接上，因此还不是最终的 v4 闭环版本。

## 运行

```bash
cd d:\project\work\rsa\v4\backend
mvn spring-boot:run
```

打开：`http://localhost:8104/`

## 已准备好的数据结构

`POST /api/secure/echo` 的请求体 DTO 已经预留了 v4 需要的字段：  
- `keyVersion`：客户端声称使用的密钥版本  
- `token`：服务端下发的绑定 token（用于防篡改/防降级）  
- `encryptedKey`：RSA 加密后的 key material（v4 计划用 OAEP）  
- `iv`：v4 计划用于 AEAD（例如 GCM 的 nonce）  
- `encryptedData`：业务数据密文

对应文件： [DecryptRequest.java](file:///d:/project/work/rsa/v4/backend/src/main/java/com/example/rsa/dto/DecryptRequest.java)

## 关键代码入口（v4 骨架）

- 多版本密钥模型： [KeyVersion.java](file:///d:/project/work/rsa/v4/backend/src/main/java/com/example/rsa/model/KeyVersion.java)
- 多版本密钥管理： [KeyManager.java](file:///d:/project/work/rsa/v4/backend/src/main/java/com/example/rsa/service/KeyManager.java)
- token 逻辑： [TokenService.java](file:///d:/project/work/rsa/v4/backend/src/main/java/com/example/rsa/service/TokenService.java)
- 控制器（待对齐 v4 流程）： [RsaController.java](file:///d:/project/work/rsa/v4/backend/src/main/java/com/example/rsa/controller/RsaController.java)

