package com.mygame.tactics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.characters.*;

/**
 * Parses a combat board .txt file produced by the map editor.
 *
 * File format:
 *   COMBAT_BOARD
 *   boardType=FOREST|WIND|DESERT
 *   spawn=CharacterName,tileX,tileY
 *   ...
 */
public class CombatBoardLoader {

    public static class Result {
        public final BoardConfig      config;
        public final Array<Character> team2;

        public Result(BoardConfig config, Array<Character> team2) {
            this.config = config;
            this.team2  = team2;
        }
    }

    /** Returns null if the file is missing or has an invalid header. */
    public static Result load(FileHandle file) {
        if (!file.exists()) return null;

        String[] lines = file.readString().split("\n");
        if (lines.length == 0 || !lines[0].trim().equals("COMBAT_BOARD")) return null;

        BoardConfig      config = BoardConfig.forest();
        Array<Character> team2  = new Array<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("boardType=")) {
                String typeName = line.substring("boardType=".length()).trim();
                try {
                    switch (BoardConfig.BoardType.valueOf(typeName)) {
                        case WIND:   config = BoardConfig.wind();   break;
                        case DESERT: config = BoardConfig.desert(); break;
                        default:     config = BoardConfig.forest(); break;
                    }
                } catch (IllegalArgumentException ignored) {}

            } else if (line.startsWith("spawn=")) {
                String[] parts = line.substring("spawn=".length()).split(",");
                if (parts.length >= 1) {
                    Character c = createCharacter(parts[0].trim());
                    if (c != null) {
                        c.team = 2;
                        team2.add(c);
                    }
                }
            }
        }

        return new Result(config, team2);
    }

    private static Character createCharacter(String name) {
        try {
            switch (name) {
                case "Aaron":      return new Aaron      (new Texture("aaron.png"));
                case "Aevan":      return new Aevan      (new Texture("aevan.png"));
                case "Anna":       return new Anna       (new Texture("anna.png"));
                case "Ben":        return new Ben        (new Texture("ben.png"));
                case "Billy":      return new Billy      (new Texture("billy.png"));
                case "Brad":       return new Brad       (new Texture("brad.png"));
                case "Emily":      return new Emily      (new Texture("emily.png"));
                case "Fescue":     return new Fescue     (new Texture("fescue.png"));
                case "Evan":       return new Evan       (new Texture("evan.png"));
                case "Ghia":       return new Ghia       (new Texture("ghia.png"));
                case "GuardTower": return new GuardTower (new Texture("guardtower.png"));
                case "Hunter":     return new Hunter     (new Texture("hunter.png"));
                case "Jaxon":      return new Jaxon      (new Texture("jaxon.png"));
                case "Lark":       return new Lark       (new Texture("lark.png"));
                case "Luke":       return new Luke       (new Texture("luke.png"));
                case "Mason":      return new Mason      (new Texture("mason.png"));
                case "Maxx":       return new Maxx       (new Texture("maxx.png"));
                case "Nathan":     return new Nathan     (new Texture("nathan.png"));
                case "Sean":       return new Sean       (new Texture("sean.png"));
                case "Snowguard":  return new Snowguard  (new Texture("snowguard.png"));
                case "Speen":      return new Speen      (new Texture("speen.png"));
                case "Stoneguard": return new Stoneguard (new Texture("stoneguard.png"));
                case "Thomas":     return new Thomas     (new Texture("thomas.png"));
                case "Tyler":      return new Tyler      (new Texture("tyler.png"));
                case "Weirdguard": return new Weirdguard (new Texture("weirdguard.png"));
                default:           return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
