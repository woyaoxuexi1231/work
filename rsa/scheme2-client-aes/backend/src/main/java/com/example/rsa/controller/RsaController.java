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

    /**
     * 获取服务端 RSA 公钥
     *
     * 业务含义（新手版）：
     * - 客户端用这个公钥加密“本次请求随机生成的 AES Key”；
     * - 服务端用私钥解密出 AES Key，再用 AES Key 解密业务数据。
     */
    @GetMapping("/key")
    public ResponseEntity<KeyResponse> getLatestKey() throws Exception {
        log.info("[API] 收到获取 RSA 公钥请求");
        return ResponseEntity.ok(new KeyResponse(
                keyManager.getPublicKeyPem(),
                "RSA-PKCS1 + AES-ECB + RSA签名验签"
        ));
    }

    /**
     * 安全请求演示接口（请求与响应都走“RSA + AES”）
     *
     * 请求：
     * - encryptedKey: RSA(PKCS1Padding) 加密后的 AES key
     * - encryptedData: AES(ECB/PKCS5Padding) 加密后的业务数据
     *
     * 响应：
     * - 服务端使用同一把 AES key 加密响应
     * - 同时用 RSA 私钥对响应做签名，前端用公钥验签，防止响应被篡改
     */
    @PostMapping("/secure/echo")
    public ResponseEntity<?> secureEcho(@RequestBody DecryptRequest request) {
        log.info("[API] 收到安全请求 /secure/echo");
        try {
            CryptoService.DecryptResult decrypted = cryptoService.decryptRequest(request, keyManager.getPrivateKey());

            String responsePlaintext = "服务端收到你的明文: " + decrypted.plaintext();
            EncryptResponse encryptedResponse = cryptoService.encryptResponse(
                    responsePlaintext,
                    decrypted.aesKey(),
                    keyManager.getPrivateKey()
            );

            log.info("[API] /secure/echo 处理完成，返回加密响应");
            return ResponseEntity.ok(encryptedResponse);
        } catch (SecurityException e) {
            log.error("[API] 安全校验失败: {}", e.getMessage());
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("[API] /secure/echo 处理失败", e);
            return ResponseEntity.badRequest().body("Secure request failed: " + e.getMessage());
        }
    }
}
