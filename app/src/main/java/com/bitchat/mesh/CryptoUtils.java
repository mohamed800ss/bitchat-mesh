package com.bitchat.mesh;

import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    // A shared secret for the mesh network. In a real app, this should be exchanged securely (e.g., via QR code or Diffie-Hellman)
    private static final String SHARED_SECRET = "BitChatMesh_SuperSecretKey_2023!";
    private static final String AES_MODE = "AES/CBC/PKCS5Padding";
    private static final String CHARSET = "UTF-8";

    private static SecretKeySpec generateKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = SHARED_SECRET.getBytes(CHARSET);
        key = sha.digest(key);
        key = Arrays.copyOf(key, 32); // Use only first 256 bit
        return new SecretKeySpec(key, "AES");
    }

    public static String encrypt(String plainText) {
        try {
            SecretKeySpec secretKey = generateKey();
            Cipher cipher = Cipher.getInstance(AES_MODE);
            
            byte[] iv = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(CHARSET));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.decode(encryptedText, Base64.NO_WRAP);
            
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            int encryptedSize = combined.length - 16;
            byte[] encryptedBytes = new byte[encryptedSize];
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedSize);
            
            SecretKeySpec secretKey = generateKey();
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
