package com.mygame.tactics;

import com.badlogic.gdx.graphics.Color;

public class Haven {
    private int x, y;
    private Color color;

    public Haven(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.color = new Color(1f, 0.84f, 0f, 1f); // Gold Color
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Color getColor() { return color; }

    // Logic to move the haven later
    public void moveTo(int newX, int newY) {
        if (newX >= 0 && newX < 9 && newY >= 0 && newY < 9) {
            this.x = newX;
            this.y = newY;
        }
    }
}