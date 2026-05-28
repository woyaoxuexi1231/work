package com.example.rsa.controller;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import com.example.rsa.dto.KeyResponse;
import com.example.rsa.service.CryptoService;
import com.example.rsa.service.KeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RsaController {
    private final KeyManager keyManager;
    private final CryptoService cryptoService;

    @GetMapping("/key")
    public ResponseEntity<KeyResponse> key() throws Exception {
        log.info("[API] (v3) 获取公钥");
        return ResponseEntity.ok(new KeyResponse(
                keyManager.getPublicKeyPem(keyManager.getPublicKey()),
                "v3：RSA-PKCS1 + AES-CBC(iv) + 响应签名"
        ));
    }

    /**
     * 接收加密请求，解密后原样回显。
     *
     * 请求中必须携带 {@code iv} 字段（AES-CBC 必需），服务端用它来解密第一个密文块。
     * 响应中会生成<strong>新的随机 IV</strong>，并对 (IV + 密文) 做 RSA 签名——
     * 这是 v3 的核心升级：签名不再只保护密文，而是保护整个"解密所需数据"(IV + 密文)。
     */
    @PostMapping("/secure/echo")
    public ResponseEntity<?> secureEcho(@RequestBody DecryptRequest request) {
        log.info("[API] (v3) /secure/echo — 收到加密请求（应包含 iv 字段）");
        try {
            // 解密：用服务端 RSA 私钥解出 AES key，再用客户端提供的 IV 做 AES-CBC 解密
            CryptoService.DecryptResult decrypted = cryptoService.decryptRequest(request, keyManager.getPrivateKey());

            // 构造响应并加密：服务端生成自己的随机 IV，签名覆盖 (IV + 密文)
            String responsePlaintext = "服务端收到你的明文: " + decrypted.getPlaintext();
            EncryptResponse response = cryptoService.encryptResponse(responsePlaintext, decrypted.getAesKey(), keyManager.getPrivateKey());

            log.info("[API] (v3) 响应已加密并签名，响应 IV={}", response.getIv());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] (v3) 处理失败", e);
            return ResponseEntity.badRequest().body("Request failed: " + e.getMessage());
        }
    }
}
