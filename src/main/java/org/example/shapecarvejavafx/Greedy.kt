package org.example.shapecarvejavafx

internal fun interface IntTernaryOperator {
    fun applyAsInt(a: Int, b: Int, c: Int): Int
}

object Greedy {
    fun greedyMesh(volume: IntArray, dims: IntArray): Mesh {
        //Cache buffer internally
        var mask = IntArray((4096))
        val f = IntTernaryOperator { i: Int, j: Int, k: Int -> volume[i + dims[0] * (j + dims[1] * k)] }

        //Sweep over 3-axes
        val vertices: MutableList<List<Int>> = ArrayList()
        val faces: MutableList<List<Int>> = ArrayList()
        for (d in 0..2) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            var w: Int
            var h: Int
            val u = (d + 1) % 3
            val v = (d + 2) % 3
            val x = intArrayOf(0, 0, 0)
            val q = intArrayOf(0, 0, 0)
            if (mask.size < dims[u] * dims[v]) {
                mask = IntArray((dims[u] * dims[v]))
            }
            q[d] = 1
            x[d] = -1
            while (x[d] < dims[d]) {
                //Compute mask
                var n = 0
                x[v] = 0
                while (x[v] < dims[v]) {
                    x[u] = 0
                    while (x[u] < dims[u]) {
                        val a = (if (0 <= x[d]) f.applyAsInt(x[0], x[1], x[2]) else 0)
                        val b = (if (x[d] < dims[d] - 1) f.applyAsInt(x[0] + q[0], x[1] + q[1], x[2] + q[2]) else 0)
                        if ((a != 0) == (b != 0)) {
                            mask[n] = 0
                        } else if (a != 0) {
                            mask[n] = a
                        } else {
                            mask[n] = -b
                        }
                        ++x[u]
                        ++n
                    }
                    ++x[v]
                }
                //Increment x[d]
                ++x[d]
                //Generate mesh for mask using lexicographic ordering
                n = 0
                j = 0
                while (j < dims[v]) {
                    i = 0
                    while (i < dims[u]) {
                        var c = mask[n]
                        if (c != 0) {
                            //Compute width
                            w = 1
                            while (c == mask[n + w] && i + w < dims[u]) {
                                ++w
                            }
                            //Compute height (this is slightly awkward)
                            var done = false
                            h = 1
                            while (j + h < dims[v]) {
                                k = 0
                                while (k < w) {
                                    if (c != mask[n + k + h * dims[u]]) {
                                        done = true
                                        break
                                    }
                                    ++k
                                }
                                if (done) {
                                    break
                                }
                                ++h
                            }
                            //Add quad
                            x[u] = i
                            x[v] = j
                            val du = intArrayOf(0, 0, 0)
                            val dv = intArrayOf(0, 0, 0)
                            if (c > 0) {
                                dv[v] = h
                                du[u] = w
                            } else {
                                c = -c
                                du[v] = h
                                dv[u] = w
                            }
                            val vertexCount = vertices.size
                            vertices.add(java.util.List.of(x[0], x[1], x[2]))
                            vertices.add(java.util.List.of(x[0] + du[0], x[1] + du[1], x[2] + du[2]))
                            vertices.add(
                                java.util.List.of(
                                    x[0] + du[0] + dv[0],
                                    x[1] + du[1] + dv[1],
                                    x[2] + du[2] + dv[2]
                                )
                            )
                            vertices.add(java.util.List.of(x[0] + dv[0], x[1] + dv[1], x[2] + dv[2]))
                            faces.add(
                                java.util.List.of(
                                    vertexCount,
                                    vertexCount + 1,
                                    vertexCount + 2,
                                    vertexCount + 3,
                                    c
                                )
                            )

                            //Zero-out mask
                            l = 0
                            while (l < h) {
                                k = 0
                                while (k < w) {
                                    mask[n + k + l * dims[u]] = 0
                                    ++k
                                }
                                ++l
                            }
                            //Increment counters and continue
                            i += w
                            n += w
                        } else {
                            ++i
                            ++n
                        }
                    }
                    ++j
                }
            }
        }
        return Mesh(vertices, faces)
    }

    data class Mesh(val vertices: List<List<Int>>, val faces: List<List<Int>>)
}
