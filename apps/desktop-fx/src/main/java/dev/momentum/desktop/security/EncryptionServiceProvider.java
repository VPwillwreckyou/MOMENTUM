package dev.momentum.desktop.security;

/**
 * Selects the best available encryption service for the current platform.
 * <p>
 * On Windows, Windows DPAPI ({@link WindowsDpapiEncryptionService}) is used
 * as the primary mechanism because it binds encryption to the machine
 * without exposing any key material in the application. If DPAPI is not
 * available, {@link AesGcmEncryptionService} is used as a fallback with a
 * machine-derived key.
 */
public final class EncryptionServiceProvider {

    private EncryptionServiceProvider() {}

    /**
     * Returns the best available {@link EncryptionService} for this platform.
     * The returned instance is a singleton within this process.
     */
    public static EncryptionService get() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final EncryptionService INSTANCE = create();

        private static EncryptionService create() {
            // Try Windows DPAPI first on Windows
            if (isWindows()) {
                WindowsDpapiEncryptionService dpapi = new WindowsDpapiEncryptionService();
                if (dpapi.isAvailable()) {
                    return dpapi;
                }
            }
            // Fall back to AES-GCM with machine-derived key
            return new AesGcmEncryptionService();
        }

        private static boolean isWindows() {
            String os = System.getProperty("os.name", "");
            return os.startsWith("Windows");
        }
    }
}
