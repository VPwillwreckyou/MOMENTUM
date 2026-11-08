package dev.momentum.desktop.security;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Windows Data Protection API (DPAPI) based encryption.
 * <p>
 * Encrypted blobs are bound to the local machine using
 * {@code CRYPTPROTECT_LOCALMACHINE}, so they cannot be decrypted on another
 * device. Any user on this machine can decrypt, which is appropriate for a
 * single-user desktop application. DPAPI keys are managed by Windows and are
 * tied to the machine's certificate store, so no key material is ever stored
 * in the application or database.
 * <p>
 * If DPAPI is unavailable (non-Windows or JNA failure), this service reports
 * {@link #isAvailable()} as {@code false} and callers should fall back to
 * {@link AesGcmEncryptionService}.
 */
public class WindowsDpapiEncryptionService implements EncryptionService {

    private static final int CRYPTPROTECT_LOCALMACHINE = 0x00000004;
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x00000001;
    private static final String FORMAT_PREFIX = "v1:";

    public interface Crypt32 extends com.sun.jna.Library {
        Crypt32 INSTANCE = Native.load("crypt32", Crypt32.class);

        boolean CryptProtectData(
            DATA_BLOB pData,
            String szDataDescr,
            DATA_BLOB pOptionalEntropy,
            PointerByReference pReserved,
            PointerByReference pPromptStruct,
            int dwFlags,
            DATA_BLOB pDataOut
        );

        boolean CryptUnprotectData(
            DATA_BLOB pData,
            PointerByReference pszDataDescr,
            DATA_BLOB pOptionalEntropy,
            PointerByReference pReserved,
            PointerByReference pPromptStruct,
            int dwFlags,
            DATA_BLOB pDataOut
        );
    }

    public static class DATA_BLOB extends com.sun.jna.Structure {
        public int cbData;
        public Pointer pbData;

        public DATA_BLOB() {
            super();
        }

        public DATA_BLOB(byte[] data) {
            super();
            this.cbData = data.length;
            this.pbData = new Memory(data.length);
            this.pbData.write(0, data, 0, data.length);
        }

        @Override
        public java.util.List<String> getFieldOrder() {
            return java.util.List.of("cbData", "pbData");
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        DATA_BLOB dataIn = new DATA_BLOB(data);
        DATA_BLOB dataOut = new DATA_BLOB();

        try {
            boolean result = Crypt32.INSTANCE.CryptProtectData(
                dataIn,
                "MOMENTUM AI key",
                null,
                null,
                null,
                CRYPTPROTECT_LOCALMACHINE | CRYPTPROTECT_UI_FORBIDDEN,
                dataOut
            );

            if (!result) {
                throw new IllegalStateException(
                    "CryptProtectData failed with error code 0x"
                        + Integer.toHexString(Native.getLastError()));
            }

            byte[] encrypted = new byte[dataOut.cbData];
            dataOut.pbData.read(0, encrypted, 0, encrypted.length);

            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(FORMAT_PREFIX)) {
            throw new IllegalStateException("Unknown or missing ciphertext format prefix");
        }
        byte[] encrypted = Base64.getDecoder().decode(ciphertext.substring(FORMAT_PREFIX.length()));
        DATA_BLOB dataIn = new DATA_BLOB(encrypted);
        DATA_BLOB dataOut = new DATA_BLOB();

        try {
            boolean result = Crypt32.INSTANCE.CryptUnprotectData(
                dataIn,
                null,
                null,
                null,
                null,
                CRYPTPROTECT_UI_FORBIDDEN,
                dataOut
            );

            if (!result) {
                throw new IllegalStateException(
                    "CryptUnprotectData failed - key may not be decryptable on this machine");
            }

            byte[] decrypted = new byte[dataOut.cbData];
            dataOut.pbData.read(0, decrypted, 0, decrypted.length);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String test = "DPAPI-availability-test";
            String encrypted = encrypt(test);
            String decrypted = decrypt(encrypted);
            return test.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }
}
