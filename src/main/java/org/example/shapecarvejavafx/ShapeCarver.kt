package org.example.shapecarvejavafx

class ShapeCarver {
    private var depths: MutableList<MutableList<Int>> = mutableListOf()
    private var x: IntArray = IntArray(3) // cursor
    private var dims: IntArray = intArrayOf(16, 16, 16) // cuboid shape
    private var volume: IntArray = IntArray(dims[0] * dims[1] * dims[2])

    fun carve(
        views: List<List<Int>>,  // 2d images
        maskColor: Int, skip: BooleanArray // views to skip
    ): Output {
        require(views.isNotEmpty())
        require(skip.isNotEmpty())

        // Initialize volume
        volume.fill(-1)

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
                        volume[x[0] + dims[0] * (x[1] + dims[1] * x[2])] = maskColor
                        ++x[d]
                    }
                    ++x[u]
                }
                ++x[v]
            }
        }

        // Perform iterative seam carving until convergence
        var removed = 1
        while (removed > 0) {
            removed = 0
            for (d in 0..2) {
                val u = (d + 1) % 3
                val v = (d + 2) % 3

                // Do front/back sweep
                var s = -1
                while (s <= 1) {
                    val vNum = 2 * d + if (s < 0) 1 else 0
                    if (skip[vNum]) {
                        s += 2
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
                                if (color == maskColor) {
                                    x[d] += s
                                    continue
                                }

                                volume[volIdx] = view[x[u] + dims[u] * x[v]]
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
                                            if (fcolor != color) {
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
                                volume[volIdx] = maskColor
                                x[d] += s
                            }

                            // Update depth value
                            depth[bufIdx] = x[d]
                            ++x[u]
                        }
                        ++x[v]
                    }
                    s += 2
                }
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
                    if (volume[n] < 0) {
                        volume[n] = 0xff00ff
                    }
                    ++x[0]
                    ++n
                }
                ++x[1]
            }
            ++x[2]
        }

        return Output(volume, dims)
    }
}

data class Output(val volume: IntArray, val dims: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Output

        if (!volume.contentEquals(other.volume)) return false
        if (!dims.contentEquals(other.dims)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = volume.contentHashCode()
        result = 31 * result + dims.contentHashCode()
        return result
    }
}
