package com.example.rsa.controller;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.KeyResponse;
import com.example.rsa.model.KeyVersion;
import com.example.rsa.service.CryptoService;
import com.example.rsa.service.KeyManager;
import com.example.rsa.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 版本防篡改方案的接口入口。
 *
 * 两个接口：
 * - GET  /api/key/latest  ：拿最新公钥 + token
 * - POST /api/decrypt     ：携带 version + token + 密文包，服务端验证后解密
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RsaController {
    private final KeyManager keyManager;
    private final TokenService tokenService;
    private final CryptoService cryptoService;

    @GetMapping("/key/latest")
    public ResponseEntity<KeyResponse> latestKey() throws Exception {
        log.info("[API] 获取最新公钥");

        KeyVersion kv = keyManager.getLatestKey();
        String token = tokenService.issueToken(kv.getVersion(), kv.getFingerprint());

        return ResponseEntity.ok(new KeyResponse(
                kv.getVersion(),
                keyManager.getPublicKeyPem(kv.getPublicKey()),
                token
        ));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<?> decrypt(@RequestBody DecryptRequest request) {
        log.info("[API] 解密请求：request.version=v{}", request.getVersion());
        try {
            KeyVersion kv = keyManager.getKey(request.getVersion());
            if (kv == null) {
                log.warn("[API] 不存在的版本: v{}", request.getVersion());
                return ResponseEntity.badRequest().body("Invalid key version");
            }

            if (kv.getStatus() == KeyVersion.Status.REVOKED) {
                log.warn("[API] 版本已吊销: v{}", request.getVersion());
                return ResponseEntity.status(403).body("Key revoked");
            }

            boolean tokenOk = tokenService.verifyToken(request.getToken(), request.getVersion(), kv.getFingerprint());
            if (!tokenOk) {
                log.error("[API] token 校验失败：疑似篡改 version");
                return ResponseEntity.status(401).body("Invalid token");
            }

            String plaintext = cryptoService.decrypt(request, kv);
            return ResponseEntity.ok(plaintext);
        } catch (SecurityException e) {
            log.error("[API] 安全校验失败: {}", e.getMessage());
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("[API] 解密失败", e);
            return ResponseEntity.badRequest().body("Decrypt failed: " + e.getMessage());
        }
    }
}

