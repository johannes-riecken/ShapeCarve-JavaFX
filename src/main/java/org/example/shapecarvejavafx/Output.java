package org.example.shapecarvejavafx;

import java.util.Arrays;
import java.util.Objects;

public final class Output {
    private final int[] volume;
    private final int[] dims;

    Output(int[] volume, int[] dims) {
        this.volume = volume;
        this.dims = dims;
    }

    public int[] volume() {
        return volume;
    }

    public int[] dims() {
        return dims;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Output) obj;
        return Arrays.equals(this.volume, that.volume) &&
                Arrays.equals(this.dims, that.dims);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(volume), Arrays.hashCode(dims));
    }

    @Override
    public String toString() {
        return "output[" +
                "volume=" + Arrays.toString(volume) + ", " +
                "dims=" + Arrays.toString(dims) + ']';
    }
}
