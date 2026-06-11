package com.saltdamage.util;

import org.springframework.stereotype.Component;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

@Component
public class IdGenerator {

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final long CUSTOM_EPOCH = 1420070400000L;

    private final long nodeId;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    public IdGenerator() {
        this.nodeId = createNodeId();
    }

    public IdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format("NodeId must be between %d and %d", 0, MAX_NODE_ID));
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return (currentTimestamp - CUSTOM_EPOCH) << (NODE_ID_BITS + SEQUENCE_BITS)
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    private long timestamp() {
        return Instant.now().toEpochMilli();
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }

    private long createNodeId() {
        long id;
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                }
            }
            id = sb.toString().hashCode();
        } catch (Exception e) {
            id = new SecureRandom().nextInt();
        }

        id = id & MAX_NODE_ID;
        if (id < 0) {
            id = Math.abs(id);
        }
        return id;
    }

    public static long parseTimestamp(long id) {
        return (id >> (NODE_ID_BITS + SEQUENCE_BITS)) + CUSTOM_EPOCH;
    }

    public static long parseNodeId(long id) {
        return (id >> SEQUENCE_BITS) & MAX_NODE_ID;
    }

    public static long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}
