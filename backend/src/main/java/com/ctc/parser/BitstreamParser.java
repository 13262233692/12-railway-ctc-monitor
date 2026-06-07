package com.ctc.parser;

import com.ctc.interlocking.InterlockingConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class BitstreamParser {

    private static final Logger LOGGER = Logger.getLogger(BitstreamParser.class.getName());

    private static final String[] TRACK_CIRCUIT_IDS = InterlockingConfig.TRACK_CIRCUIT_IDS.toArray(new String[0]);
    private static final String[] SIGNAL_IDS = InterlockingConfig.SIGNAL_IDS.toArray(new String[0]);
    private static final String[] SWITCH_IDS = InterlockingConfig.SWITCH_IDS.toArray(new String[0]);
    private static final String[] ROUTE_IDS = {"Route1", "Route2", "Route3", "Route4", "Route5", "Route6"};

    private static final int MIN_FRAME_SIZE = 6;

    public Map<String, Object> parse(byte[] data) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (data == null || data.length < MIN_FRAME_SIZE) {
            result.put("valid", false);
            result.put("error", "数据长度不足: 需要" + MIN_FRAME_SIZE + "字节, 实际" + (data == null ? 0 : data.length));
            return result;
        }

        boolean checksumValid = validateChecksum(data);
        result.put("valid", checksumValid);

        if (!checksumValid) {
            result.put("error", "校验和失败");
            return result;
        }

        result.put("trackCircuits", parseTrackCircuits(data));
        result.put("signals", parseSignals(data));
        result.put("switches", parseSwitches(data));
        result.put("routes", parseRoutes(data));

        return result;
    }

    private boolean validateChecksum(byte[] data) {
        byte xor = 0;
        for (byte b : data) {
            xor ^= b;
        }
        return xor == (byte) 0xFF;
    }

    private Map<String, Boolean> parseTrackCircuits(byte[] data) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        int tcCount = Math.min(TRACK_CIRCUIT_IDS.length, 16);

        int word0 = Byte.toUnsignedInt(data[0]);
        int word1 = Byte.toUnsignedInt(data[1]);
        int combined = (word0 << 8) | word1;

        for (int i = 0; i < tcCount; i++) {
            boolean occupied = ((combined >> i) & 1) == 1;
            states.put(TRACK_CIRCUIT_IDS[i], occupied);
        }

        LOGGER.fine("解析轨道电路状态: " + states);
        return states;
    }

    private Map<String, String> parseSignals(byte[] data) {
        Map<String, String> states = new LinkedHashMap<>();
        int sigCount = Math.min(SIGNAL_IDS.length, 8);

        int word2 = Byte.toUnsignedInt(data[2]);
        int word3 = Byte.toUnsignedInt(data[3]);
        int combined = (word2 << 8) | word3;

        for (int i = 0; i < sigCount; i++) {
            int bits = (combined >> (i * 2)) & 0x03;
            String aspect = switch (bits) {
                case 0b00 -> "RED";
                case 0b01 -> "YELLOW";
                case 0b10 -> "GREEN";
                default -> "RED";
            };
            states.put(SIGNAL_IDS[i], aspect);
        }

        LOGGER.fine("解析信号机状态: " + states);
        return states;
    }

    private Map<String, Integer> parseSwitches(byte[] data) {
        Map<String, Integer> states = new LinkedHashMap<>();
        int swCount = Math.min(SWITCH_IDS.length, 8);

        int word4 = Byte.toUnsignedInt(data[4]);

        for (int i = 0; i < swCount; i++) {
            int position = (word4 >> i) & 1;
            states.put(SWITCH_IDS[i], position);
        }

        LOGGER.fine("解析道岔状态: " + states);
        return states;
    }

    private Map<String, Boolean> parseRoutes(byte[] data) {
        Map<String, Boolean> states = new LinkedHashMap<>();
        int routeCount = Math.min(ROUTE_IDS.length, 8);

        int word5 = Byte.toUnsignedInt(data[5]);

        for (int i = 0; i < routeCount; i++) {
            boolean setActive = ((word5 >> i) & 1) == 1;
            states.put(ROUTE_IDS[i], setActive);
        }

        LOGGER.fine("解析路线状态: " + states);
        return states;
    }

    public static byte[] computeChecksum(byte[] data) {
        byte xor = 0;
        for (int i = 0; i < data.length - 1; i++) {
            xor ^= data[i];
        }
        data[data.length - 1] = (byte) (xor ^ 0xFF);
        return data;
    }
}
