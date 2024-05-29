package org.example.shapecarvejavafx;

import java.util.*;

public class ShapeCarver {
    List<List<Integer>> depth = new ArrayList<>();
    int[] x = new int[3];
    int[] dims = new int[]{16, 16, 16};
    int[] volume = new int[dims[0] * dims[1] * dims[2]];

    public Output carve(int[] dims, int[][] views, final int mask_color, boolean[] skip) {

        //Initialize volume
        Arrays.fill(volume, -1);

        //Initialize depth fields
        for (var d = 0; d < 3; ++d) {
            var u = (d + 1) % 3;
            var v = (d + 2) % 3;
            for (var s = 0; s <= dims[d] - 1; s += dims[d] - 1) {
                var vals = new int[dims[u] * dims[v]];
                var view = views[depth.size()];
                var s_op = (s == 0) ? dims[d] - 1 : 0;
                for (var i = 0; i < vals.length; ++i) {
                    vals[i] = (!skip[depth.size()] && view[i] == mask_color) ? s_op : s;
                }
                // add vals as a mutable ArrayList to depth
                var valsList = new ArrayList<Integer>();
                for (var val : vals) {
                    valsList.add(val);
                }
                depth.add(valsList);

            }

            //Clear out volume
            for (x[v] = 0; x[v] < dims[v]; ++x[v])
                for (x[u] = 0; x[u] < dims[u]; ++x[u])
                    for (x[d] = depth.get(2 * d + 1).get(x[u] + x[v] * dims[u]); x[d] <= depth.get(2 * d).get(x[u] + x[v] * dims[u]); ++x[d]) {
                        volume[x[0] + dims[0] * (x[1] + dims[1] * x[2])] = mask_color;
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
                    var v_num = 2 * d + ((s < 0) ? 1 : 0);
                    if (skip[v_num]) {
                        continue;
                    }

                    var aview = views[v_num];
                    var adepth = depth.get(v_num);

                    for (x[v] = 0; x[v] < dims[v]; ++x[v])
                        for (x[u] = 0; x[u] < dims[u]; ++x[u]) {

                            //March along ray
                            var buf_idx = x[u] + x[v] * dims[u];
                            for (x[d] = adepth.get(buf_idx); 0 <= x[d] && x[d] < dims[d]; x[d] += s) {

                                //Read volume color
                                var vol_idx = x[0] + dims[0] * (x[1] + dims[1] * x[2]);
                                var color = volume[vol_idx];
                                if (color == mask_color) {
                                    continue;
                                }

                                color = volume[vol_idx] = aview[x[u] + dims[u] * x[v]];

                                //Check photoconsistency of volume at x
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
                                        var fdepth = depth.get(fnum).get(idx);
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
                                volume[vol_idx] = mask_color;
                            }

                            //Update depth value
                            adepth.set(buf_idx, x[d]);
                        }
                }
            }
        }

        //Do a final pass to fill in any missing colors
        var n = 0;
        for (x[2] = 0; x[2] < dims[2]; ++x[2])
            for (x[1] = 0; x[1] < dims[1]; ++x[1])
                for (x[0] = 0; x[0] < dims[0]; ++x[0], ++n) {
                    if (volume[n] < 0) {
                        volume[n] = 0xff00ff;
                    }
                }

        return new Output(volume, dims);

    }
}

