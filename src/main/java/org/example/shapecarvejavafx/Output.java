package org.example.shapecarvejavafx;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Output {
    private final List<Integer> volume;
    private final List<Integer> dims;

    Output(int[] volume, int[] dims) {
        this.volume = Arrays.stream(volume).boxed().toList();
        this.dims = Arrays.stream(dims).boxed().toList();
    }

    public List<Integer> volume() {
        return volume;
    }

    public List<Integer> dims() {
        return dims;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Output) obj;
        return this.volume.equals(that.volume) &&
                this.dims.equals(that.dims);
    }

    @Override
    public int hashCode() {
        return Objects.hash(volume.hashCode(), dims.hashCode());
    }

    @Override
    public String toString() {
        return "output[" +
                "volume=" + volume.toString() + ", " +
                "dims=" + dims.toString() + ']';
    }
}
