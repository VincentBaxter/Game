package com.mygame.tactics;

public class DamagePopup {
    public String text;
    public float x, y;
    public float delay = 0; // New field
    public float alpha = 1.0f;
    public float lifetime = 1.5f; 

    public DamagePopup(String text, float x, float y) {
        this.text = text;
        this.x = x + 10; 
        this.y = y + 40;
    }

    public void update(float delta) {
        // If there's a delay, count it down and stop here
        if (delay > 0) {
            delay -= delta;
            return; 
        }

        // Only start the animation after the delay
        lifetime -= delta;
        alpha = Math.max(0, lifetime / 1.5f); 
        y += delta * 40f; 
        x += delta * 5f; 
    }
}