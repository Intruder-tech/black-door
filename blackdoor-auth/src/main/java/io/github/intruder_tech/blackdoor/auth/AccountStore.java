package io.github.intruder_tech.blackdoor.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accounts in one tab-separated file, rewritten atomically. A file that cannot be parsed is never
 * overwritten: loading fails and the login system stays locked until it is repaired. Not
 * thread-safe; {@link AuthService} guards it.
 */
final class AccountStore {

    /** {@code sessionAddress} is a digest, never the address itself. */
    record Account(UUID id, String name, String passwordHash, String sessionAddress, long sessionAtMillis) {
    }

    private final Path file;
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();

    AccountStore(Path directory) throws IOException {
        this.file = directory.resolve("accounts.tsv");
        if (!Files.exists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            try {
                if (f.length != 5) {
                    throw new IllegalArgumentException("expected 5 fields");
                }
                Account account = new Account(UUID.fromString(f[0]), f[1], f[2], f[3], Long.parseLong(f[4]));
                accounts.put(account.id(), account);
            } catch (IllegalArgumentException e) {
                throw new IOException("Damaged account file " + file.getFileName() + ": " + e.getMessage(), e);
            }
        }
    }

    Account get(UUID id) {
        return accounts.get(id);
    }

    Account findByName(String name) {
        return accounts.values().stream().filter(a -> a.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    void put(Account account) throws IOException {
        Account previous = accounts.put(account.id(), account);
        try {
            save();
        } catch (IOException e) {
            if (previous == null) {
                accounts.remove(account.id());
            } else {
                accounts.put(account.id(), previous);
            }
            throw e;
        }
    }

    void remove(UUID id) throws IOException {
        Account previous = accounts.remove(id);
        try {
            save();
        } catch (IOException e) {
            accounts.put(id, previous);
            throw e;
        }
    }

    private void save() throws IOException {
        List<String> lines = new ArrayList<>(accounts.size());
        for (Account a : accounts.values()) {
            lines.add(String.join("\t", a.id().toString(), a.name().replaceAll("[\\t\\r\\n]", ""), a.passwordHash(),
                    a.sessionAddress(), Long.toString(a.sessionAtMillis())));
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
