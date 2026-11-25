package io.github.intruder_tech.blackdoor.state;

/**
 * Ring buffer of recent client positions and rotations in primitive arrays (no per-entry
 * allocation). Single writer: the connection's network thread.
 */
public final class MovementHistory {

    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final float[] yaw;
    private final float[] pitch;
    private final boolean[] onGround;
    private int next;
    private int size;

    public MovementHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        x = new double[capacity];
        y = new double[capacity];
        z = new double[capacity];
        yaw = new float[capacity];
        pitch = new float[capacity];
        onGround = new boolean[capacity];
    }

    public void record(double px, double py, double pz, float pyaw, float ppitch, boolean ground) {
        x[next] = px;
        y[next] = py;
        z[next] = pz;
        yaw[next] = pyaw;
        pitch[next] = ppitch;
        onGround[next] = ground;
        next = (next + 1) % x.length;
        if (size < x.length) {
            size++;
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return x.length;
    }

    /** Index 0 is the newest entry. */
    public double x(int ago) {
        return x[index(ago)];
    }

    public double y(int ago) {
        return y[index(ago)];
    }

    public double z(int ago) {
        return z[index(ago)];
    }

    public float yaw(int ago) {
        return yaw[index(ago)];
    }

    public float pitch(int ago) {
        return pitch[index(ago)];
    }

    public boolean onGround(int ago) {
        return onGround[index(ago)];
    }

    private int index(int ago) {
        if (ago < 0 || ago >= size) {
            throw new IndexOutOfBoundsException("ago=" + ago + " size=" + size);
        }
        return Math.floorMod(next - 1 - ago, x.length);
    }
}
