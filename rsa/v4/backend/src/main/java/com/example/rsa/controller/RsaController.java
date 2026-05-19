package com.example.rsa.controller;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import com.example.rsa.dto.KeyResponse;
import com.example.rsa.service.CryptoService;
import com.example.rsa.service.KeyManager;
import com.example.rsa.service.TokenService;
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
    private final TokenService tokenService;

    @GetMapping("/key")
    public ResponseEntity<KeyResponse> key() throws Exception {
        var kv = keyManager.getLatestKey();
        long expireAtMs = System.currentTimeMillis() + 10 * 60 * 1000L;
        String token = tokenService.issueToken(kv.getVersion(), kv.getFingerprint(), expireAtMs);
        log.info("[API] (v4) 获取公钥 keyVersion={} status={} expireAtMs={}", kv.getVersion(), kv.getStatus(), expireAtMs);
        return ResponseEntity.ok(new KeyResponse(
                kv.getVersion(),
                keyManager.getPublicKeyPem(kv.getPublicKey()),
                "v4：keyVersion+token（v4 加解密流程待继续完善）",
                token
        ));
    }

    @PostMapping("/secure/echo")
    public ResponseEntity<?> secureEcho(@RequestBody DecryptRequest request) {
        try {
            if (request.getKeyVersion() == null || request.getKeyVersion().isBlank()) {
                return ResponseEntity.badRequest().body("Request failed: missing keyVersion");
            }
            var kv = keyManager.getKey(request.getKeyVersion());
            if (kv == null) {
                return ResponseEntity.badRequest().body("Request failed: invalid keyVersion");
            }

            long nowMs = System.currentTimeMillis();
            boolean ok = tokenService.verifyToken(request.getToken(), kv.getVersion(), kv.getFingerprint(), nowMs);
            if (!ok) {
                return ResponseEntity.badRequest().body("Request failed: invalid token");
            }

            log.info("[API] (v4) /secure/echo keyVersion={} status={}", kv.getVersion(), kv.getStatus());
            CryptoService.DecryptResult decrypted = cryptoService.decryptRequest(request, kv.getPrivateKey());
            String responsePlaintext = "服务端收到你的明文: " + decrypted.plaintext();
            EncryptResponse response = cryptoService.encryptResponse(responsePlaintext, decrypted.aesKey());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] (v4) 处理失败", e);
            return ResponseEntity.badRequest().body("Request failed: " + e.getMessage());
        }
    }
}
