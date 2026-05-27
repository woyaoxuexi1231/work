package com.example.rsa.controller;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import com.example.rsa.dto.KeyResponse;
import com.example.rsa.service.CryptoService;
import com.example.rsa.service.KeyManager;
import com.example.rsa.service.NonceService;
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
    private final NonceService nonceService;

    @GetMapping("/key")
    public ResponseEntity<KeyResponse> key() throws Exception {
        KeyVersion kv = keyManager.getLatestKey();
        long expireAtMs = System.currentTimeMillis() + 10 * 60 * 1000L;
        String token = tokenService.issueToken(kv.getVersion(), kv.getFingerprint(), expireAtMs);
        log.info("[API] (v5) 获取公钥 keyVersion={} status={} expireAtMs={}", kv.getVersion(), kv.getStatus(), expireAtMs);
        return ResponseEntity.ok(new KeyResponse(
                kv.getVersion(),
                keyManager.getPublicKeyPem(kv.getPublicKey()),
                "v5：keyVersion + token + timestamp + nonce + HMAC 防重放",
                token,
                nonceService.getReplayWindowMs()
        ));
    }

    @PostMapping("/secure/echo")
    public ResponseEntity<?> secureEcho(@RequestBody DecryptRequest request) {
        try {
            if (request.getKeyVersion() == null || request.getKeyVersion().isBlank()) {
                return ResponseEntity.badRequest().body("Request failed: missing keyVersion");
            }
            KeyVersion kv = keyManager.getKey(request.getKeyVersion());
            if (kv == null) {
                return ResponseEntity.badRequest().body("Request failed: invalid keyVersion");
            }

            long nowMs = System.currentTimeMillis();
            boolean ok = tokenService.verifyToken(request.getToken(), kv.getVersion(), kv.getFingerprint(), nowMs);
            if (!ok) {
                return ResponseEntity.badRequest().body("Request failed: invalid token");
            }

            byte[] aesKey = cryptoService.decryptAesKey(request, kv.getPrivateKey());
            boolean signatureOk = cryptoService.verifyRequestSignature(request, aesKey);
            if (!signatureOk) {
                return ResponseEntity.badRequest().body("Request failed: invalid request signature");
            }

            if (!nonceService.isTimestampWithinWindow(request.getTimestamp(), nowMs)) {
                return ResponseEntity.badRequest().body("Request failed: timestamp out of allowed window");
            }

            if (!nonceService.consumeNonce(request.getNonce(), nowMs)) {
                return ResponseEntity.badRequest().body("Request failed: nonce already used");
            }

            log.info("[API] (v5) /secure/echo keyVersion={} status={} timestamp={} nonce={}",
                    kv.getVersion(), kv.getStatus(), request.getTimestamp(), request.getNonce());
            CryptoService.DecryptResult decrypted = cryptoService.decryptPayload(request, aesKey);
            String responsePlaintext = "服务端收到你的明文: " + decrypted.getPlaintext();
            EncryptResponse response = cryptoService.encryptResponse(responsePlaintext, decrypted.getAesKey());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] (v5) 处理失败", e);
            return ResponseEntity.badRequest().body("Request failed: " + e.getMessage());
        }
    }
}
