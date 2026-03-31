package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists per-player story flags (String → int) to an AES-encrypted local file.
 * Booleans are stored as 0/1. Integers are used for counters (e.g. combats won).
 *
 * One instance lives on Main and is shared across all screens via Main.flags.
 *
 * File location: ~/.haven/player.dat  (user home dir, not the game install dir)
 *
 * Migration path to Steam Cloud Save: replace save() / load() with Steamworks
 * calls — nothing else in the codebase needs to change.
 */
public class PlayerFlags {

    // AES-128 key (16 bytes). Changing this invalidates all existing save files.
    private static final byte[] KEY = {
        (byte)0x48,(byte)0x61,(byte)0x76,(byte)0x65,
        (byte)0x6e,(byte)0x47,(byte)0x61,(byte)0x6d,
        (byte)0x65,(byte)0x4b,(byte)0x65,(byte)0x79,
        (byte)0x31,(byte)0x39,(byte)0x38,(byte)0x37
    }; // "HavenGameKey1987"

    private static final String SAVE_DIR  =
            System.getProperty("user.home") + File.separator + ".haven";
    private static final String SAVE_PATH =
            SAVE_DIR + File.separator + "player.dat";

    private final Map<String, Integer> data = new HashMap<>();

    public PlayerFlags() {
        load();
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    /** Returns the integer value of a flag, or 0 if not set. */
    public int get(String key) {
        Integer v = data.get(key);
        return v != null ? v : 0;
    }

    /** Returns true if the flag is non-zero. */
    public boolean is(String key) {
        return get(key) != 0;
    }

    /**
     * Evaluates a condition: get(key) op threshold.
     * Supported ops: >=  <=  ==  >  <
     */
    public boolean check(String key, String op, int threshold) {
        int v = get(key);
        switch (op) {
            case ">=": return v >= threshold;
            case "<=": return v <= threshold;
            case "==": return v == threshold;
            case ">":  return v >  threshold;
            case "<":  return v <  threshold;
            default:   return false;
        }
    }

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------

    /** Sets a flag to an exact value and immediately persists to disk. */
    public void set(String key, int value) {
        data.put(key, value);
        save();
    }

    /** Increments a flag by 1 and immediately persists. */
    public void increment(String key) {
        set(key, get(key) + 1);
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private void save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            byte[] enc = encrypt(sb.toString().getBytes("UTF-8"));
            new File(SAVE_DIR).mkdirs();
            try (FileOutputStream fos = new FileOutputStream(SAVE_PATH)) {
                fos.write(enc);
            }
        } catch (Exception e) {
            Gdx.app.error("PlayerFlags", "Save failed: " + e.getMessage());
        }
    }

    private void load() {
        File f = new File(SAVE_PATH);
        if (!f.exists()) return;
        try {
            byte[] raw  = readAllBytes(f);
            String text = new String(decrypt(raw), "UTF-8");
            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                try {
                    data.put(line.substring(0, eq),
                             Integer.parseInt(line.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            Gdx.app.error("PlayerFlags", "Load failed — starting fresh: " + e.getMessage());
            data.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] readAllBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    private static byte[] encrypt(byte[] in) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"));
        return c.doFinal(in);
    }

    private static byte[] decrypt(byte[] in) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"));
        return c.doFinal(in);
    }
}
