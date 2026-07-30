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
        var dIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
        while (true) {
            try {
                var d = (PyObject) e.invokeMethod(dIt, "__next__");
                var u = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", e.fromJava(1)), "__mod__", e.fromJava(3)); // other axis 0
                var v = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", e.fromJava(2)), "__mod__", e.fromJava(3)); // other axis 1
                var sIdxIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                while (true) {
                    try {
                        var sIdx = (PyObject) e.invokeMethod(sIdxIt, "__next__");
                        var idxTuple = e.newPyTuple(d, sIdx);
                        var view = views.getItem(idxTuple);
                        var s = (PyObject) (sIdx.equals(e.fromJava(1)) ? e.invokeMethod(dims.getItem(d), "__sub__", e.fromJava(1)) : e.fromJava(0));
                        var sOp = (PyObject) (sIdx.equals(e.fromJava(0)) ? e.invokeMethod(dims.getItem(d), "__sub__", e.fromJava(1)) : e.fromJava(0));
                        // TODO: Switch u and v here to match cursor semantics below
                        var uIdxIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(u)), "__iter__");
                        while (true) {
                            try {
                                var uIdx = (PyObject) e.invokeMethod(uIdxIt, "__next__");
                                var vIdxIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(v)), "__iter__");
                                while (true) {
                                    try {
                                        var vIdx = (PyObject) e.invokeMethod(vIdxIt, "__next__");
                                        var shouldSkip = skip.getItem(idxTuple);
                                        var pixel = view.getItem(e.newPyTuple(uIdx, vIdx));
                                        // the ternary can always take the false branch
                                        // and my test still succeeds :-(
                                        depths.setItem(e.newPyTuple(d, sIdx, uIdx, vIdx),
                                                (shouldSkip.isFalse() && pixel.equals(maskColor)) ? sOp : s);
                                    } catch (Exception ex) {
                                        break;
                                    }
                                }
                            } catch (Exception ex) {
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        break;
                    }
                }

                //Clear out volume where ray goes through entirely
                var cursorVIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(v)), "__iter__");
                while (true) {
                    try {
                        var cursorV = (PyObject) e.invokeMethod(cursorVIt, "__next__");
                        cursor.setItem(v, cursorV);
                        var cursorUIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(u)), "__iter__");
                        while (true) {
                            try {
                                var cursorU = (PyObject) e.invokeMethod(cursorUIt, "__next__");
                                cursor.setItem(u, cursorU);
                                var cursorDIt = e.invokeMethod(e.invokeFunction("range", depths.getItem(e.newPyTuple(d, e.fromJava(1), cursor.getItem(v), cursor.getItem(u))), e.invokeMethod(depths.getItem(e.newPyTuple(d, e.fromJava(0), cursor.getItem(v), cursor.getItem(u))), "__add__", e.fromJava(1))), "__iter__");
                                while (true) {
                                    try {
                                        var cursorD = (PyObject) e.invokeMethod(cursorDIt, "__next__");
                                        cursor.setItem(d, cursorD);
                                        var slice = (PyObject) e.eval("slice");
                                        volume.setItem(e.newPyTuple(cursor.getItem(slice.call(e.getNone(), e.getNone(), e.fromJava(-1)))), maskColor);
                                    } catch (Exception ex) {
                                        break;
                                    }
                                }
                            } catch (Exception ex) {
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        break;
                    }
                }
            } catch (Exception ex) {
                break;
            }
        }

        //Perform iterative shape carving until convergence
        var removed = e.fromJava(1);
        var cond = (PyObject) (e.invokeMethod(removed, "__gt__", e.fromJava(0)));
        while (cond.isTrue()) {
            removed = e.fromJava(0);
            var dItNew = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
            while (true) {
                try {
                    var d = (PyObject) e.invokeMethod(dItNew, "__next__");
                    var u = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", e.fromJava(1)), "__mod__", e.fromJava(3)); // other axis 0
                    var v = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(d, "__add__", e.fromJava(2)), "__mod__", e.fromJava(3)); // other axis 1

                    //Do front/back sweep
                    var sIdxIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                    while (true) {
                        try {
                            var sIdx = e.invokeMethod(sIdxIt, "__next__");
                            var s = e.invokeMethod(e.invokeMethod(sIdx, "__mul__", -2), "__add__", 1);
                            var idxTuple = e.newPyTuple(d, e.fromJava(sIdx));
                            if (skip.getItem(idxTuple).isTrue()) {
                                continue;
                            }

                            var view = views.getItem(idxTuple);
                            var depth = depths.getItem(idxTuple);

                            var cursorVIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(v)), "__iter__");
                            while (true) {
                                try {
                                    var cursorV = (PyObject) e.invokeMethod(cursorVIt, "__next__");
                                    cursor.setItem(v, cursorV);
                                    var cursorUIt = e.invokeMethod(e.invokeFunction("range", dims.getItem(u)), "__iter__");
                                    while (true) {
                                        try {
                                            var cursorU = (PyObject) e.invokeMethod(cursorUIt, "__next__");
                                            cursor.setItem(u, cursorU);
                                            //March along ray
                                            for (cursor.setItem(d, depth.getItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u)))); 0 <= cursor.getItem(d).toLong() && cursor.getItem(d).toLong() < dims.getItem(d).toLong(); cursor.setItem(d, (PyObject) e.invokeMethod(cursor.getItem(d), "__add__", s))) {

                                                //Read volume color
                                                var volIdx = e.newPyTuple(cursor.getItem(e.fromJava(2)), cursor.getItem(e.fromJava(1)), cursor.getItem(e.fromJava(0)));
                                                var color = volume.getItem(volIdx);
                                                if (((PyObject) e.invokeMethod(color, "__eq__", maskColor)).isTrue()) {
                                                    continue;
                                                }
                                                volume.setItem(volIdx, view.getItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u))));
                                                color = volume.getItem(volIdx);

                                                //Check photo-consistency of volume at cursor
                                                var consistent = e.getTrue();
                                                var aIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
                                                while (true) {
                                                    try {
                                                        var a = (PyObject) e.invokeMethod(aIt, "__next__");
                                                        var b = (PyObject) e.invokeMethod(e.invokeMethod(a, "__add__", e.fromJava(1)), "__mod__", 3);
                                                        var c = (PyObject) e.invokeMethod(e.invokeMethod(a, "__add__", e.fromJava(2)), "__mod__", 3);
                                                        var tIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                                                        while (true) {
                                                            try {
                                                                var t = (PyObject) e.invokeMethod(tIt, "__next__");
                                                                idxTuple = e.newPyTuple(a, t);
                                                                if (skip.getItem(idxTuple).isTrue()) {
                                                                    continue;
                                                                }
                                                                var idxTupleInner = e.newPyTuple(a, t, cursor.getItem(c), cursor.getItem(b));
                                                                var fColor = views.getItem(idxTupleInner);
                                                                var fDepth = depths.getItem(idxTupleInner);
                                                                if ((((PyObject) e.invokeMethod(t, "__ne__", e.fromJava(0))).isTrue() ? (PyObject) e.invokeMethod(fDepth, "__le__", cursor.getItem(a)) : (PyObject) e.invokeMethod(cursor.getItem(a), "__le__", fDepth)).isTrue()) {
                                                                    if (((PyObject) e.invokeMethod(fColor, "__ne__", color)).isTrue()) {
                                                                        consistent = e.getFalse();
                                                                        break;
                                                                    }
                                                                }
                                                            } catch (Exception ex) {
                                                                break;
                                                            }
                                                        }
                                                        if (consistent.isFalse()) {
                                                            break;
                                                        }
                                                    } catch (Exception ex) {
                                                        break;
                                                    }
                                                }
                                                if (consistent.isTrue()) {
                                                    break;
                                                }

                                                //Clear out voxel
                                                removed = (PyObject) e.invokeMethod(removed, "__add__", e.fromJava(1));;
                                                volume.setItem(volIdx, maskColor);
                                            }

                                            //Update depth value
                                            depth.setItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u)), cursor.getItem(d));
                                        } catch (Exception ex) {
                                            break;
                                        }
                                    }
                                } catch (Exception ex) {
                                    break;
                                }
                            }
                        } catch (Exception ex) {
                            break;
                        }
                    }
                } catch (Exception ex) {
                    break;
                }
            }
            cond = (PyObject) (e.invokeMethod(removed, "__gt__", e.fromJava(0)));
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

