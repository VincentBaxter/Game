package com.mygame.tactics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * Represents a single cutscene loaded from a plain-text script file.
 *
 * -----------------------------------------------------------------------
 * Script format  (save as assets/cutscenes/<triggerAreaId>.txt)
 * -----------------------------------------------------------------------
 *
 *   # Lines starting with # are comments and are ignored.
 *
 *   BACKGROUND filename.png       — full-screen background image (from assets/)
 *
 *   SAY SpeakerName: Text here.   — dialogue line attributed to a character
 *   NARRATOR Text here.           — narration with no speaker label
 *
 *   FLAG flagKey 1                — set the named flag to an integer value
 *   FLAG flagKey increment        — increment the named flag by 1
 *
 *   CHOICE Prompt text?           — present a set of choices; player must pick one
 *   OPTION Option label text      — one choice button; sub-beats follow until
 *   SAY Speaker: Response.            the next OPTION or END_CHOICE
 *   FLAG some_flag 1
 *   OPTION Another option
 *   FLAG some_flag 2
 *   END_CHOICE                    — closes the CHOICE block; dialogue continues
 *                                   for all paths after this line
 *
 *   BACKGROUND newimage.png       — mid-scene background swap (processed instantly)
 *
 * -----------------------------------------------------------------------
 * Example
 * -----------------------------------------------------------------------
 *
 *   BACKGROUND cave_entrance.png
 *   NARRATOR The cave mouth yawns before you.
 *   SAY Mason: I've been waiting a long time for someone like you.
 *   CHOICE How do you respond?
 *   OPTION Step inside.
 *   SAY Mason: Good. Follow me.
 *   FLAG tutorial_entered 1
 *   OPTION Turn back for now.
 *   NARRATOR You decide to come back when you're ready.
 *   FLAG tutorial_entered 0
 *   END_CHOICE
 *   SAY Mason: The path ahead won't be easy.
 *
 * -----------------------------------------------------------------------
 * Notes
 * -----------------------------------------------------------------------
 *  - CHOICE blocks can be nested inside OPTION blocks.
 *  - FLAG beats are applied silently (no on-screen pause).
 *  - BACKGROUND changes mid-scene are also silent.
 *  - The cutscene ends when all beats have been processed.
 */
public class CutsceneData {

    /** Relative path of the initial background image (within assets/). May be null. */
    public String      backgroundPath;
    public Array<Beat> beats = new Array<>();

    // =========================================================
    // Beat types
    // =========================================================

    public static abstract class Beat {}

    /** A line of spoken dialogue. speaker == null means narration (no name label). */
    public static class DialogueBeat extends Beat {
        public final String speaker; // null for NARRATOR lines
        public final String text;
        public DialogueBeat(String speaker, String text) {
            this.speaker = speaker;
            this.text    = text;
        }
    }

    /**
     * Presents a list of choices. The player picks one; that option's sub-beats
     * are inserted at the front of the queue, then the main sequence resumes.
     */
    public static class ChoiceBeat extends Beat {
        public final String        prompt;
        public final Array<Option> options = new Array<>();
        public ChoiceBeat(String prompt) { this.prompt = prompt; }
    }

    public static class Option {
        public final String      label;
        public final Array<Beat> beats = new Array<>();
        public Option(String label) { this.label = label; }
    }

    /** Sets or increments a PlayerFlag. Applied silently with no screen pause. */
    public static class FlagBeat extends Beat {
        public final String  key;
        public final boolean increment; // true = increment by 1; false = set to value
        public final int     value;     // only used when !increment
        public FlagBeat(String key, boolean increment, int value) {
            this.key       = key;
            this.increment = increment;
            this.value     = value;
        }
    }

    /** Swaps the background image mid-cutscene. Applied silently. */
    public static class BackgroundChangeBeat extends Beat {
        public final String path;
        public BackgroundChangeBeat(String path) { this.path = path; }
    }

    // =========================================================
    // Script loader
    // =========================================================

    public static CutsceneData load(FileHandle file) {
        // Strip blank lines and comments, trim whitespace
        Array<String> lines = new Array<>();
        for (String raw : file.readString().split("\n")) {
            String t = raw.trim();
            if (!t.isEmpty() && !t.startsWith("#")) lines.add(t);
        }

        CutsceneData data  = new CutsceneData();
        int[]        idx   = {0}; // mutable index passed into recursive parser

        if (idx[0] < lines.size && lines.get(idx[0]).startsWith("BACKGROUND ")) {
            data.backgroundPath = lines.get(idx[0]).substring("BACKGROUND ".length()).trim();
            idx[0]++;
        }

        data.beats = parseBeats(lines, idx, false);
        return data;
    }

    /**
     * Parses a sequence of beats starting at idx[0].
     *
     * @param insideOption  When true, stop (without consuming) on OPTION or END_CHOICE
     *                      so the caller can handle the option boundary.
     */
    private static Array<Beat> parseBeats(Array<String> lines, int[] idx, boolean insideOption) {
        Array<Beat> beats = new Array<>();

        while (idx[0] < lines.size) {
            String line = lines.get(idx[0]);

            // Let the parent CHOICE loop handle these boundaries
            if (insideOption && (line.startsWith("OPTION ") || line.equals("END_CHOICE"))) break;

            idx[0]++;

            if (line.startsWith("SAY ")) {
                String rest  = line.substring(4);
                int    colon = rest.indexOf(':');
                if (colon >= 0) {
                    beats.add(new DialogueBeat(
                            rest.substring(0, colon).trim(),
                            rest.substring(colon + 1).trim()));
                } else {
                    beats.add(new DialogueBeat(null, rest.trim()));
                }

            } else if (line.startsWith("NARRATOR ")) {
                beats.add(new DialogueBeat(null, line.substring("NARRATOR ".length()).trim()));

            } else if (line.startsWith("FLAG ")) {
                String[] parts = line.substring(5).trim().split("\\s+", 2);
                if (parts.length == 2) {
                    if (parts[1].equalsIgnoreCase("increment")) {
                        beats.add(new FlagBeat(parts[0], true, 0));
                    } else {
                        try {
                            beats.add(new FlagBeat(parts[0], false,
                                    Integer.parseInt(parts[1])));
                        } catch (NumberFormatException ignored) {}
                    }
                }

            } else if (line.startsWith("CHOICE ")) {
                ChoiceBeat choice = new ChoiceBeat(
                        line.substring("CHOICE ".length()).trim());

                // Collect options until END_CHOICE
                while (idx[0] < lines.size && !lines.get(idx[0]).equals("END_CHOICE")) {
                    String ol = lines.get(idx[0]);
                    if (ol.startsWith("OPTION ")) {
                        idx[0]++;
                        Option opt = new Option(ol.substring("OPTION ".length()).trim());
                        opt.beats.addAll(parseBeats(lines, idx, true)); // recursive
                        choice.options.add(opt);
                    } else {
                        idx[0]++; // skip unexpected lines inside a CHOICE block
                    }
                }
                if (idx[0] < lines.size) idx[0]++; // consume END_CHOICE

                beats.add(choice);

            } else if (line.startsWith("BACKGROUND ")) {
                beats.add(new BackgroundChangeBeat(
                        line.substring("BACKGROUND ".length()).trim()));
            }
            // Any unrecognised line is silently ignored
        }

        return beats;
    }
}
