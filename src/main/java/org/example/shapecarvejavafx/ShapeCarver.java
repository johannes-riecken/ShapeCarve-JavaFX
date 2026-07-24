package org.example.shapecarvejavafx;

import java.io.*;
import java.util.*;
import javax.script.*;

import org.openjdk.engine.python.*;
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
        c.dims = e.newPyTuple(e.fromJava(views[2].length), e.fromJava(views[0][0].length), e.fromJava(views[0].length));
        var intDType = (PyObject) e.eval("int");
        var npEmpty = (PyObject) e.eval("np.empty");
        c.volume = npEmpty.call(c.dims, intDType);

        var maskColor = e.fromJava(0);
        var npViews = viewsToNumPy(e, viewsAsList);
        var npZeros = (PyObject) e.eval("np.zeros");
        var boolDType = (PyObject) e.eval("bool");
        var skip = npZeros.call(e.newPyTuple(e.fromJava(3), e.fromJava(2)), boolDType);
        e.put("res", c.carve(e, npViews, maskColor, skip));
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

    PyTuple dims; /* cuboid shape */
    PyObject volume;

    // note that JavaFX uses a y-down coordinate system, so the views are left, right, top, bottom, front, back
    public PyObject carve(PythonScriptEngine e, PyObject views /* 2d images {x,y,z}-{front,back} */, final PyObject maskColor, PyObject skip /* views to skip, must have shape (3, 2) */) throws ScriptException, NoSuchMethodException {
        Objects.requireNonNull(views);
        Objects.requireNonNull(skip);


        //Initialize volume. This is necessary.
        e.invokeMethod(volume, "fill", -1);

        var npEmpty = (PyObject) e.eval("np.empty");
        var intDType = (PyObject) e.eval("int");
        var depths = npEmpty.call(views.getAttribute("shape"), intDType);
        var cursor = npEmpty.call(e.fromJava(3), intDType); // (z, y, x)
        //Initialize depth fields
        for (PyObject d = e.fromJava(0) /* axis */;
                ((PyObject) e.invokeMethod(d, "__lt__", e.fromJava(3))).isTrue();
                d = (PyObject) e.invokeMethod(d, "__add__", 1)) {
            var u = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", 1), "__mod__", 3); // other axis 0
            var v = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", 2), "__mod__", 3); // other axis 1
            for (var s = 0; s <= dims.getItem(d).toLong() - 1; s += dims.getItem(d).toLong() - 1) {
                var sIdx = s == 0 ? 0 : 1; // s meaning side
                var idxTuple = e.newPyTuple(d, e.fromJava(sIdx));
                var view = views.getItem(idxTuple);
                var sOp = (s == 0) ? dims.getItem(d).toLong() - 1 : 0;
                // TODO: Switch u and v here to match cursor semantics below
                for (var uIdx = 0L; uIdx < dims.getItem(u).toLong(); uIdx++) {
                    for (var vIdx = 0; vIdx < dims.getItem(v).toLong(); vIdx++) {
                        var shouldSkip = skip.getItem(idxTuple);
                        var pixel = view.getItem(e.newPyTuple(e.fromJava(uIdx), e.fromJava(vIdx))).toLong();
                        depths.setItem(e.newPyTuple(d, e.fromJava(sIdx), e.fromJava(uIdx), e.fromJava(vIdx)),
                                (shouldSkip.isFalse() && pixel == maskColor.toLong()) ? e.fromJava(sOp) : e.fromJava(s));
                    }
                }

            }

            //Clear out volume where ray goes through entirely
            for (cursor.setItem(v, e.fromJava(0)); cursor.getItem(v).toLong() < dims.getItem(v).toLong(); cursor.setItem(v, (PyObject) e.invokeMethod(cursor.getItem(v), "__add__", e.fromJava(1)))) {
                for (cursor.setItem(u, e.fromJava(0)); cursor.getItem(u).toLong() < dims.getItem(u).toLong(); cursor.setItem(u, (PyObject) e.invokeMethod(cursor.getItem(u), "__add__", e.fromJava(1)))) {
                    for (cursor.setItem(d, depths.getItem(e.newPyTuple(d, e.fromJava(1), cursor.getItem(v), cursor.getItem(u))));
                            cursor.getItem(d).toLong() <= depths.getItem(e.newPyTuple(d, e.fromJava(0), cursor.getItem(v), cursor.getItem(u))).toLong();
                            cursor.setItem(d, (PyObject) e.invokeMethod(cursor.getItem(d), "__add__", e.fromJava(1)))) {
                        var slice = (PyObject) e.eval("slice");
                        var tuple = (PyObject) e.eval("tuple");
                        volume.setItem(tuple.call(cursor.getItem(slice.call(e.getNone(), e.getNone(), e.fromJava(-1)))), maskColor);
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
                    var idxTuple = e.newPyTuple(e.fromJava(d), e.fromJava(sIdx));
                    if (skip.getItem(idxTuple).isTrue()) {
                        continue;
                    }

                    var view = views.getItem(idxTuple);
                    var depth = depths.getItem(idxTuple);

                    for (cursor.setItem(e.fromJava(v), e.fromJava(0)); cursor.getItem(e.fromJava(v)).toLong() < dims.getItem(e.fromJava(v)).toLong(); cursor.setItem(e.fromJava(v), (PyObject) e.invokeMethod(cursor.getItem(e.fromJava(v)), "__add__", e.fromJava(1))))
                        for (cursor.setItem(e.fromJava(u), e.fromJava(0)); cursor.getItem(e.fromJava(u)).toLong() < dims.getItem(e.fromJava(u)).toLong(); cursor.setItem(e.fromJava(u), (PyObject) e.invokeMethod(cursor.getItem(e.fromJava(u)), "__add__", e.fromJava(1)))) {

                            //March along ray
                            for (cursor.setItem(e.fromJava(d), depth.getItem(e.newPyTuple(cursor.getItem(e.fromJava(v)), cursor.getItem(e.fromJava(u))))); 0 <= cursor.getItem(e.fromJava(d)).toLong() && cursor.getItem(e.fromJava(d)).toLong() < dims.getItem(e.fromJava(d)).toLong(); cursor.setItem(e.fromJava(d), (PyObject) e.invokeMethod(cursor.getItem(e.fromJava(d)), "__add__", s))) {

                                //Read volume color
                                var volIdx = e.newPyTuple(cursor.getItem(e.fromJava(2)), cursor.getItem(e.fromJava(1)), cursor.getItem(e.fromJava(0)));
                                var color = volume.getItem(volIdx).toLong();
                                if (color == maskColor.toLong()) {
                                    continue;
                                }
                                volume.setItem(volIdx, view.getItem(e.newPyTuple(cursor.getItem(e.fromJava(v)), cursor.getItem(e.fromJava(u)))));
                                color = volume.getItem(volIdx).toLong();

                                //Check photo-consistency of volume at cursor
                                var consistent = true;
                                for (var a = 0; consistent && a < 3; ++a) {
                                    var b = (a + 1) % 3;
                                    var c = (a + 2) % 3;
                                    for (var t = 0; t < 2; ++t) {
                                        idxTuple = e.newPyTuple(e.fromJava(a), e.fromJava(t));
                                        if (skip.getItem(idxTuple).isTrue()) {
                                            continue;
                                        }
                                        var idxTupleInner = e.newPyTuple(e.fromJava(a), e.fromJava(t), cursor.getItem(e.fromJava(c)), cursor.getItem(e.fromJava(b)));
                                        var fColor = views.getItem(idxTupleInner).toLong();
                                        var fDepth = depths.getItem(idxTupleInner).toLong();
                                        if (t != 0 ? fDepth <= cursor.getItem(e.fromJava(a)).toLong() : cursor.getItem(e.fromJava(a)).toLong() <= fDepth) {
                                            if (fColor != color) {
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
                            depth.setItem(e.newPyTuple(cursor.getItem(e.fromJava(v)), cursor.getItem(e.fromJava(u))), cursor.getItem(e.fromJava(d)));
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
            var pyObjArr1D = new PyObject[views.getFirst().size()];
            for (var j = 0; j < views.getFirst().size(); j++) {
                pyObjArr1D[j] = (PyObject) e.invokeFunction("int", views.get(i).get(j));
            }
            var pyList1D = e.newPyList(pyObjArr1D);
            pyObjArr2D[i] = pyList1D;
        }
        var pyList2D = e.newPyList(pyObjArr2D);
        e.put("views", pyList2D);
        var sideLen = (int) Math.sqrt(views.getFirst().size());
        return (PyObject) e.invokeMethod(e.eval("np.array(views)"), "reshape", 3, 2, sideLen, sideLen);
    }
}

