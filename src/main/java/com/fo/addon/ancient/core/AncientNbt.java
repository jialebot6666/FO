package com.fo.addon.ancient.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * NBT 二进制解析（纯逻辑，可单测，零依赖）。
 *
 * <p>复刻 Minecraft 结构模板（.nbt）的二进制格式。返回结构：
 * Compound → {@code Map<String,Object>}；List → {@code List<Object>}；
 * 各数值类型为对应的 Java 包装类型；byte[]/int[]/long[] 为数组。
 * 支持 gzip 压缩的 NBT。
 */
public final class AncientNbt {

    private final byte[] data;
    private int pos;

    private AncientNbt(byte[] data) {
        this.data = data;
    }

    /** 解析 NBT（自动识别 gzip）。返回根的 payload（通常是 Compound）。 */
    public static Object read(byte[] raw) {
        byte[] d = raw;
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                d = in.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException("NBT gzip 解压失败", e);
            }
        }
        AncientNbt nbt = new AncientNbt(d);
        int type = nbt.readByte();
        nbt.readString(); // 根名（忽略）
        return nbt.readPayload(type);
    }

    private int readByte() {
        return data[pos++];
    }

    private int readUnsignedByte() {
        return data[pos++] & 0xFF;
    }

    private short readShort() {
        short v = (short) (((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF));
        pos += 2;
        return v;
    }

    private int readInt() {
        int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
            | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }

    private long readLong() {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (data[pos + i] & 0xFF);
        pos += 8;
        return v;
    }

    private String readString() {
        int len = (data[pos] & 0xFF) << 8 | (data[pos + 1] & 0xFF);
        pos += 2;
        String s = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return s;
    }

    private Object readPayload(int type) {
        switch (type) {
            case 1: return (byte) readByte();
            case 2: return readShort();
            case 3: return readInt();
            case 4: return readLong();
            case 5: return Float.intBitsToFloat(readInt());
            case 6: return Double.longBitsToDouble(readLong());
            case 7: {
                int len = readInt();
                byte[] a = new byte[len];
                System.arraycopy(data, pos, a, 0, len);
                pos += len;
                return a;
            }
            case 8: return readString();
            case 9: {
                int elemType = readUnsignedByte();
                int len = readInt();
                List<Object> list = new ArrayList<>(Math.max(len, 0));
                if (elemType == 0 || len <= 0) return list;
                for (int i = 0; i < len; i++) list.add(readPayload(elemType));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = readUnsignedByte();
                    if (t == 0) break;
                    String name = readString();
                    map.put(name, readPayload(t));
                }
                return map;
            }
            case 11: {
                int len = readInt();
                int[] a = new int[len];
                for (int i = 0; i < len; i++) a[i] = readInt();
                return a;
            }
            case 12: {
                int len = readInt();
                long[] a = new long[len];
                for (int i = 0; i < len; i++) a[i] = readLong();
                return a;
            }
            default:
                throw new IllegalStateException("未知 NBT 类型: " + type);
        }
    }
}
