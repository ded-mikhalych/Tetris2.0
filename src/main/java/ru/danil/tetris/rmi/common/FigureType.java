package ru.danil.tetris.rmi.common;

import java.io.Serializable;

public enum FigureType implements Serializable {
    I('I', new boolean[][][] {
        {
            {true, true, true, true}
        },
        {
            {true},
            {true},
            {true},
            {true}
        }
    }),
    O('O', new boolean[][][] {
        {
            {true, true},
            {true, true}
        }
    }),
    T('T', new boolean[][][] {
        {
            {true, true, true},
            {false, true, false}
        },
        {
            {true, false},
            {true, true},
            {true, false}
        },
        {
            {false, true, false},
            {true, true, true}
        },
        {
            {false, true},
            {true, true},
            {false, true}
        }
    }),
    L('L', new boolean[][][] {
        {
            {true, true, true},
            {true, false, false}
        },
        {
            {true, true},
            {false, true},
            {false, true}
        },
        {
            {false, false, true},
            {true, true, true}
        },
        {
            {true, false},
            {true, false},
            {true, true}
        }
    }),
    J('J', new boolean[][][] {
        {
            {true, true, true},
            {false, false, true}
        },
        {
            {false, true},
            {false, true},
            {true, true}
        },
        {
            {true, false, false},
            {true, true, true}
        },
        {
            {true, true},
            {true, false},
            {true, false}
        }
    }),
    S('S', new boolean[][][] {
        {
            {false, true, true},
            {true, true, false}
        },
        {
            {true, false},
            {true, true},
            {false, true}
        }
    }),
    Z('Z', new boolean[][][] {
        {
            {true, true, false},
            {false, true, true}
        },
        {
            {false, true},
            {true, true},
            {true, false}
        }
    });

    private final char symbol;
    private final boolean[][][] rotations;

    FigureType(char symbol, boolean[][][] rotations) {
        this.symbol = symbol;
        this.rotations = rotations;
    }

    public char getSymbol() {
        return symbol;
    }

    public boolean[][] getRotation(int rotationIndex) {
        return rotations[Math.floorMod(rotationIndex, rotations.length)];
    }

    public int getRotationCount() {
        return rotations.length;
    }
}
