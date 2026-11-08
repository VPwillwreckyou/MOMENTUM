package dev.momentum;

import dev.momentum.auth.AccountStore;
import dev.momentum.core.Json;
import dev.momentum.core.PasswordHasher;
import dev.momentum.core.RateLimiter;
import dev.momentum.sync.SyncStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Lightweight dependency-free test runner for the Java service. */
public final class TestRunner {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        test("json parses nested data and escapes", TestRunner::jsonRoundTrip);
        test("json rejects malformed input", TestRunner::jsonRejectsMalformed);
        test("password hashing verifies and rejects", TestRunner::passwordHashing);
        test("rate limiter enforces its window", TestRunner::rateLimiter);
        test("account registration, login and sessions", TestRunner::accounts);
        test("oauth links by identity and verified email", TestRunner::oauthAccounts);
        test("sync store enforces optimistic revisions", TestRunner::syncStore);
        test("sync store rejects a malformed envelope", TestRunner::syncRejectsMalformed);
        test("backup import round trips goals, tasks and attachments", DataMigrationTest::importRoundTrip);
        test("failed import rolls back and keeps original data intact", DataMigrationTest::rollbackOnCorruptBackup);
        test("attachment size mismatch aborts migration", DataMigrationTest::rollbackOnAttachmentSizeMismatch);
        test("database backups keep the newest five", DataMigrationTest::backupRetentionPolicy);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void test(String name, ThrowingRunnable runnable) {
        try {
            runnable.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable throwable) {
            failed++;
            System.out.println("FAIL  " + name + " — " + throwable.getMessage());
            throwable.printStackTrace(System.out);
        }
    }

    private static void jsonRoundTrip() {
        String source = "{\"title\":\"Ship \\\"MOMENTUM\\\"\",\"items\":[1,true,null,2.5],\"note\":\"caf\\u00e9\"}";
        Map<String, Object> parsed = Json.parseObject(source);
        assertEquals("Ship \"MOMENTUM\"", parsed.get("title"));
        List<?> items = (List<?>) parsed.get("items");
        assertEquals(4, items.size());
        assertEquals(true, items.get(1));
        assertEquals("café", parsed.get("note"));
        Map<String, Object> reparsed = Json.parseObject(Json.stringify(parsed));
        assertEquals(parsed.get("title"), reparsed.get("title"));
        assertTrue(Json.pretty(parsed).contains("\n"));
    }

    private static void jsonRejectsMalformed() {
        for (String invalid : List.of("{", "{\"a\":}", "{\"a\":1} trailing", "[1,]")) {
            boolean threw = false;
            try {
                Json.parse(invalid);
            } catch (Json.JsonException expected) {
                threw = true;
            }
            assertTrue(threw);
        }
    }

    private static void passwordHashing() {
        String hash = PasswordHasher.hash("correct horse 42");
        assertTrue(hash.startsWith("pbkdf2$210000$"));
        assertTrue(PasswordHasher.verify("correct horse 42", hash));
        assertFalse(PasswordHasher.verify("wrong password", hash));
        assertFalse(PasswordHasher.verify("correct horse 42", "broken"));
    }

    private static void rateLimiter() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));
        assertTrue(limiter.allow("client"));
        assertTrue(limiter.allow("client"));
        assertFalse(limiter.allow("client"));
        assertTrue(limiter.allow("other"));
        assertEquals(2, limiter.limit());
    }

    private static void accounts() throws Exception {
        Path dir = Files.createTempDirectory("momentum-accounts-");
        try {
            AccountStore store = new AccountStore(dir);
            AccountStore.AuthResult result = store.register("Test@Example.com", "momentum42", "Test User");
            assertEquals("test@example.com", result.user().email());
            assertTrue(result.token().length() > 20);
            assertTrue(store.sessionUser(result.token()).isPresent());
            boolean duplicate = false;
            try {
                store.register("test@example.com", "momentum42", "Another User");
            } catch (AccountStore.AuthException expected) {
                duplicate = true;
            }
            assertTrue(duplicate);
            boolean wrongPassword = false;
            try {
                store.login("test@example.com", "not-the-password");
            } catch (AccountStore.AuthException expected) {
                assertEquals(401, expected.status());
                wrongPassword = true;
            }
            assertTrue(wrongPassword);
            AccountStore.AuthResult login = store.login("test@example.com", "momentum42");
            assertEquals(result.user().id(), login.user().id());
            store.logout(login.token());
            assertTrue(store.sessionUser(login.token()).isEmpty());
            AccountStore reopened = new AccountStore(dir);
            assertTrue(reopened.findByEmail("test@example.com").isPresent());
            store.deleteUser(result.user().id());
            assertTrue(store.findById(result.user().id()).isEmpty());
        } finally {
            deleteTree(dir);
        }
    }

    private static void oauthAccounts() throws Exception {
        Path dir = Files.createTempDirectory("momentum-oauth-");
        try {
            AccountStore store = new AccountStore(dir);
            AccountStore.AuthResult local = store.register("person@example.com", "momentum42", "Person");
            AccountStore.AuthResult linked = store.oauth(
                    "github", "github-123", "person@example.com", "Person GitHub", true);
            assertEquals(local.user().id(), linked.user().id());
            AccountStore.AuthResult repeat = store.oauth(
                    "github", "github-123", null, null, false);
            assertEquals(local.user().id(), repeat.user().id());
            boolean refused = false;
            try {
                store.oauth("discord", "discord-1", "person@example.com", "Impostor", false);
            } catch (AccountStore.AuthException expected) {
                refused = true;
            }
            assertTrue(refused);
        } finally {
            deleteTree(dir);
        }
    }

    private static void syncStore() throws Exception {
        Path dir = Files.createTempDirectory("momentum-sync-");
        try {
            SyncStore store = new SyncStore(dir);
            String envelope = "{\"version\":1,\"salt\":\"abc\",\"iv\":\"def\",\"ciphertext\":\"ghi\"}";
            assertTrue(store.get("user-1").isEmpty());
            SyncStore.Snapshot first = store.put("user-1", envelope, 0);
            assertEquals(1L, first.revision());
            assertTrue(store.get("user-1").isPresent());
            boolean conflict = false;
            try {
                store.put("user-1", envelope, 0);
            } catch (SyncStore.ConflictException expected) {
                conflict = true;
                assertEquals(1L, expected.revision());
            }
            assertTrue(conflict);
            SyncStore.Snapshot second = store.put("user-1", envelope, 1);
            assertEquals(2L, second.revision());
            store.delete("user-1");
            assertTrue(store.get("user-1").isEmpty());
        } finally {
            deleteTree(dir);
        }
    }

    private static void syncRejectsMalformed() throws Exception {
        Path dir = Files.createTempDirectory("momentum-sync-bad-");
        try {
            SyncStore store = new SyncStore(dir);
            boolean threw = false;
            try {
                store.put("user-1", "{\"version\":1}", 0);
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            assertTrue(threw);
            boolean invalidId = false;
            try {
                store.put("../escape", "{}", 0);
            } catch (IllegalArgumentException expected) {
                invalidId = true;
            }
            assertTrue(invalidId);
        } finally {
            deleteTree(dir);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected condition to be true");
    }

    private static void assertFalse(boolean condition) {
        if (condition) throw new AssertionError("Expected condition to be false");
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Best-effort cleanup of test data.
                }
            });
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
