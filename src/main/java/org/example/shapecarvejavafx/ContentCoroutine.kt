package org.example.shapecarvejavafx

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box

class ContentCoroutine(o: Output, private val group: Group) {
    private val dims: IntArray = o.dims
    private val volume: IntArray = o.volume
    private val pos = IntArray(3) // x, y, z initialized to 0,0,0
    var isComplete: Boolean = false
        private set

    // Method to perform one slice of work and yield control
    fun next() {
        if (isComplete) return

        val z = pos[2]
        for (y in 0 until dims[1]) {
            for (x in 0 until dims[0]) {
                val index = x + dims[0] * (y + dims[1] * z)
                val color = volume[index]
                val box = group.children[index] as Box
                if (color != 0) {
                    box.material = PhongMaterial(
                        Color.rgb(
                            (color shr 16) and 0xFF,
                            color shr 8 and 0xFF,
                            color and 0xFF
                        )
                    )
                } else {
                    box.isVisible = false
                }
            }
        }

        // Move to the next z-slice
        pos[2]++
        if (pos[2] >= dims[2]) {
            isComplete = true // Mark completion if the outer loop is done
        }
    }
}
