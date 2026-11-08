package dev.momentum.desktop.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM encryption with a key derived from machine-specific information.
 * <p>
 * Used as a fallback when Windows DPAPI is unavailable. The key is derived
 * from the Windows machine GUID (from the registry) combined with the
 * current user name, making the encrypted data device-bound.
 * <p>
 * Format: {@code v2:base64(salt || iv || ciphertext)}
 */
public class AesGcmEncryptionService implements EncryptionService {

    private static final String FORMAT_PREFIX = "v2:";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        try {
            SecretKeySpec key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer out = ByteBuffer.allocate(SALT_BYTES + IV_BYTES + ciphertext.length);
            out.put(salt);
            out.put(iv);
            out.put(ciphertext);
            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(out.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(FORMAT_PREFIX)) {
            throw new IllegalStateException("Unknown or missing ciphertext format prefix");
        }
        byte[] data = Base64.getDecoder().decode(ciphertext.substring(FORMAT_PREFIX.length()));
        if (data.length < SALT_BYTES + IV_BYTES + 1) {
            throw new IllegalStateException("Ciphertext too short");
        }

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        byte[] encrypted = new byte[data.length - SALT_BYTES - IV_BYTES];
        System.arraycopy(data, 0, salt, 0, SALT_BYTES);
        System.arraycopy(data, SALT_BYTES, iv, 0, IV_BYTES);
        System.arraycopy(data, SALT_BYTES + IV_BYTES, encrypted, 0, encrypted.length);

        try {
            SecretKeySpec key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String test = "AES-GCM-availability-test";
            String encrypted = encrypt(test);
            String decrypted = decrypt(encrypted);
            return test.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Derives a 256-bit encryption key from machine-specific information.
     * <p>
     * Reads the Windows machine GUID from the registry via
     * {@code reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid}
     * and combines it with the current user name, making the encrypted data
     * device-bound.
     */
    private SecretKeySpec deriveKey(byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(getMachineMaterial().toCharArray(), salt, ITERATIONS, KEY_BITS);
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(key, "AES");
    }

    /**
     * Collects machine-specific identifying information for key derivation.
     * <p>
     * Reads the Windows machine GUID from the registry via
     * {@code reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid}
     * and combines it with the current user name. Falls back to Java system
     * properties if the registry query fails.
     */
    private String getMachineMaterial() {
        StringBuilder sb = new StringBuilder();
        sb.append("os.name=").append(System.getProperty("os.name"));
        sb.append("|os.arch=").append(System.getProperty("os.arch"));
        sb.append("|os.version=").append(System.getProperty("os.version"));
        sb.append("|user.name=").append(System.getProperty("user.name"));

        String machineGuid = readMachineGuidFromRegistry();
        if (machineGuid != null && !machineGuid.isEmpty()) {
            sb.append("|machineGuid=").append(machineGuid);
        }
        String userDomain = System.getenv("USERDOMAIN");
        if (userDomain != null && !userDomain.isEmpty()) {
            sb.append("|user.domain=").append(userDomain);
        }
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName != null && !computerName.isEmpty()) {
            sb.append("|computer.name=").append(computerName);
        }
        return sb.toString();
    }

    /**
     * Reads the Windows machine GUID from the registry via
     * {@code reg query "HKLM\SOFTWARE\Microsoft\Cryptography" /v MachineGuid}.
     * Falls back to {@code null} if the query fails or the value is absent.
     */
    private String readMachineGuidFromRegistry() {
        try {
            Process process = new ProcessBuilder(
                "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"
            ).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
            String text = output.toString();
            int idx = text.indexOf("MachineGuid");
            if (idx < 0) {
                return null;
            }
            int valueStart = text.indexOf("REG_SZ", idx);
            if (valueStart < 0) {
                return null;
            }
            valueStart = text.indexOf(" ", valueStart + "REG_SZ".length());
            if (valueStart < 0) {
                return null;
            }
            return text.substring(valueStart + 1).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
