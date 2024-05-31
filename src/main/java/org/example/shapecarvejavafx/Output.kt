package org.example.shapecarvejavafx

import java.util.*

class Output internal constructor(volume: IntArray?, dims: IntArray?) {
    private val volume: List<Int> = Arrays.stream(volume).boxed().toList()
    private val dims: List<Int> = Arrays.stream(dims).boxed().toList()

    fun volume(): List<Int> {
        return volume
    }

    fun dims(): List<Int> {
        return dims
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) return true
        if (obj == null || obj.javaClass != this.javaClass) return false
        val that = obj as Output
        return this.volume == that.volume && (this.dims == that.dims)
    }

    override fun hashCode(): Int {
        return Objects.hash(volume.hashCode(), dims.hashCode())
    }

    override fun toString(): String {
        return "output[" +
                "volume=" + volume.toString() + ", " +
                "dims=" + dims.toString() + ']'
    }
}
