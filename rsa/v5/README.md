# v5：在 v4 基础上加入 timestamp + nonce，防重放攻击

## 目标

v4 主要解决的是：

- 多版本密钥
- keyVersion 不可信
- token 防篡改 / 防降级

但 v4 还有一个现实问题没有处理：

> 如果攻击者把一整包合法请求原样录下来，然后一模一样地再发一遍，会怎样？

这就是**重放攻击**。

v5 的目标，就是在 v4 的基础上补上这一层防护：

- `timestamp`：限制请求必须落在允许时间窗口内
- `nonce`：每次请求一个随机串，只能使用一次
- `requestSignature`：把 `keyVersion + timestamp + nonce + encryptedData` 一起签进去

## 运行

```bash
cd d:\project\work\rsa\v5\backend
mvn spring-boot:run
```

打开：`http://localhost:8105/`

## v5 的请求结构

`POST /api/secure/echo` 的请求体包含：

- `keyVersion`：当前使用的密钥版本
- `token`：服务端签发的版本绑定 token
- `timestamp`：前端发请求时的毫秒时间戳
- `nonce`：每次请求随机生成的字符串
- `encryptedKey`：RSA 公钥加密后的 AES key
- `encryptedData`：AES 加密后的业务数据
- `requestSignature`：请求签名

## 签名原文是什么

v5 为了让前后端容易对齐，签名原文采用固定拼接格式：

```text
signRaw = keyVersion + "|" + timestamp + "|" + nonce + "|" + encryptedData
```

然后：

- 前端用 **AES key 作为 HMAC 密钥**
- 对 `signRaw` 做 `HMAC-SHA256`
- 得到 `requestSignature`

后端收到请求后：

1. 先用 RSA 私钥解出 AES key
2. 再用同样的规则重算 `HMAC-SHA256`
3. 与 `requestSignature` 比较

如果中途有人改了：

- `timestamp`
- `nonce`
- `encryptedData`
- `keyVersion`

任意一个字段，HMAC 都会对不上，请求直接失败。

## 后端校验顺序

v5 后端处理顺序是：

1. 校验 `keyVersion`
2. 校验 `token`
3. RSA 解 `encryptedKey` 得到 AES key
4. 校验 `requestSignature`
5. 校验 `timestamp` 是否在允许窗口内（默认 5 分钟）
6. 校验 `nonce` 是否已经用过
7. 通过后才真正解密业务数据并执行业务

这个顺序的好处是：

- 签名先过，说明这几个关键字段没有被篡改
- 然后再做时间窗和 nonce 判重
- 逻辑清晰，也符合你说的那种面试表达方式

## nonce 为什么能防重放

因为攻击者重放时，通常会把整包请求原样重发：

- `timestamp` 不变
- `nonce` 不变
- `encryptedData` 不变
- `requestSignature` 也不变

第一次请求：

- nonce 没见过
- 后端放行，并把 nonce 记下来

第二次再发同一包：

- nonce 已经存在
- 后端直接拒绝

这就是 v5 的核心。

## 这个项目里 nonce 存在哪里

为了让项目开箱即跑，v5 里是用**内存 ConcurrentHashMap** 记录 nonce 的。

这适合教学，因为：

- 不用额外引 Redis
- 你可以先把流程理解清楚

但生产里更常见的做法是：

- 把 nonce 存 Redis
- 设置 10 分钟左右过期
- 多实例共享去重状态

## 页面怎么演示

页面有两个按钮：

- `发送新请求`
- `重放上一包（演示防重放）`

你先点一次“发送新请求”，会成功。  
然后再点“重放上一包”，服务端会因为 `nonce already used` 直接拒绝。

这个效果非常直观，适合学习和面试讲解。

## 关键代码入口

- 控制器： [RsaController.java](file:///d:/project/work/rsa/v5/backend/src/main/java/com/example/rsa/controller/RsaController.java)
- HMAC 签名与解密： [CryptoService.java](file:///d:/project/work/rsa/v5/backend/src/main/java/com/example/rsa/service/CryptoService.java)
- nonce 时间窗与唯一性校验： [NonceService.java](file:///d:/project/work/rsa/v5/backend/src/main/java/com/example/rsa/service/NonceService.java)
- token 逻辑： [TokenService.java](file:///d:/project/work/rsa/v5/backend/src/main/java/com/example/rsa/service/TokenService.java)
- 前端演示页： [index.html](file:///d:/project/work/rsa/v5/backend/src/main/resources/static/index.html)
