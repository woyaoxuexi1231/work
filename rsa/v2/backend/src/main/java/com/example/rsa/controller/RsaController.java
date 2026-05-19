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
        log.info("[API] (v2) 获取公钥");
        return ResponseEntity.ok(new KeyResponse(
                keyManager.getPublicKeyPem(keyManager.getPublicKey()),
                "v2：RSA-PKCS1 + AES-ECB + 响应签名（前端验签）"
        ));
    }

    @PostMapping("/secure/echo")
    public ResponseEntity<?> secureEcho(@RequestBody DecryptRequest request) {
        log.info("[API] (v2) /secure/echo");
        try {
            CryptoService.DecryptResult decrypted = cryptoService.decryptRequest(request, keyManager.getPrivateKey());
            String responsePlaintext = "服务端收到你的明文: " + decrypted.plaintext();
            EncryptResponse response = cryptoService.encryptResponse(responsePlaintext, decrypted.aesKey(), keyManager.getPrivateKey());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] (v2) 处理失败", e);
            return ResponseEntity.badRequest().body("Request failed: " + e.getMessage());
        }
    }
}
