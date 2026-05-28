package com.example.rsa.service;

import com.example.rsa.dto.DecryptRequest;
import com.example.rsa.dto.EncryptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {
    /**
     * 解密客户端发来的请求。
     *
     * 流程：RSA 私钥解出 AES key → 用客户端提供的 IV + AES-CBC 解密业务数据。
     *
     * 注意：这里的 request.iv 是<strong>客户端自己生成并上传的</strong>，服务端直接信任它来解密。
     * 这一步假设"请求是完整的、未被篡改的"——v3 实际上没有对请求做签名校验，
     * 签名的保护范围只覆盖了服务端 → 客户端的响应方向。
     * 如果攻击者篡改了请求中的 iv，解密结果会变成乱码（CBC 模式的 iv 错误会导致
     * 第一个 16 字节块完全损坏），但服务端目前不会主动检测这种篡改。
     */
    public DecryptResult decryptRequest(DecryptRequest request, PrivateKey rsaPrivateKey) throws Exception {
        log.info("[解密服务] (v3) RSA 解密 AES key + AES-CBC 解密业务数据（需要 iv）");

        // Step 1: 用 RSA 私钥解开客户端用公钥加密的 AES key
        byte[] aesKey = rsaDecryptPkcs1(request.getEncryptedKey(), rsaPrivateKey);

        // Step 2: 用客户端提供的 IV + AES-CBC 解密业务数据
        //   - AES-CBC 解密时，第一个密文块解密后要和 IV 做 XOR 才能得到第一个明文块
        //   - 如果 IV 被篡改，第一个块会解出乱码；后续块不受影响（CBC 的错误传播只有 1 个块）
        byte[] plaintextBytes = aesDecryptCbc(request.getEncryptedData(), aesKey, request.getIv());
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        log.info("[解密服务] (v3) 解密成功，明文长度={} 字符", plaintext.length());
        log.debug("[解密服务] (v3) 明文内容（仅学习用，生产不要打）: {}", plaintext);
        return new DecryptResult(aesKey, plaintext);
    }

    /**
     * 加密服务端的响应，并对其签名。
     *
     * 关键升级（相比 v2）：
     *
     * 1. 每次加密生成一个新的随机 IV（16 字节），用 SecureRandom。
     *    <strong>为什么 IV 必须随机？</strong>
     *    AES-CBC 的第一个明文块在加密前会先和 IV 做 XOR。如果 IV 固定不变，
     *    那么相同的第一个明文块每次都会产生相同的密文块——这又退化成了 ECB 的问题。
     *    因此每次加密都换一个新 IV，即使明文完全相同，密文也完全不同。
     *
     * 2. 签名范围从 v2 的"只签密文"升级为"签 IV + 密文"。
     *    <strong>为什么 IV 也要参与签名？</strong>
     *    IV 本身不是秘密（它和密文一起明文传输），但它的完整性至关重要。
     *    如果攻击者篡改了 IV，前端用错误的 IV 解密时：
     *      - CBC 解密第一个块 = AES_decrypt(密文块1) XOR IV
     *      - IV 被改 → XOR 结果完全错误 → 第一个 16 字节明文块损坏
     *    把 IV 纳入签名范围后，只要 IV 被改了一个 bit，验签就会失败，
     *    前端在解密之前就能发现并拒绝。
     *
     * 3. 签名算法仍然是 SHA256withRSA，和 v2 一致。
     */
    public EncryptResponse encryptResponse(String responsePlaintext, byte[] aesKey, PrivateKey signingKey) throws Exception {
        log.info("[加密服务] (v3) AES-CBC 加密响应 + RSA 私钥签名 (iv + encryptedData)");

        // --- 生成随机 IV ---
        // 注意：必须用 SecureRandom，不要用 java.util.Random（那是伪随机，可预测）
        // IV 长度固定 16 字节，对应 AES 的块大小
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        log.debug("[加密服务] (v3) 生成随机 IV: {}", Base64.getEncoder().encodeToString(iv));

        // --- AES-CBC 加密 ---
        // CBC 模式需要 IvParameterSpec；没有它，Cipher.init 会直接抛异常
        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = aesCipher.doFinal(responsePlaintext.getBytes(StandardCharsets.UTF_8));
        log.debug("[加密服务] (v3) AES-CBC 加密完成，密文长度={} 字节", encrypted.length);

        // --- 签名：签 (IV + 密文) ---
        // 对 toSign = ivBytes || encryptedBytes 做 SHA256 哈希，再用 RSA 私钥签名
        // 前端验签时也必须按同样的拼接顺序：md.update(ivBytes + encryptedBytes)
        byte[] toSign = concat(iv, encrypted);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signingKey);
        signature.update(toSign);
        byte[] sig = signature.sign();
        log.debug("[加密服务] (v3) 签名完成，签名长度={} 字节", sig.length);

        return new EncryptResponse(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(sig)
        );
    }

    public static class DecryptResult {
        private final byte[] aesKey;
        private final String plaintext;

        public DecryptResult(byte[] aesKey, String plaintext) {
            this.aesKey = aesKey;
            this.plaintext = plaintext;
        }

        public byte[] getAesKey() { return aesKey; }
        public String getPlaintext() { return plaintext; }
    }

    private static byte[] rsaDecryptPkcs1(String encryptedKeyB64, PrivateKey privateKey) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKey = rsa.doFinal(Base64.getDecoder().decode(encryptedKeyB64));
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            throw new IllegalArgumentException("Invalid AES key length: " + aesKey.length);
        }
        return aesKey;
    }

    /**
     * AES-CBC 解密。
     *
     * <strong>IV 在 CBC 解密中的作用：</strong>
     * 解密时，第一个密文块先用 AES 解密得到中间值 D₁，然后 D₁ XOR IV = 第一个明文块 P₁。
     * 后续的每个密文块解密后，和<strong>上一个密文块</strong>做 XOR（不是 IV）。
     *
     * 这意味着：
     *   - IV 只影响第一个明文块（错误传播仅 1 个块）
     *   - 如果 IV 缺失，Cipher.init 直接抛异常——根本解不了
     *   - 如果 IV 错误（被篡改），第一个块解出乱码，后续块正常
     *
     * 这也是为什么 v3 要把 IV 纳入签名范围：攻击者可以通过篡改 IV 来破坏第一个明文块，
     * 虽然影响有限，但在某些场景下（第一个块恰好是关键字段），这仍然是可利用的。
     */
    private static byte[] aesDecryptCbc(String encryptedDataB64, byte[] aesKey, String ivB64) throws Exception {
        // v3 相比 v1/v2 多了 iv 字段——如果客户端没传，说明它可能是 v1/v2 的老前端
        if (!StringUtils.hasLength(ivB64)) {
            throw new IllegalArgumentException("v3 缺少 iv：请求必须携带 iv 字段（AES-CBC 模式必需），请确认前端版本 >= v3");
        }
        byte[] iv = Base64.getDecoder().decode(ivB64);
        if (iv.length != 16) {
            throw new IllegalArgumentException("v3 iv 长度错误：期望 16 字节，实际 " + iv.length + " 字节");
        }

        // CBC 解密：必须传入同一个 IV，解密才能得到正确的第一个明文块
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        return aes.doFinal(Base64.getDecoder().decode(encryptedDataB64));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
