package com.mygame.tactics;

public class Item {

    public enum ItemSlot { HELMET, BODY, SHOES, WEAPON, SPECIAL, MISC }

    public String   name        = "";
    public String   description = "";
    public ItemSlot slot        = ItemSlot.MISC;

    public int    atkMod, magMod, armorMod, cloakMod, speedMod, moveDistMod, rangeMod;
    public double critMod, dodgeMod;
    public String iconName = "";

    public Item() {}

    public Item(String name, String description, ItemSlot slot) {
        this.name        = name;
        this.description = description != null ? description : "";
        this.slot        = slot != null ? slot : ItemSlot.MISC;
    }

    public String serialize() {
        return name + "|" + description + "|" + slot.name() + "|"
             + atkMod + "|" + magMod + "|" + armorMod + "|" + cloakMod + "|"
             + speedMod + "|" + moveDistMod + "|" + rangeMod + "|"
             + critMod + "|" + dodgeMod + "|" + iconName;
    }

    public static Item deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split("\\|", -1);
        if (p.length < 13) return null;
        Item item        = new Item();
        item.name        = p[0];
        item.description = p[1];
        item.slot        = parseSlot(p[2]);
        item.atkMod      = parseInt(p[3]);
        item.magMod      = parseInt(p[4]);
        item.armorMod    = parseInt(p[5]);
        item.cloakMod    = parseInt(p[6]);
        item.speedMod    = parseInt(p[7]);
        item.moveDistMod = parseInt(p[8]);
        item.rangeMod    = parseInt(p[9]);
        item.critMod     = parseDouble(p[10]);
        item.dodgeMod    = parseDouble(p[11]);
        item.iconName    = p[12];
        return item;
    }

    private static ItemSlot parseSlot(String s) {
        try { return ItemSlot.valueOf(s); } catch (Exception e) { return ItemSlot.MISC; }
    }
    private static int    parseInt(String s)    { try { return Integer.parseInt(s.trim()); }    catch (Exception e) { return 0;   } }
    private static double parseDouble(String s) { try { return Double.parseDouble(s.trim()); }  catch (Exception e) { return 0.0; } }
}
