package org.example.shapecarvejavafx

import javafx.beans.property.SimpleIntegerProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import javax.imageio.ImageIO


class ShapeCarver(private var output: Output) {
    private var depths: MutableList<MutableList<Int>> = mutableListOf()
    private var x: IntArray = IntArray(3) // cursor

    val channel = Channel<Unit>()

    suspend fun carve(
        views: List<List<Int>>,  // 2d images
        maskColor: Int, skip: BooleanArray // views to skip
    ) {
        val volume = output.volume
        val dims = output.dims
        require(skip.isNotEmpty())

        // Initialize depth fields
        for (d in 0..2) {
            val u = (d + 1) % 3
            val v = (d + 2) % 3
            var s = 0
            while (s <= dims[d] - 1) {
                val vals = IntArray(dims[u] * dims[v])
                val view = views[depths.size]
                val sOp = if (s == 0) dims[d] - 1 else 0
                vals.forEachIndexed { i, _ ->
                    vals[i] = if (!skip[depths.size] && view[i] == maskColor) sOp else s
                }
                depths.add(vals.toMutableList())
                s += dims[d] - 1
            }

            // Clear out volume
            x[v] = 0
            while (x[v] < dims[v]) {
                x[u] = 0
                while (x[u] < dims[u]) {
                    x[d] = depths[2 * d + 1][x[u] + x[v] * dims[u]]
                    while (x[d] <= depths[2 * d][x[u] + x[v] * dims[u]]) {
                        volume[x[0] + dims[0] * (x[1] + dims[1] * x[2])].set(maskColor)
                        ++x[d]
                    }
                    ++x[u]
                }
                ++x[v]
            }
            channel.send(Unit)
        }

        // Perform iterative seam carving until convergence
        var removed = 1
        while (removed > 0) {
            removed = 0
            for (d in 0..2) {
                val u = (d + 1) % 3
                val v = (d + 2) % 3

                // Do front/back sweep
                for (s in listOf(-1, 1)) {
                    val vNum = 2 * d + if (s < 0) 1 else 0
                    if (skip[vNum]) {
                        continue
                    }

                    val view = views[vNum]
                    val depth = depths[vNum]

                    x[v] = 0
                    while (x[v] < dims[v]) {
                        x[u] = 0
                        while (x[u] < dims[u]) {
                            // March along ray
                            val bufIdx = x[u] + x[v] * dims[u]
                            x[d] = depth[bufIdx]
                            while (x[d] in 0 until dims[d]) {
                                // Read volume color
                                val volIdx = x[0] + dims[0] * (x[1] + dims[1] * x[2])
                                var color = volume[volIdx]
                                if (color.get() == maskColor) {
                                    x[d] += s
                                    continue
                                }

                                volume[volIdx].set(view[x[u] + dims[u] * x[v]])
                                color = volume[volIdx]

                                // Check photo-consistency of volume at x
                                var consistent = true
                                for (a in 0..2) {
                                    val b = (a + 1) % 3
                                    val c = (a + 2) % 3
                                    val idx = x[b] + dims[b] * x[c]
                                    for (t in 0..1) {
                                        val fnum = 2 * a + t
                                        if (skip[fnum]) continue
                                        val fcolor = views[fnum][idx]
                                        val fdepth = depths[fnum][idx]
                                        if (if (t != 0) fdepth <= x[a] else x[a] <= fdepth) {
                                            if (fcolor != color.get()) {
                                                consistent = false
                                                break
                                            }
                                        }
                                    }
                                    if (!consistent) break
                                }
                                if (consistent) break

                                // Clear out voxel
                                ++removed
                                volume[volIdx].set(maskColor)
                                x[d] += s
                            }

                            // Update depth value
                            depth[bufIdx] = x[d]
                            ++x[u]
                        }
                        ++x[v]
                    }
                }
                channel.send(Unit)
            }
        }

        // Do a final pass to fill in any missing colors
        var n = 0 // linear index. See loop invariant below
        x[2] = 0
        while (x[2] < dims[2]) {
            x[1] = 0
            while (x[1] < dims[1]) {
                x[0] = 0
                while (x[0] < dims[0]) {
                    if (volume[n].get() < 0) {
                        volume[n].set(0xff00ff)
                    }
                    ++x[0]
                    ++n
                }
                ++x[1]
            }
            ++x[2]
        }

        val projections = output.project()
        output.saveProjectionsToImages(projections)
        channel.close()
    }
}

data class Output(val volume: List<SimpleIntegerProperty>, val dims: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Output

        if (volume != other.volume) return false
        if (!dims.contentEquals(other.dims)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = volume.hashCode()
        result = 31 * result + dims.contentHashCode()
        return result
    }

    // project returns the six axis-oriented orthogonal views of the shape
    fun project(): List<List<List<Int>>> {
        val views = mutableListOf<List<List<Int>>>()
        for (d in 0..2) {
            val u = (d + 1) % 3
            val v = (d + 2) % 3
            val view = mutableListOf<List<Int>>()
            for (s in 0 until dims[d]) {
                val vals = mutableListOf<Int>()
                for (vIdx in 0 until dims[v]) {
                    for (uIdx in 0 until dims[u]) {
                        vals.add(volume[uIdx + dims[u] * (vIdx + dims[v] * s)].get())
                    }
                }
                view.add(vals)
            }
            views.add(view)
        }
        return views
    }

    fun saveProjectionsToImages(views: List<List<List<Int>>>) {
        for ((i, view) in views.withIndex()) {
            val img = projectionToImage(view)
            val legacyImg = SwingFXUtils.fromFXImage(img, null)
            ImageIO.write(legacyImg, "png", File("view$i.png"))
        }

    }

    // projectionToImage takes a matrix of integers, where the integers represent 24-bit RGB colors without transparency
    private fun projectionToImage(view: List<List<Int>>): Image {
        val width = view[0].size
        val height = view.size
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = view[y][x]
                pixels[x + y * width] = color
            }
        }
        val argbPixels = IntArray(width * height)
        for (i in pixels.indices) {
            argbPixels[i] = 0xff shl 24 or pixels[i]
        }

        val pixelData = ByteArray(width * height * 4) // Assuming 4 bytes per pixel (ARGB)


        // Fill the pixelData with data from argbPixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = (y * width + x) * 4
                val argb = argbPixels[y * width + x]
                // first comes A
                pixelData[index] = (argb shr 24).toByte()
                // then R
                pixelData[index + 1] = (argb shr 16).toByte()
                // then G
                pixelData[index + 2] = (argb shr 8).toByte()
                // and lastly B
                pixelData[index + 3] = argb.toByte()
            }
        }

        val image = WritableImage(width, height)
        val pixelWriter = image.pixelWriter
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), pixelData, 0, width * 4)
        return image
    }
}
