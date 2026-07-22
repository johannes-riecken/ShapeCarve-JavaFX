package org.example.shapecarvejavafx;

import java.io.*;
import java.util.*;
import javax.script.*;
import org.openjdk.engine.python.*;
import org.openjdk.engine.python.AbstractPythonScriptEngine;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

public class ShapeCarver {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ShapeCarver <test_case_index>");
            System.exit(1);
        }

        var m = new ScriptEngineManager();
        var e = (PythonScriptEngine) m.getEngineByName("python");
        e.setExecMode(PyExecMode.SINGLE);
        e.eval("import numpy as np");

        var testCaseIndex = Integer.parseInt(args[0]);
        var c = new ShapeCarver();
        var process = new ProcessBuilder("python3", "test_case_to_java_obj.py", args[0]).start();
        try (InputStream stdout = process.getInputStream();
             InputStream stderr = process.getErrorStream()) {

            // We transfer the bytes of the diff directly to System.out
            stdout.transferTo(System.out);
            // In case of execution issues, transfer error streams to System.err
            stderr.transferTo(System.err);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
        var s = new ObjectInputStream(new FileInputStream("roundtrip.ser"));
        var views = (int[][][])s.readObject();
        var viewsAsList = new ArrayList<List<Integer>>();
        for (int[][] view : views) {
            var viewAsList = new ArrayList<Integer>();
            for (int[] row : view) {
                for (int color : row) {
                    viewAsList.add(color);
                }
            }
            viewsAsList.add(viewAsList);
        }
        // assuming views:
        // 0 (z, y)
        // 1 (x, z)
        // 2 (y, x)
        c.dims = new int[]{views[2].length, views[0][0].length, views[0].length};
        c.volume = new int[c.dims[0] * c.dims[1] * c.dims[2]];

        var maskColor = 0;
        var res = c.carve(viewsAsList, maskColor, new boolean[]{
                false,
                false,
                false,
                false,
                false,
                false
        });
        var printWriter = new PrintWriter("volume.txt");
        printWriter.println(res.toZyxString());
        printWriter.close();

        try {
                // Define the processes to run in the pipeline
                List<ProcessBuilder> builders = Arrays.asList(
                    new ProcessBuilder("jq", "-c", ".[" + testCaseIndex + "].want", "test_cases.json")
                    , new ProcessBuilder("gsed", "s/-1\\>/16711935/g")
                    , new ProcessBuilder("git", "diff", "volume.txt", "/dev/stdin")
                );

                // startPipeline hooks the output of 'jq' to the input of 'git' at the OS level
                List<Process> processes = ProcessBuilder.startPipeline(builders);

                Process jqProcess = processes.get(0);
                Process gsedProcess = processes.get(1);
                Process gitProcess = processes.get(2);

                // Read the output of the final process (git diff) and write it to our stdout
                try (InputStream stdout = gitProcess.getInputStream();
                     InputStream stderr = gitProcess.getErrorStream()) {

                    // We transfer the bytes of the diff directly to System.out
                    stdout.transferTo(System.out);
                    // In case of execution issues, transfer error streams to System.err
                    stderr.transferTo(System.err);

                    // Also print errors from jq if any occurred
                    try (InputStream jqStderr = jqProcess.getErrorStream()) {
                        jqStderr.transferTo(System.err);
                    }

                    try (InputStream gsedStderr = gsedProcess.getErrorStream()) {
                        gsedStderr.transferTo(System.err);
                    }
                }

                int jqExitCode = jqProcess.waitFor();
                int gsedExitCode = gsedProcess.waitFor();
                int gitExitCode = gitProcess.waitFor();

                if (jqExitCode != 0) {
                    System.err.printf("jq process failed with exit code: %d%n", jqExitCode);
                    System.exit(jqExitCode);
                }

                if (gsedExitCode != 0) {
                    System.err.printf("gsed process failed with exit code: %d%n", gsedExitCode);
                    System.exit(gsedExitCode);
                }

                if (gitExitCode != 0) {
                    System.exit(1);
                }

            } catch (IOException ex) {
                System.err.println("Pipeline execution error (missing binary or invalid file): " + ex.getMessage());
                System.exit(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.err.println("Execution was interrupted.");
                System.exit(1);
            }
    }

    public static final class Output {
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
            var res = new StringBuilder();
            res.append("[");
            for (var z = 0; z < this.dims.get(2); z++) {
                res.append("[");
                for (var y = 0; y < this.dims.get(1); y++) {
                    res.append("[");
                    for (var x = 0; x < this.dims.get(0); x++) {
                        res.append(this.volume.get(x + dims.get(0) * y + dims.get(0) * dims.get(1) * z));
                        // res.append(this.volume.get(z + dims.get(2) * y + dims.get(2) * dims.get(1) * x));
                        // if (z < this.dims.get(2) -1 || y < this.dims.get(1) - 1 || x < this.dims.get(0) - 1) {
                        if (x < this.dims.get(0) - 1) {
                            res.append(",");
                        }
                    }
                    res.append("]");
                    if (y < this.dims.get(1) - 1) {
                        res.append(",");
                    }
                }
                res.append("]");
                if (z < this.dims.get(2) - 1) {
                    res.append(",");
                }
            }
            res.append("]");
            return res.toString();
        }

        public String toZyxString() {
            var res = this.toString();
            return res.replace(" ", "");
            // return res.replace(" ", "").replace("16711935", "-1");
            // var res = new StringBuilder("[");
            // for (var z = 0; z < this.dims.get(2); z++) {
            //     for (var y = 0; y < this.dims.get(1); y++) {
            //         for (var x = 0; x < this.dims.get(0); x++) {
            //             res.append(this.volume.get(z + dims.get(2) * y + dims.get(2) * dims.get(1) * x));
            //             if (z < this.dims.get(2) -1 || y < this.dims.get(1) - 1 || x < this.dims.get(0) - 1) {
            //                 res.append(",");
            //             }
            //         }
            //     }
            // }
            // res.append("]");
            // return res.toString();
        }

    }

    List<List<Integer>> depths = new ArrayList<>();
    int[] cursor = new int[3]; // (z, y, x)
    int[] dims; /* cuboid shape */
    int[] volume;

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
                var depthsForView = new int[dims[u] * dims[v]];
                var view = views.get(depths.size());
                var sOp = (s == 0) ? dims[d] - 1 : 0;
                for (var i = 0; i < depthsForView.length; ++i) {
                    var shouldSkip = skip[depths.size()];
                    var pixel = view.get(i);
                    depthsForView[i] = (!shouldSkip && pixel == maskColor) ? sOp : s;
                }
                // add depthsForView as a mutable ArrayList to depth
                var valsList = new ArrayList<Integer>();
                for (var val : depthsForView) {
                    valsList.add(val);
                }
                depths.add(valsList);

            }

            //Clear out volume where ray goes through entirely
            for (cursor[v] = 0; cursor[v] < dims[v]; ++cursor[v]) {
                for (cursor[u] = 0; cursor[u] < dims[u]; ++cursor[u]) {
                    for (cursor[d] = depths.get(2 * d + 1).get(cursor[u] + dims[u] * cursor[v]); cursor[d] <= depths.get(2 * d).get(cursor[u] + dims[u] * cursor[v]); ++cursor[d]) {
                        volume[cursor[0] + dims[0] * (cursor[1] + dims[1] * cursor[2])] = maskColor;
                    }
                }
            }
        }

        //Perform iterative shape carving until convergence
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

        //Do a final pass to fill in any missing colors
        var n = 0; // linear index. See loop invariant below
        for (cursor[2] = 0; cursor[2] < dims[2]; ++cursor[2])
            for (cursor[1] = 0; cursor[1] < dims[1]; ++cursor[1])
                for (cursor[0] = 0; cursor[0] < dims[0]; ++cursor[0], ++n) {
                    assert n == cursor[0] + dims[0] * (cursor[1] + dims[1] * cursor[2]);
                    if (volume[n] < 0) {
                        volume[n] = 0xff00ff;
                    }
                }

        return new Output(volume, dims);

    }
}

