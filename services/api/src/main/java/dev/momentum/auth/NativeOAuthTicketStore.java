package dev.momentum.auth;

import dev.momentum.core.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Short-lived, single-use bridge from an OAuth browser callback to a native app. */
public final class NativeOAuthTicketStore {
    private record Ticket(AccountStore.AuthResult result, Instant expiresAt) {}

    private final ConcurrentMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Duration lifetime;

    public NativeOAuthTicketStore() {
        this(Duration.ofMinutes(2));
    }

    public NativeOAuthTicketStore(Duration lifetime) {
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("Ticket lifetime must be positive.");
        }
        this.lifetime = lifetime;
    }

    public String issue(AccountStore.AuthResult result) {
        cleanup();
        String ticket = Tokens.randomToken(32);
        tickets.put(ticket, new Ticket(result, Instant.now().plus(lifetime)));
        return ticket;
    }

    public AccountStore.AuthResult redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        Ticket value = tickets.remove(ticket);
        if (value == null || value.expiresAt().isBefore(Instant.now())) return null;
        return value.result();
    }

    private void cleanup() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
