package io.github.intruder_tech.blackdoor.benchmarks.network;

import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.network.ConnectionGuard;
import io.github.intruder_tech.blackdoor.network.NetworkSettings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;

/**
 * Cost of the connection guard per new connection (handshake plus login), against the budget in
 * docs/PERFORMANCE_BUDGET.md. A plain timing loop with warm-up, not JMH.
 */
public final class NetworkOverhead {

    private static final int OPERATIONS = 100_000;

    private NetworkOverhead() {
    }

    public static void main(String[] args) throws UnknownHostException {
        InetAddress[] addresses = new InetAddress[10_000];
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = InetAddress.getByAddress(new byte[]{(byte) 203, 0, (byte) (i >> 8), (byte) i});
        }
        for (int online : new int[]{0, 100, 500, 2000}) {
            for (int round = 0; round < 3; round++) {
                double micros = measure(addresses, online);
                if (round == 2) {
                    System.out.printf("players online %5d: %.2f us per new connection (flood, unknown addresses)%n",
                            online, micros);
                }
            }
        }
    }

    private static double measure(InetAddress[] addresses, int online) throws UnknownHostException {
        NetworkSettings.Limit limit = new NetworkSettings.Limit(30, 3);
        NetworkSettings settings = new NetworkSettings(NetworkSettings.Mode.MITIGATE, limit, limit, limit, 255,
                50_000, Set.of(), true, Set.of(), new NetworkSettings.Adaptive(NetworkSettings.Mode.MITIGATE, 10, 2, 60));
        ConnectionGuard guard = new ConnectionGuard(finding -> {
        }, TimeSource.system(), () -> settings, () -> true, () -> false);
        for (int i = 0; i < online; i++) {
            guard.joined(new UUID(1, i), InetAddress.getByAddress(new byte[]{(byte) 198, 51, (byte) (i >> 8), (byte) i}));
        }
        long start = System.nanoTime();
        for (int i = 0; i < OPERATIONS; i++) {
            InetAddress address = addresses[i % addresses.length];
            guard.handshake(address, 20);
            guard.loginStart(address, "Player");
        }
        return (System.nanoTime() - start) / 1000.0 / OPERATIONS;
    }
}
