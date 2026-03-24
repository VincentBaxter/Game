package com.mygame.tactics;

public class DamageResult {
    public int phys;
    public int mag;
    public int trueDmg;

    public DamageResult(int p, int m, int t) {
        this.phys = p;
        this.mag = m;
        this.trueDmg = t;
    }
}