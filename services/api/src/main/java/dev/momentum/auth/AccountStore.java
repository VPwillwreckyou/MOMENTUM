package dev.momentum.auth;

import dev.momentum.core.Json;
import dev.momentum.core.PasswordHasher;
import dev.momentum.core.Tokens;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * File-backed accounts and sessions. Goal data lives elsewhere and is never
 * part of this store. Session tokens are SHA-256 hashed at rest.
 */
public final class AccountStore {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final int MAX_USERS = 100_000;

    public record User(
            String id,
            String email,
            String name,
            String passwordHash,
            Map<String, String> identities,
            String createdAt,
            String updatedAt) {}

    public record Session(String tokenHash, String userId, String createdAt, String expiresAt) {}

    public record AuthResult(User user, String token, Instant expiresAt) {}

    private final Path file;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    public AccountStore(Path dataDirectory) {
        this.file = dataDirectory.resolve("accounts.json");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the data directory.", exception);
        }
        load();
    }

    public synchronized Optional<User> findByEmail(String rawEmail) {
        if (rawEmail == null) return Optional.empty();
        String email = normalizeEmail(rawEmail);
        return users.values().stream().filter(user -> email.equals(user.email())).findFirst();
    }

    public synchronized Optional<User> findById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public synchronized Optional<User> findByIdentity(String provider, String subject) {
        return users.values().stream()
                .filter(user -> subject.equals(user.identities().get(provider)))
                .findFirst();
    }

    public synchronized AuthResult register(String rawEmail, String password, String rawName) {
        String email = normalizeEmail(rawEmail);
        String name = cleanName(rawName);
        if (!EMAIL.matcher(email).matches()) throw new AuthException("Enter a valid email address.");
        if (name.length() < 2) throw new AuthException("Enter your name.");
        validatePassword(password);
        if (findByEmail(email).isPresent()) throw new AuthException("An account with this email already exists.");
        if (users.size() >= MAX_USERS) throw new AuthException("This server is not accepting new accounts.");
        String now = Instant.now().toString();
        User user = new User(UUID.randomUUID().toString(), email, name, PasswordHasher.hash(password),
                new LinkedHashMap<>(), now, now);
        users.put(user.id(), user);
        persist();
        return createSession(user);
    }

    public synchronized AuthResult login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        User user = findByEmail(email).orElse(null);
        if (user == null || user.passwordHash() == null || !PasswordHasher.verify(password, user.passwordHash())) {
            // Equalize obvious timing differences between unknown accounts and bad passwords.
            PasswordHasher.verify(password == null ? "" : password,
                    "pbkdf2$210000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
            throw new AuthException("Email or password is incorrect.", 401);
        }
        return createSession(user);
    }

    public synchronized AuthResult oauth(
            String provider, String subject, String rawEmail, String rawName, boolean emailVerified) {
        if (provider == null || subject == null || subject.isBlank()) {
            throw new AuthException("The identity provider returned an incomplete profile.");
        }
        String email = rawEmail == null || rawEmail.isBlank() ? null : normalizeEmail(rawEmail);
        Optional<User> identityUser = findByIdentity(provider, subject);
        String now = Instant.now().toString();
        if (identityUser.isPresent()) {
            User existing = identityUser.get();
            User updated = new User(existing.id(), email != null ? email : existing.email(),
                    cleanName(rawName).isEmpty() ? existing.name() : cleanName(rawName),
                    existing.passwordHash(), existing.identities(), existing.createdAt(), now);
            users.put(updated.id(), updated);
            persist();
            return createSession(updated);
        }
        if (email != null) {
            Optional<User> emailUser = findByEmail(email);
            if (emailUser.isPresent()) {
                if (!emailVerified) {
                    throw new AuthException("This email is already registered. Verify it with the provider or use another sign-in method.");
                }
                User existing = emailUser.get();
                Map<String, String> identities = new LinkedHashMap<>(existing.identities());
                identities.put(provider, subject);
                User linked = new User(existing.id(), existing.email(),
                        cleanName(rawName).isEmpty() ? existing.name() : cleanName(rawName),
                        existing.passwordHash(), identities, existing.createdAt(), now);
                users.put(linked.id(), linked);
                persist();
                return createSession(linked);
            }
        }
        if (users.size() >= MAX_USERS) throw new AuthException("This server is not accepting new accounts.");
        Map<String, String> identities = new LinkedHashMap<>();
        identities.put(provider, subject);
        User user = new User(UUID.randomUUID().toString(), email,
                cleanName(rawName).isEmpty() ? "MOMENTUM user" : cleanName(rawName), null, identities, now, now);
        users.put(user.id(), user);
        persist();
        return createSession(user);
    }

    public synchronized Optional<User> sessionUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String hash = Tokens.sha256(token);
        Session session = sessions.get(hash);
        if (session == null) return Optional.empty();
        if (Instant.parse(session.expiresAt()).isBefore(Instant.now())) {
            sessions.remove(hash);
            persist();
            return Optional.empty();
        }
        return findById(session.userId());
    }

    public synchronized void logout(String token) {
        if (token == null || token.isBlank()) return;
        if (sessions.remove(Tokens.sha256(token)) != null) persist();
    }

    /** Change a user's password after verifying the current one. */
    public synchronized void changePassword(String userId, String oldPassword, String newPassword) {
        User user = findById(userId)
                .orElseThrow(() -> new AuthException("User not found.", 404));
        if (user.passwordHash() == null || !PasswordHasher.verify(oldPassword, user.passwordHash())) {
            throw new AuthException("Current password is incorrect.", 401);
        }
        validatePassword(newPassword);
        User updated = new User(user.id(), user.email(), user.name(),
                PasswordHasher.hash(newPassword), user.identities(), user.createdAt(), Instant.now().toString());
        users.put(user.id(), updated);
        persist();
    }

    /** Sign out all devices: invalidate every session for the given user. */
    public synchronized void signOutAllDevices(String userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
        persist();
    }

    public synchronized void deleteUser(String userId) {
        users.remove(userId);
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
        persist();
    }

    public synchronized void cleanupSessions() {
        Instant now = Instant.now();
        boolean changed = sessions.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(now));
        if (changed) persist();
    }

    public synchronized int userCount() {
        return users.size();
    }

    public synchronized List<Map<String, Object>> allUsersForAdmin() {
        return users.values().stream()
                .sorted(Comparator.comparing(User::createdAt))
                .map(user -> view(user))
                .toList();
    }

    public static Map<String, Object> view(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id());
        view.put("email", user.email());
        view.put("name", user.name());
        view.put("provider", user.identities().isEmpty() ? "email" : user.identities().keySet().iterator().next());
        view.put("providers", new ArrayList<>(user.identities().keySet()));
        view.put("hasPassword", user.passwordHash() != null);
        view.put("createdAt", user.createdAt());
        return view;
    }

    private AuthResult createSession(User user) {
        String token = Tokens.randomToken(32);
        Instant expiry = Instant.now().plus(SESSION_LIFETIME);
        sessions.put(Tokens.sha256(token), new Session(Tokens.sha256(token), user.id(), Instant.now().toString(), expiry.toString()));
        sessions.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(Instant.now()));
        persist();
        return new AuthResult(user, token, expiry);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) throw new AuthException("Use at least 8 characters.");
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit)) {
            throw new AuthException("Include at least one letter and one number.");
        }
        if (password.length() > 200) throw new AuthException("Password is too long.");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT).substring(0, Math.min(254, email.trim().length()));
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.substring(0, Math.min(80, cleaned.length()));
    }

    private void load() {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) return;
            Map<String, Object> root = Json.parseObject(raw);
            for (Object item : asList(root.get("users"))) {
                Map<String, Object> record = asMap(item);
                if (record == null) continue;
                String id = text(record.get("id"));
                if (id == null) continue;
                Map<String, String> identities = new LinkedHashMap<>();
                Map<String, Object> rawIdentities = asMap(record.get("identities"));
                if (rawIdentities != null) {
                    rawIdentities.forEach((key, value) -> identities.put(key, text(value)));
                }
                User user = new User(id, text(record.get("email")), text(record.get("name")),
                        text(record.get("passwordHash")), identities, text(record.get("createdAt")),
                        text(record.get("updatedAt")));
                users.put(id, user);
            }
            for (Object item : asList(root.get("sessions"))) {
                Map<String, Object> record = asMap(item);
                if (record == null) continue;
                String hash = text(record.get("tokenHash"));
                if (hash == null) continue;
                sessions.put(hash, new Session(hash, text(record.get("userId")),
                        text(record.get("createdAt")), text(record.get("expiresAt"))));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Accounts data is unreadable. Restore or remove " + file, exception);
        }
    }

    private void persist() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        List<Object> userRecords = new ArrayList<>();
        for (User user : users.values()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", user.id());
            record.put("email", user.email());
            record.put("name", user.name());
            record.put("passwordHash", user.passwordHash());
            record.put("identities", user.identities());
            record.put("createdAt", user.createdAt());
            record.put("updatedAt", user.updatedAt());
            userRecords.add(record);
        }
        root.put("users", userRecords);
        List<Object> sessionRecords = new ArrayList<>();
        for (Session session : sessions.values()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("tokenHash", session.tokenHash());
            record.put("userId", session.userId());
            record.put("createdAt", session.createdAt());
            record.put("expiresAt", session.expiresAt());
            sessionRecords.add(record);
        }
        root.put("sessions", sessionRecords);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "accounts-", ".tmp");
            Files.writeString(temp, Json.pretty(root), StandardCharsets.UTF_8);
            restrictPermissions(temp);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save account data.", exception);
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs are inherited from the user profile directory.
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    public static final class AuthException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public AuthException(String message) {
            this(message, 400);
        }

        public AuthException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
