# 方案 A：版本号防篡改（Token + 版本锁定 + AAD）

这个项目实现的是“RSA 密钥轮换 + 防降级攻击”的方案：服务端维护多套 RSA 私钥（v1/v2...），客户端拿到最新公钥后会带上一个 **token**，服务端用 token 校验版本号是否被篡改，从而防止攻击者强制降级到旧版本。

## 第一大部分：用到的技术（新手版）

### 1) RSA 多版本密钥（v1/v2…）
- 服务端会定期更换 RSA 密钥对（这叫“轮换”）。
- 为了兼容旧客户端，服务端可能暂时保留多把私钥（v1、v2）。
- 风险：攻击者把请求里的 `version` 改成旧版本，试图让服务端使用旧私钥（降级攻击）。

### 2) Token（服务端签发的防伪票据）
- `version` 这个字段是前端传的，可能被篡改，所以不能直接信。
- 服务端在下发公钥时，同时下发 `token`，并把 **version + 公钥指纹 + 过期时间** 打包后做数字签名。
- 前端发请求时必须带回这个 token；服务端会验证 token 的签名：
  - 签名不对：token 被篡改或伪造，直接拒绝。
  - token 里绑定的 version ≠ 请求里 version：说明 version 被篡改，直接拒绝。

### 3) “版本锁定”（把 version 写进 RSA 密文里）
- 只校验 token 还不够，我们再做一道“密文自描述”：
  - RSA 加密块里不是只有 AES Key，还塞入 version 和 timestamp。
- 服务端解密后能读出“密文内部版本”，攻击者没法改（他没有私钥）。

### 4) AES-GCM 的 AAD（让篡改 version 直接导致解密失败）
- AES-GCM 支持 AAD（附加认证数据）。
- 我们把 **真实版本号** 作为 AAD 参与 GCM 校验：只要 version 被篡改，GCM 校验必失败。

## 第二大部分：一条请求怎么走（数据流）

### Step 0：前端获取最新密钥包
接口：`GET /api/key/latest`

返回（示例）：
```json
{
  "version": "2",
  "publicKey": "-----BEGIN PUBLIC KEY-----...",
  "token": "Base64(payload).Base64(signature)"
}
```

### Step 1：前端随机生成 AES Key，并构造 RSA 加密块（keyMaterial）
keyMaterial 结构（固定 28 bytes）：
- `version`（4 bytes）
- `timestamp`（8 bytes）
- `aesKey`（16 bytes）

前端用最新公钥 RSA-OAEP 加密 `keyMaterial`，得到 `encryptedKey`。

### Step 2：前端用 AES-GCM 加密业务数据（version 作为 AAD）
前端用 `aesKey` + `iv` 加密业务数据得到 `encryptedData`（ciphertext + tag）。
同时设置 `additionalData = version`（这就是 AAD）。

### Step 3：前端上报（可能被攻击者篡改 version）
接口：`POST /api/decrypt`

请求体（示例）：
```json
{
  "version": "2",
  "token": "...",
  "encryptedKey": "Base64(...)",
  "iv": "Base64(12 bytes)",
  "encryptedData": "Base64(ciphertext + tag)"
}
```

### Step 4：服务端三道关卡（防降级）
1) **查版本**：根据 `request.version` 找到对应的密钥版本（v1/v2…），并检查是否被吊销。\n+2) **验 token**：校验 token 是否由服务端签发；token 内绑定的 version 是否与 request.version 一致。\n+3) **解密 + 锁定校验**：\n+   - RSA 解密 keyMaterial，读出内部 version\n+   - 内部 version 必须等于“当前使用的私钥版本”\n+   - AES-GCM 解密时把 version 作为 AAD，确保篡改立刻失败\n+
## 代码入口（按学习顺序）
- 前端：加密 + 攻击模拟：[index.html](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/resources/static/index.html)
- 后端接口入口：[RsaController.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- Token 逻辑（核心防篡改）：[TokenService.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/TokenService.java)
- 解密核心（版本锁定 + AAD）：[CryptoService.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- 多版本密钥管理：[KeyManager.java](file:///d:/project/work/rsa/scheme1-version-protection/backend/src/main/java/com/example/rsa/service/KeyManager.java)

## 如何运行
```bash
cd d:\project\work\rsa\scheme1-version-protection\backend
mvn spring-boot:run
```
浏览器打开：`http://localhost:8084/`

