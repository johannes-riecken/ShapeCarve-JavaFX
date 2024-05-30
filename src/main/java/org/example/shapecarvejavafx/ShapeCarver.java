package org.example.shapecarvejavafx;

import java.util.*;

public class ShapeCarver {
    List<List<Integer>> depths = new ArrayList<>();
    int[] x = new int[3]; // cursor
    int[] dims = new int[]{16, 16, 16};
    int[] volume = new int[dims[0] * dims[1] * dims[2]];

    public Output carve(int[] dims /* cuboid shape */, int[][] views /* 2d images */, final int maskColor, boolean[] skip /* views to skip */) {

        //Initialize volume. This is necessary.
        Arrays.fill(volume, -1);

        //Initialize depth fields
        for (var d = 0 /* axis */; d < 3; ++d) {
            var u = (d + 1) % 3; // other axis 0
            var v = (d + 2) % 3; // other axis 1
            for (var s = 0; s <= dims[d] - 1; s += dims[d] - 1) {
                var vals = new int[dims[u] * dims[v]];
                var view = views[depths.size()];
                var sOp = (s == 0) ? dims[d] - 1 : 0;
                for (var i = 0; i < vals.length; ++i) {
                    vals[i] = (!skip[depths.size()] && view[i] == maskColor) ? sOp : s;
                }
                // add vals as a mutable ArrayList to depth
                var valsList = new ArrayList<Integer>();
                for (var val : vals) {
                    valsList.add(val);
                }
                depths.add(valsList);

            }

            //Clear out volume
            for (x[v] = 0; x[v] < dims[v]; ++x[v])
                for (x[u] = 0; x[u] < dims[u]; ++x[u])
                    for (x[d] = depths.get(2 * d + 1).get(x[u] + x[v] * dims[u]); x[d] <= depths.get(2 * d).get(x[u] + x[v] * dims[u]); ++x[d]) {
                        volume[x[0] + dims[0] * (x[1] + dims[1] * x[2])] = maskColor;
                    }
        }

        //Perform iterative seam carving until convergence
        var removed = 1;
        while (removed > 0) {
            removed = 0;
            for (var d = 0; d < 3; ++d) {
                var u = (d + 1) % 3;
                var v = (d + 2) % 3;

                //Do front/back sweep
                for (var s = -1; s <= 1; s += 2) {
                    var vNum = 2 * d + ((s < 0) ? 1 : 0);
                    if (skip[vNum]) {
                        continue;
                    }

                    var view = views[vNum];
                    var depth = depths.get(vNum);

                    for (x[v] = 0; x[v] < dims[v]; ++x[v])
                        for (x[u] = 0; x[u] < dims[u]; ++x[u]) {

                            //March along ray
                            var bufIdx = x[u] + x[v] * dims[u];
                            for (x[d] = depth.get(bufIdx); 0 <= x[d] && x[d] < dims[d]; x[d] += s) {

                                //Read volume color
                                var volIdx = x[0] + dims[0] * (x[1] + dims[1] * x[2]);
                                var color = volume[volIdx];
                                if (color == maskColor) {
                                    continue;
                                }

                                color = volume[volIdx] = view[x[u] + dims[u] * x[v]];

                                //Check photo-consistency of volume at x
                                var consistent = true;
                                for (var a = 0; consistent && a < 3; ++a) {
                                    var b = (a + 1) % 3;
                                    var c = (a + 2) % 3;
                                    var idx = x[b] + dims[b] * x[c];
                                    for (var t = 0; t < 2; ++t) {
                                        var fnum = 2 * a + t;
                                        if (skip[fnum]) {
                                            continue;
                                        }
                                        var fcolor = views[fnum][idx];
                                        var fdepth = depths.get(fnum).get(idx);
                                        if (t != 0 ? fdepth <= x[a] : x[a] <= fdepth) {
                                            if (fcolor != color) {
                                                consistent = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (consistent) {
                                    break;
                                }

                                //Clear out voxel
                                ++removed;
                                volume[volIdx] = maskColor;
                            }

                            //Update depth value
                            depth.set(bufIdx, x[d]);
                        }
                }
            }
        }

        //Do a final pass to fill in any missing colors
        var n = 0; // linear index. See loop invariant below
        for (x[2] = 0; x[2] < dims[2]; ++x[2])
            for (x[1] = 0; x[1] < dims[1]; ++x[1])
                for (x[0] = 0; x[0] < dims[0]; ++x[0], ++n) {
                    assert n == x[0] + dims[0] * (x[1] + dims[1] * x[2]);
                    if (volume[n] < 0) {
                        volume[n] = 0xff00ff;
                    }
                }

        return new Output(volume, dims);

    }
}

