package org.example.shapecarvejavafx;

import java.io.*;
import java.util.*;
import javax.script.*;

import org.openjdk.engine.python.*;
import org.openjdk.engine.python.AbstractPythonScriptEngine;
import org.openjdk.engine.python.AbstractPythonScriptEngine.PyExecMode;

public class ShapeCarver {
    public static String toZyxString(Object o) {
        var res = o.toString();
        return res.replace(" ", "");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ShapeCarver <test_case_index>");
            System.exit(1);
        }

        var m = new ScriptEngineManager();
        var e = (PythonScriptEngine) m.getEngineByName("python");
        e.setExecMode(PyExecMode.SINGLE);
        e.eval("import numpy as np");
        e.setExecMode(PyExecMode.EVAL);

        var testCaseIndex = Integer.parseInt(args[0]);
        var c = new ShapeCarver();
        var process = new ProcessBuilder("./venv/bin/python3", "test_case_to_java_obj.py", args[0]).start();
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
        var views = (int[][][]) s.readObject();
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
        var intDType = (PyObject) e.eval("int");
        var npEmpty = (PyObject) e.eval("np.empty");
        c.volume = (PyObject) npEmpty.call(e.newPyTuple(e.fromJava(c.dims[0]), e.fromJava(c.dims[1]), e.fromJava(c.dims[2])), intDType);

        var maskColor = 0;
        var npViews = viewsToNumPy(e, viewsAsList);
        e.put("res", c.carve(e, npViews, maskColor, new boolean[]{
                false,
                false,
                false,
                false,
                false,
                false
        }));
        var printWriter = new PrintWriter("volume.txt");
        printWriter.println(toZyxString(e.eval("res.tolist()")));
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

    int[][] depths = new int[6][];
    int[] cursor = new int[3]; // (z, y, x)
    int[] dims; /* cuboid shape */
    PyObject volume;

    // note that JavaFX uses a y-down coordinate system, so the views are left, right, top, bottom, front, back
    public PyObject carve(PythonScriptEngine e, PyObject views /* 2d images {x,y,z}-{front,back} */, final int maskColor, boolean[] skip /* views to skip, must have length 6 */) throws ScriptException, NoSuchMethodException {
        Objects.requireNonNull(views);
        Objects.requireNonNull(skip);


        //Initialize volume. This is necessary.
        e.invokeMethod(volume, "fill", -1);

        //Initialize depth fields
        for (var d = 0 /* axis */; d < 3; ++d) {
            var u = (d + 1) % 3; // other axis 0
            var v = (d + 2) % 3; // other axis 1
            for (var s = 0; s <= dims[d] - 1; s += dims[d] - 1) {
                var sIdx = s == 0 ? 0 : 1; // s meaning side
                var depthsIdx = 2 * d + sIdx;
                var depthsForView = new int[dims[u] * dims[v]];
                var view = (PyObject) views.getItem(e.newPyTuple(e.fromJava(d), e.fromJava(sIdx)));
                var sOp = (s == 0) ? dims[d] - 1 : 0;
                for (var uIdx = 0; uIdx < dims[u]; uIdx++) {
                    for (var vIdx = 0; vIdx < dims[v]; vIdx++) {
                        var i = uIdx * dims[v] + vIdx;
                        var shouldSkip = skip[depthsIdx];
                        var pixel = view.getItem(e.newPyTuple(e.fromJava(uIdx), e.fromJava(vIdx))).toLong();
                        depthsForView[i] = (!shouldSkip && pixel == maskColor) ? sOp : s;
                    }
                }
                depths[depthsIdx] = depthsForView;;

            }

            //Clear out volume where ray goes through entirely
            for (cursor[v] = 0; cursor[v] < dims[v]; ++cursor[v]) {
                for (cursor[u] = 0; cursor[u] < dims[u]; ++cursor[u]) {
                    for (cursor[d] = depths[2 * d + 1][cursor[u] + dims[u] * cursor[v]]; cursor[d] <= depths[2 * d][cursor[u] + dims[u] * cursor[v]]; ++cursor[d]) {
                        volume.setItem(e.newPyTuple(e.fromJava(cursor[2]), e.fromJava(cursor[1]), e.fromJava(cursor[0])), e.fromJava(maskColor));
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
                    var sIdx = s < 0 ? 1 : 0;
                    var vNum = 2 * d + ((s < 0) ? 1 : 0);
                    if (skip[vNum]) {
                        continue;
                    }

                    var view = (PyObject) views.getItem(e.newPyTuple(e.fromJava(d), e.fromJava(sIdx)));
                    var depth = depths[vNum];

                    for (cursor[v] = 0; cursor[v] < dims[v]; ++cursor[v])
                        for (cursor[u] = 0; cursor[u] < dims[u]; ++cursor[u]) {

                            //March along ray
                            var bufIdx = cursor[u] + cursor[v] * dims[u];
                            for (cursor[d] = depth[bufIdx]; 0 <= cursor[d] && cursor[d] < dims[d]; cursor[d] += s) {

                                //Read volume color
                                var volIdx = e.newPyTuple(e.fromJava(cursor[2]), e.fromJava(cursor[1]), e.fromJava(cursor[0]));
                                var color = volume.getItem(volIdx).toLong();
                                if (color == maskColor) {
                                    continue;
                                }
                                volume.setItem(volIdx, view.getItem(e.newPyTuple(e.fromJava(cursor[v]), e.fromJava(cursor[u]))));
                                color = volume.getItem(volIdx).toLong();;

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
                                        var fcolor = views.getItem(e.newPyTuple(e.fromJava(a), e.fromJava(t), e.fromJava(cursor[c]), e.fromJava(cursor[b]))).toLong();
                                        var fdepth = depths[fnum][idx];
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
                                volume.setItem(volIdx, e.fromJava(maskColor));
                            }

                            //Update depth value
                            depth[bufIdx] = cursor[d];
                        }
                }
            }
        }

        ////Do a final pass to fill in any missing colors
        volume.setItem((PyObject) e.invokeMethod(volume, "__lt__", 0), e.fromJava(0xff00ff));

        return volume;
    }

    // views has shape like (6, 16 * 16), but for NumPy it gets (3, 2, 16, 16)
    public static PyObject viewsToNumPy(PythonScriptEngine e, List<List<Integer>> views) throws ScriptException, NoSuchMethodException {
        var pyObjArr2D = new PyObject[6];
        for (var i = 0; i < 6; i++) {
            var pyObjArr1D = new PyObject[views.get(0).size()];
            for (var j = 0; j < views.get(0).size(); j++) {
                pyObjArr1D[j] = (PyObject) e.invokeFunction("int", views.get(i).get(j));
            }
            var pyList1D = e.newPyList(pyObjArr1D);
            pyObjArr2D[i] = pyList1D;
        }
        var pyList2D = e.newPyList(pyObjArr2D);
        e.put("views", pyList2D);
        var sideLen = (int) Math.sqrt(views.get(0).size());
        return (PyObject) e.invokeMethod(e.eval("np.array(views)"), "reshape", 3, 2, sideLen, sideLen);
    }
}

