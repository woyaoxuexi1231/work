package com.example.rsa;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;

public class RsaBidirectionalTest {
    public static void main(String[] args) throws Exception {
        // 1. 生成 RSA 密钥对（2048位）
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // 原始数据
        String originalText = "Hello, RSA 双向测试！";
        byte[] data = originalText.getBytes("UTF-8");

        System.out.println("========== 公钥加密 → 私钥解密（标准加密）==========");
        // 公钥加密
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = encryptCipher.doFinal(data);
        System.out.println("密文(Base64): " + Base64.getEncoder().encodeToString(encrypted));

        // 私钥解密
        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = decryptCipher.doFinal(encrypted);
        System.out.println("解密后: " + new String(decrypted, "UTF-8"));
        System.out.println("验证是否一致: " + originalText.equals(new String(decrypted, "UTF-8")));

        System.out.println("\n========== 私钥“加密” → 公钥“解密”（模拟签名）==========");
        // 注意：这不是标准安全做法，仅用于演示 RSA 双向性
        // 私钥“加密”
        Cipher signLikeCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        signLikeCipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] signedData = signLikeCipher.doFinal(data);
        System.out.println("私钥加密结果(Base64): " + Base64.getEncoder().encodeToString(signedData));

        // 公钥“解密”
        Cipher verifyLikeCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        verifyLikeCipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] recoveredData = verifyLikeCipher.doFinal(signedData);
        System.out.println("公钥解密后: " + new String(recoveredData, "UTF-8"));
        System.out.println("验证是否一致: " + originalText.equals(new String(recoveredData, "UTF-8")));

        System.out.println("\n结论：RSA 运算确实可逆，私钥操作也能用公钥倒推回去。");
    }
}