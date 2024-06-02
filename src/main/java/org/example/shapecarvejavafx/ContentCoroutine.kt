package org.example.shapecarvejavafx

import javafx.beans.value.WritableIntegerValue
import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class ContentCoroutine(output: Output, private val group: Group) {
    private val dims: IntArray = output.dims
    private val volume: List<WritableIntegerValue> = output.volume
    val channel = Channel<Unit>()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            processSlices()
        }
    }

    private fun processSlices() {
        for (z in 0 until dims[2]) {
            for (y in 0 until dims[1]) {
                for (x in 0 until dims[0]) {
                    val index = x + dims[0] * (y + dims[1] * z)
                    val color = volume[index]
                    val box = group.children[index] as Box
                    if (color.get() != 0) {
                        box.material = PhongMaterial(
                            Color.rgb(
                                (color.get() shr 16) and 0xFF,
                                (color.get() shr 8) and 0xFF,
                                color.get() and 0xFF
                            )
                        )
                    } else {
                        box.isVisible = false
                    }
                }
            }
        }
    }
}
