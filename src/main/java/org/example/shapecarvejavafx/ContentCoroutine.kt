package org.example.shapecarvejavafx

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class ContentCoroutine(output: Output, private val group: Group) {
    private val dims: IntArray = output.dims
    private val volume: IntArray = output.volume
    private val channel = Channel<Unit>()
    var isComplete: Boolean = false
        private set

    init {
        CoroutineScope(Dispatchers.Default).launch {
            processSlices()
        }
    }

    private suspend fun processSlices() {
        for (z in 0 until dims[2]) {
            for (y in 0 until dims[1]) {
                for (x in 0 until dims[0]) {
                    val index = x + dims[0] * (y + dims[1] * z)
                    val color = volume[index]
                    val box = group.children[index] as Box
                    withContext(Dispatchers.Main) {
                        if (color != 0) {
                            box.material = PhongMaterial(
                                Color.rgb(
                                    (color shr 16) and 0xFF,
                                    (color shr 8) and 0xFF,
                                    color and 0xFF
                                )
                            )
                        } else {
                            box.isVisible = false
                        }
                    }
                }
            }
            channel.send(Unit)
        }
        isComplete = true
        channel.close()
    }

    suspend fun next() {
        if (!isComplete) {
            channel.receive()
        }
    }
}
