package com.henri.nonogram.ui;

public enum InputMode {
    FILL,
    CROSS;

    public InputMode opposite() {
        return this == FILL ? CROSS : FILL;
    }
}