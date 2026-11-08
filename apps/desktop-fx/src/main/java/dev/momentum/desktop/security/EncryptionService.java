package dev.momentum.desktop.security;

/**
 * Device-bound encryption for sensitive at-rest data such as AI API keys.
 * <p>
 * Encrypted values are bound to the local machine and cannot be decrypted on
 * another device, protecting keys if the database file is copied or stolen.
 * On Windows, encryption uses the Windows Data Protection API (DPAPI) with
 * {@code CRYPTPROTECT_LOCALMACHINE}; on other platforms, AES-GCM with a key
 * derived from machine-specific information is used as a fallback.
 */
public interface EncryptionService {

    /**
     * Encrypts a plaintext string and returns a Base64-encoded ciphertext.
     * The ciphertext includes a format prefix so the format can evolve safely.
     *
     * @param plaintext the sensitive plaintext to encrypt
     * @return a Base64-encoded, device-bound ciphertext string
     * @throws IllegalStateException if encryption is not available on this platform
     */
    String encrypt(String plaintext);

    /**
     * Decrypts a Base64-encoded ciphertext produced by {@link #encrypt(String)}.
     *
     * @param ciphertext the ciphertext string previously produced by {@link #encrypt}
     * @return the decrypted plaintext
     * @throws IllegalStateException if the ciphertext cannot be decrypted (wrong machine,
     *                               corrupted data, or unsupported format)
     */
    String decrypt(String ciphertext);

    /**
     * Returns true if encryption is available and functional on this platform.
     * Performs a quick encrypt/decrypt round-trip to verify.
     */
    boolean isAvailable();
}
