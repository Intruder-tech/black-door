package io.github.intruder_tech.blackdoor.benchmarks.worldview;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal NBT reader for the spike (big-endian Java edition NBT). Not used in shipped code. */
final class NbtReader {

    private static final int MAX_DEPTH = 64;

    private NbtReader() {
    }

    /** Reads a named root compound and returns its content. */
    static Map<String, Object> readRoot(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        if (type != 10) {
            throw new IOException("root is not a compound: " + type);
        }
        readString(in);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) readPayload(in, 10, 0);
        return root;
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("nbt too deep");
        }
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] b = new byte[checkedLength(in.readInt())];
                in.readFully(b);
                return b;
            }
            case 8: return readString(in);
            case 9: {
                int elementType = in.readUnsignedByte();
                int length = checkedLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(length, 1024));
                for (int i = 0; i < length; i++) {
                    list.add(readPayload(in, elementType, depth + 1));
                }
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) {
                        return map;
                    }
                    String name = readString(in);
                    map.put(name, readPayload(in, t, depth + 1));
                }
            }
            case 11: {
                int[] a = new int[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("unknown nbt tag " + type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > 16_777_216) {
            throw new IOException("bad nbt length " + length);
        }
        return length;
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readUnsignedShort()];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
