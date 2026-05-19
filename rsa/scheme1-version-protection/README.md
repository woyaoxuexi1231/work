# 方案 A：版本号防篡改（先把“为什么要防”讲明白）

这套方案解决的是一个很现实的问题：**前端传的 version 可能被篡改**。

你先只记住一句话：
- **版本号这种“安全相关参数”，不能相信前端原样传来的。**

## 第一大部分：用到的技术（先用最简单的理解）

### 1) 为什么会有多个版本（v1 / v2）？
- 真实工作里，RSA 密钥会轮换：比如今天用 v2，旧的 v1 可能暂时还留着兼容旧客户端。
- 风险：攻击者把请求里的 `version=2` 改成 `version=1`，想让服务端走旧私钥（降级攻击）。

### 2) Token 是什么（新手版）
- Token 可以理解成：**服务端盖过章的“票据”**。
- 这个票据里写着：`version + 公钥fingerprint + 过期时间`，并由服务端签名。
- 前端请求时带回 token；服务端先验 token：
  - token 签名不对：伪造/篡改，拒绝
  - token 里写的 version ≠ 你请求里传的 version：说明 version 被改过，拒绝

### 3) 版本锁定是什么（新手版）
- 只靠 token 还不够“稳”，我们再做一层：把版本号写进 RSA 密文里（密文自己说“我属于 v2”）。
- 攻击者可以改外层 version，但改不了密文内部的 version（他没有私钥）。

## 第二大部分：一条请求怎么走（按本项目真实接口走）

### Step 0：前端获取最新密钥包（带 token）
接口：`GET /api/key/latest`

返回（示例）：
```json
{
  "version": "2",
  "publicKey": "-----BEGIN PUBLIC KEY-----...",
  "token": "..."
}
```

### Step 1：前端生成 AES Key，并构造“带版本锁定”的 RSA 加密块
前端做：
- 生成随机 `aesKey`
- 构造 `keyMaterial = version + timestamp + aesKey`
- RSA 加密 `keyMaterial` 得到 `encryptedKey`

### Step 2：前端用 AES 加密业务数据
前端做：
- `encryptedData = AES(aesKey, 明文)`

### Step 3：前端上报（攻击者可能在这里篡改 version）
接口：`POST /api/decrypt`

请求体（示例）：
```json
{
  "version": "2",
  "token": "...",
  "encryptedKey": "Base64(...)",
  "encryptedData": "Base64(...)"
}
```

### Step 4：服务端三道关卡（真正的“防降级”）
1) 查版本：按 request.version 找到对应的私钥（v1/v2…），并检查是否吊销  
2) 验 token：确认 token 真的是服务端签发的，而且 token 里绑定的 version 没被改  
3) 解密：用私钥解密 AES Key，再用 AES 解密业务数据  
## 代码从哪里看（按学习顺序）
- 前端：加密 + 攻击模拟：[index.html](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/resources/static/index.html)
- 后端入口（查版本/验 token/解密）：[RsaController.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- Token 逻辑（最关键的防篡改点）：[TokenService.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/TokenService.java)
- 解密核心（版本锁定校验）：[CryptoService.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 多版本密钥管理：[KeyManager.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/KeyManager.java)

## 如何运行
```bash
cd d:\project\work\rsa\scheme1-version-protection\backend
mvn spring-boot:run
```
浏览器打开：`http://localhost:8084/`

## 后续升级（先别看）

等你把本项目基础版流程吃透之后，我们再单独开一版“升级方案”，再把更强的设计点逐步加回来。
