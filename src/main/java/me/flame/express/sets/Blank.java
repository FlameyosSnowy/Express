package me.flame.express.sets;

public final class Blank {

    public static final Blank BLANK = new Blank();

    public Blank() {}

    public Void toVoid() {
        return null;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Blank;
    }

    public static Void voidness() {
        return null;
    }

}
