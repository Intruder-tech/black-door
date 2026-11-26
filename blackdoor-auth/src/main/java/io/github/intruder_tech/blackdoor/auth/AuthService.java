package io.github.intruder_tech.blackdoor.auth;

import io.github.intruder_tech.blackdoor.auth.AccountStore.Account;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Who must still authenticate, and the rules for doing so. Unlike the detection parts of Black
 * Door this fails <em>closed</em>: if accounts cannot be read or written, a player stays locked.
 *
 * <p>{@link #register}, {@link #login} and {@link #reset} hash or write files, so call them from a
 * worker thread. The other methods are cheap and safe from any thread.
 */
public final class AuthService {

    public enum State { AUTHENTICATED, NEEDS_SIGNUP, NEEDS_SIGNIN }

    public enum Result {
        OK, ALREADY_REGISTERED, NOT_REGISTERED, NAME_TAKEN, MISMATCH, TOO_SHORT, TOO_LONG, SAME_AS_NAME,
        WRONG_PASSWORD, LOCKED_OUT, NOT_PENDING, BUSY, STORAGE_ERROR
    }

    public static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_TRACKED_FAILURES = 10_000;

    /** One locked connection. A new join creates a new instance, so a late result cannot unlock it. */
    private static final class Pending {
        final long sinceNanos;
        boolean busy;

        Pending(long sinceNanos) {
            this.sinceNanos = sinceNanos;
        }
    }

    private static final class Failures {
        int count;
        long lastNanos;
    }

    private final TimeSource time;
    private final Supplier<AuthSettings> settings;
    private final PasswordHasher hasher;
    private final AccountStore store;
    private final BiConsumer<String, Throwable> errors;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new LinkedHashMap<>(16, 0.75f, true);
    private final Object lock = new Object();

    public AuthService(Path directory, TimeSource time, Supplier<AuthSettings> settings, PasswordHasher hasher,
                       BiConsumer<String, Throwable> errors) {
        this.time = time;
        this.settings = settings;
        this.hasher = hasher;
        this.errors = errors;
        AccountStore loaded = null;
        try {
            loaded = new AccountStore(directory);
        } catch (IOException e) {
            errors.accept("Accounts could not be read; nobody can sign in until this is fixed", e);
        }
        this.store = loaded;
    }

    public boolean enabled() {
        return settings.get().enabled();
    }

    /** Call when a player joins. Decides whether the 24-hour session lets them straight in. */
    public State join(UUID id, String name, String address) {
        pending.remove(id);
        if (!enabled()) {
            return State.AUTHENTICATED;
        }
        State state = State.NEEDS_SIGNIN;
        if (store != null) {
            synchronized (lock) {
                Account account = store.get(id);
                if (account == null) {
                    state = State.NEEDS_SIGNUP;
                } else if (sessionValid(account, address)) {
                    return State.AUTHENTICATED;
                }
            }
        }
        pending.put(id, new Pending(time.nanoTime()));
        return state;
    }

    public boolean isBlocked(UUID id) {
        return enabled() && pending.containsKey(id);
    }

    /** Which command a locked player needs. */
    public State state(UUID id) {
        if (!isBlocked(id)) {
            return State.AUTHENTICATED;
        }
        if (store == null) {
            return State.NEEDS_SIGNIN;
        }
        synchronized (lock) {
            return store.get(id) == null ? State.NEEDS_SIGNUP : State.NEEDS_SIGNIN;
        }
    }

    public void quit(UUID id) {
        pending.remove(id);
    }

    public Result register(UUID id, String name, String address, String password, String repeated) {
        Pending current = pending.get(id);
        if (current == null) {
            return Result.NOT_PENDING;
        }
        if (store == null) {
            return Result.STORAGE_ERROR;
        }
        if (!password.equals(repeated)) {
            return Result.MISMATCH;
        }
        if (password.length() < settings.get().minPasswordLength()) {
            return Result.TOO_SHORT;
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return Result.TOO_LONG;
        }
        if (password.equalsIgnoreCase(name)) {
            return Result.SAME_AS_NAME;
        }
        synchronized (lock) {
            Result taken = taken(id, name);
            if (taken != null) {
                return taken;
            }
            if (current.busy) {
                return Result.BUSY;
            }
            current.busy = true;
        }
        try {
            String hash = hasher.hash(password);
            synchronized (lock) {
                if (pending.get(id) != current) {
                    return Result.NOT_PENDING;
                }
                Result taken = taken(id, name);
                if (taken != null) {
                    return taken;
                }
                store.put(new Account(id, name, hash, digest(id, address), time.currentTimeMillis()));
                pending.remove(id, current);
                return Result.OK;
            }
        } catch (IOException e) {
            errors.accept("Account could not be saved", e);
            return Result.STORAGE_ERROR;
        } finally {
            current.busy = false;
        }
    }

    public Result login(UUID id, String address, String password) {
        Pending current = pending.get(id);
        if (current == null) {
            return Result.NOT_PENDING;
        }
        if (store == null) {
            return Result.STORAGE_ERROR;
        }
        String key = id + "|" + address;
        Account account;
        synchronized (lock) {
            if (lockedOut(key)) {
                return Result.LOCKED_OUT;
            }
            account = store.get(id);
            if (account == null) {
                return Result.NOT_REGISTERED;
            }
            if (current.busy) {
                return Result.BUSY;
            }
            current.busy = true;
        }
        try {
            boolean right = password.length() <= MAX_PASSWORD_LENGTH
                    && hasher.verify(password, account.passwordHash());
            synchronized (lock) {
                if (pending.get(id) != current) {
                    return Result.NOT_PENDING;
                }
                if (!right) {
                    return recordFailure(key);
                }
                failures.remove(key);
                store.put(new Account(id, account.name(), account.passwordHash(), digest(id, address),
                        time.currentTimeMillis()));
                pending.remove(id, current);
                return Result.OK;
            }
        } catch (IOException e) {
            errors.accept("Session could not be saved", e);
            return Result.STORAGE_ERROR;
        } finally {
            current.busy = false;
        }
    }

    /** Deletes the account with this name, for a forgotten password. */
    public boolean reset(String name) {
        if (store == null) {
            return false;
        }
        synchronized (lock) {
            Account account = store.findByName(name);
            if (account == null) {
                return false;
            }
            try {
                store.remove(account.id());
                return true;
            } catch (IOException e) {
                errors.accept("Account could not be removed", e);
                return false;
            }
        }
    }

    /** Players still locked after the login timeout. */
    public List<UUID> timedOut() {
        List<UUID> out = new ArrayList<>();
        if (!enabled()) {
            return out;
        }
        long limit = settings.get().loginTimeoutSeconds() * 1_000_000_000L;
        long now = time.nanoTime();
        pending.forEach((id, p) -> {
            if (now - p.sinceNanos > limit) {
                out.add(id);
            }
        });
        return out;
    }

    private Result taken(UUID id, String name) {
        if (store.get(id) != null) {
            return Result.ALREADY_REGISTERED;
        }
        return store.findByName(name) != null ? Result.NAME_TAKEN : null;
    }

    private boolean sessionValid(Account account, String address) {
        if (address == null) {
            return false;
        }
        long age = time.currentTimeMillis() - account.sessionAtMillis();
        return age >= 0 && age < settings.get().sessionHours() * 3_600_000L
                && account.sessionAddress().equals(digest(account.id(), address));
    }

    private boolean lockedOut(String key) {
        Failures f = failures.get(key);
        if (f == null || f.count < settings.get().maxAttempts()) {
            return false;
        }
        if (time.nanoTime() - f.lastNanos > settings.get().lockoutMinutes() * 60_000_000_000L) {
            failures.remove(key);
            return false;
        }
        return true;
    }

    private Result recordFailure(String key) {
        Failures f = failures.computeIfAbsent(key, k -> new Failures());
        while (failures.size() > MAX_TRACKED_FAILURES) {
            var eldest = failures.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        f.count++;
        f.lastNanos = time.nanoTime();
        return f.count >= settings.get().maxAttempts() ? Result.LOCKED_OUT : Result.WRONG_PASSWORD;
    }

    private static String digest(UUID id, String address) {
        if (address == null) {
            return "";
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().withoutPadding()
                    .encodeToString(sha.digest((id + "|" + address).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
