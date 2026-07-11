package org.example.shapecarvejavafx;

import java.util.*;

public class ShapeCarver {
    public static void main(String[] args) {
        ShapeCarver c = new ShapeCarver();
        var res = c.carve(List.of(
List.of(0,0,0,0,0,1,2,0,0,3,4,0,0,0,0,0),
List.of(0,0,0,0,0,5,6,0,0,7,8,0,0,0,0,0),
List.of(0,0,0,0,0,0,0,0),
List.of(0,0,0,0,0,0,0,0),
List.of(0,0,0,0,0,0,0,0),
List.of(0,0,0,0,0,0,0,0)
                    ), 0, new boolean[]{
            false,
            false,
            false,
            false,
            false,
            false
        });
        System.out.println(res);
    }

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
    List<List<Integer>> depths = new ArrayList<>();
    int[] cursor = new int[3]; // (z, y, x)
    int[] dims = new int[]{2, 4, 4}; /* cuboid shape */
    int[] volume = new int[dims[0] * dims[1] * dims[2]];

    // note that JavaFX uses a y-down coordinate system, so the views are left, right, top, bottom, front, back
    public Output carve(List<List<Integer>> views /* 2d images {x,y,z}-{front,back} */, final int maskColor, boolean[] skip /* views to skip, must have length 6 */) {
        Objects.requireNonNull(views);
        Objects.requireNonNull(skip);

        //Initialize volume. This is necessary.
        Arrays.fill(volume, -1);

        //Initialize depth fields
        for (var d = 0 /* axis */; d < 3; ++d) {
            var u = (d + 1) % 3; // other axis 0
            var v = (d + 2) % 3; // other axis 1
            for (var s = 0; s <= dims[d] - 1; s += dims[d] - 1) {
                var vals = new int[dims[u] * dims[v]];
                var view = views.get(depths.size());
                var sOp = (s == 0) ? dims[d] - 1 : 0;
                for (var i = 0; i < vals.length; ++i) {
                    var shouldSkip = skip[depths.size()];
                    var pixel = view.get(i);
                    vals[i] = (!shouldSkip && pixel == maskColor) ? sOp : s;
                }
                // add vals as a mutable ArrayList to depth
                var valsList = new ArrayList<Integer>();
                for (var val : vals) {
                    valsList.add(val);
                }
                depths.add(valsList);

            }

            //Clear out volume
            for (cursor[v] = 0; cursor[v] < dims[v]; ++cursor[v]) {
                for (cursor[u] = 0; cursor[u] < dims[u]; ++cursor[u]) {
                    for (cursor[d] = depths.get(2 * d + 1).get(cursor[u] + dims[u] * cursor[v]); cursor[d] <= depths.get(2 * d).get(cursor[u] + dims[u] * cursor[v]); ++cursor[d]) {
                        volume[cursor[0] + dims[0] * (cursor[1] + dims[1] * cursor[2])] = maskColor;
                    }
                }
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

                    var view = views.get(vNum);
                    var depth = depths.get(vNum);

                    for (cursor[v] = 0; cursor[v] < dims[v]; ++cursor[v])
                        for (cursor[u] = 0; cursor[u] < dims[u]; ++cursor[u]) {

                            //March along ray
                            var bufIdx = cursor[u] + cursor[v] * dims[u];
                            for (cursor[d] = depth.get(bufIdx); 0 <= cursor[d] && cursor[d] < dims[d]; cursor[d] += s) {

                                //Read volume color
                                var volIdx = cursor[0] + dims[0] * (cursor[1] + dims[1] * cursor[2]);
                                var color = volume[volIdx];
                                if (color == maskColor) {
                                    continue;
                                }

                                color = volume[volIdx] = view.get(cursor[u] + dims[u] * cursor[v]);

                                //Check photo-consistency of volume at cursor
                                var consistent = true;
                                for (var a = 0; consistent && a < 3; ++a) {
                                    var b = (a + 1) % 3;
                                    var c = (a + 2) % 3;
                                    var idx = cursor[b] + dims[b] * cursor[c];
                                    for (var t = 0; t < 2; ++t) {
                                        var fnum = 2 * a + t;
                                        if (skip[fnum]) {
                                            continue;
                                        }
                                        var fcolor = views.get(fnum).get(idx);
                                        var fdepth = depths.get(fnum).get(idx);
                                        if (t != 0 ? fdepth <= cursor[a] : cursor[a] <= fdepth) {
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
                            depth.set(bufIdx, cursor[d]);
                        }
                }
            }
        }

        return new Output(volume, dims);

    }
}

