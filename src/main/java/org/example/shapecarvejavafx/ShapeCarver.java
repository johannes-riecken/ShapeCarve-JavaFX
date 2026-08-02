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
        c.volumeDims = e.newPyTuple(e.fromJava(views[2].length), e.fromJava(views[0][0].length), e.fromJava(views[0].length));
        var intDType = (PyObject) e.eval("int");
        var npDotEmpty = (PyObject) e.eval("np.empty");
        c.volume = npDotEmpty.call(c.volumeDims, intDType);

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

    PyTuple volumeDims; /* cuboid shape */
    // Type: MArray{Tuple{S, S, S}, Int}
    PyObject volume;

    // note that JavaFX uses a y-down coordinate system, so the views are left, right, top, bottom, front, back
    public PyObject carve(
            PythonScriptEngine e,
            // Type: SArray{Tuple{3, 2, S, S}, Int}
            PyObject views /* 2d images {x,y,z}-{front,back} */,
            final PyObject maskColor,
            // Type: SArray{Tuple{3, 2}, Bool}
            PyObject skip /* views to skip */) throws ScriptException, NoSuchMethodException {
        Objects.requireNonNull(views);
        Objects.requireNonNull(skip);


        //Initialize volume. This is necessary.
        e.invokeMethod(volume, "fill", e.fromJava(-1));

        var npDotEmpty = (PyObject) e.eval("np.empty");
        var intDType = (PyObject) e.eval("int");
        // Type: MArray{Tuple{3, 2, S, S}, Int}
        var depths = npDotEmpty.call(views.getAttribute("shape"), intDType);
        // Type: MVector{3, Int}
        var cursor = npDotEmpty.call(e.fromJava(3), intDType); // (z, y, x)
        //Initialize depth fields
        var dIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
        while (true) {
            try {
                var projAxis = (PyObject) e.invokeMethod(dIt, "__next__");
                var u = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(projAxis, "__add__", e.fromJava(1)), "__mod__", e.fromJava(3)); // other axis 0
                var v = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(projAxis, "__add__", e.fromJava(2)), "__mod__", e.fromJava(3)); // other axis 1
                var sideIdxIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                while (true) {
                    try {
                        var sideIdx = (PyObject) e.invokeMethod(sideIdxIt, "__next__");
                        var idxTuple = e.newPyTuple(projAxis, sideIdx);
                        var view = views.getItem(idxTuple);
                        var isMinSide = (PyObject) e.invokeMethod(sideIdx, "__eq__", e.fromJava(0));
                        var isMaxSide = (PyObject) e.invokeMethod(sideIdx, "__eq__", e.fromJava(1));
                        var nearBoundary = (PyObject) (isMaxSide.isTrue() ? e.invokeMethod(volumeDims.getItem(projAxis), "__sub__", e.fromJava(1)) : e.fromJava(0));
                        var farBoundary = (PyObject) (isMinSide.isTrue() ? e.invokeMethod(volumeDims.getItem(projAxis), "__sub__", e.fromJava(1)) : e.fromJava(0));
                        // TODO: Switch u and v here to match cursor semantics below
                        var uIdxIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(u)), "__iter__");
                        while (true) {
                            try {
                                var uIdx = (PyObject) e.invokeMethod(uIdxIt, "__next__");
                                var vIdxIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(v)), "__iter__");
                                while (true) {
                                    try {
                                        var vIdx = (PyObject) e.invokeMethod(vIdxIt, "__next__");
                                        var shouldSkip = skip.getItem(idxTuple);
                                        var viewColor = view.getItem(e.newPyTuple(uIdx, vIdx));
                                        var isMaskColor = (PyObject) e.invokeMethod(viewColor, "__eq__", maskColor);
                                        depths.setItem(e.newPyTuple(projAxis, sideIdx, uIdx, vIdx),
                                                shouldSkip.isFalse() && isMaskColor.isTrue() ? farBoundary : nearBoundary);
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
                var cursorVIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(v)), "__iter__");
                while (true) {
                    try {
                        var cursorV = (PyObject) e.invokeMethod(cursorVIt, "__next__");
                        cursor.setItem(v, cursorV);
                        var cursorUIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(u)), "__iter__");
                        while (true) {
                            try {
                                var cursorU = (PyObject) e.invokeMethod(cursorUIt, "__next__");
                                cursor.setItem(u, cursorU);
                                var startDepth = depths.getItem(e.newPyTuple(projAxis, e.fromJava(1), cursor.getItem(v), cursor.getItem(u)));
                                var cursorDIt = e.invokeMethod(e.invokeFunction("range", startDepth, e.invokeMethod(depths.getItem(e.newPyTuple(projAxis, e.fromJava(0), cursor.getItem(v), cursor.getItem(u))), "__add__", e.fromJava(1))), "__iter__");
                                while (true) {
                                    try {
                                        var cursorD = (PyObject) e.invokeMethod(cursorDIt, "__next__");
                                        cursor.setItem(projAxis, cursorD);
                                        var slice = (PyObject) e.eval("slice");
                                        var someList = cursor.getItem(slice.call(e.getNone(), e.getNone(), e.fromJava(-1)));
                                        volume.setItem(e.newPyTuple(someList.getItem(e.fromJava(0)), someList.getItem(e.fromJava(1)), someList.getItem(e.fromJava(2))), maskColor);
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
        var voxelsRemoved = e.fromJava(1);
        var cond = (PyObject) (e.invokeMethod(voxelsRemoved, "__gt__", e.fromJava(0)));
        while (cond.isTrue()) {
            voxelsRemoved = e.fromJava(0);
            var dItNew = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
            while (true) {
                try {
                    var projAxis = (PyObject) e.invokeMethod(dItNew, "__next__");
                    var u = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(projAxis, "__add__", e.fromJava(1)), "__mod__", e.fromJava(3)); // other axis 0
                    var v = (PyObject) e.invokeMethod((PyObject) e.invokeMethod(projAxis, "__add__", e.fromJava(2)), "__mod__", e.fromJava(3)); // other axis 1

                    //Do front/back sweep
                    var sideIdxIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                    while (true) {
                        try {
                            var sideIdx = (PyObject) e.invokeMethod(sideIdxIt, "__next__");
                            var sweepDirection = e.invokeMethod(e.invokeMethod(sideIdx, "__mul__", e.fromJava(-2)), "__add__", e.fromJava(1));
                            var idxTuple = e.newPyTuple(projAxis, sideIdx);
                            var skipThis = skip.getItem(idxTuple);
                            if (skipThis.isTrue()) {
                                continue;
                            }

                            var view = views.getItem(idxTuple);
                            var depth = depths.getItem(idxTuple);

                            var cursorVIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(v)), "__iter__");
                            while (true) {
                                try {
                                    var cursorV = (PyObject) e.invokeMethod(cursorVIt, "__next__");
                                    cursor.setItem(v, cursorV);
                                    var cursorUIt = e.invokeMethod(e.invokeFunction("range", volumeDims.getItem(u)), "__iter__");
                                    while (true) {
                                        try {
                                            var cursorU = (PyObject) e.invokeMethod(cursorUIt, "__next__");
                                            cursor.setItem(u, cursorU);
                                            //March along ray
                                            var start = depth.getItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u)));
                                            var isForwardSweep = (PyObject) e.invokeMethod(sweepDirection, "__eq__", e.fromJava(1));
                                            var stop = isForwardSweep.isTrue() ? volumeDims.getItem(projAxis) : e.fromJava(-1);
                                            var cursorDIt = e.invokeMethod(e.invokeFunction("range", start, stop, sweepDirection), "__iter__");
                                            while (true) {
                                                try {
                                                    var cursorD = (PyObject) e.invokeMethod(cursorDIt, "__next__");
                                                    cursor.setItem(projAxis, cursorD);
                                                    //Read volume color
                                                    var volIdx = e.newPyTuple(cursor.getItem(e.fromJava(2)), cursor.getItem(e.fromJava(1)), cursor.getItem(e.fromJava(0)));
                                                    var color = volume.getItem(volIdx);
                                                    var isMaskColor = (PyObject) e.invokeMethod(color, "__eq__", maskColor);
                                                    if (isMaskColor.isTrue()) {
                                                        continue;
                                                    }
                                                    volume.setItem(volIdx, view.getItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u))));
                                                    color = volume.getItem(volIdx);

                                                    //Check photo-consistency of volume at cursor
                                                    var isConsistent = e.getTrue();
                                                    var checkAxisIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(3)), "__iter__");
                                                    while (true) {
                                                        try {
                                                            var checkAxis = (PyObject) e.invokeMethod(checkAxisIt, "__next__");
                                                            var checkU = (PyObject) e.invokeMethod(e.invokeMethod(checkAxis, "__add__", e.fromJava(1)), "__mod__", e.fromJava(3));
                                                            var checkV = (PyObject) e.invokeMethod(e.invokeMethod(checkAxis, "__add__", e.fromJava(2)), "__mod__", e.fromJava(3));
                                                            var checkSideIt = e.invokeMethod(e.invokeFunction("range", e.fromJava(2)), "__iter__");
                                                            while (true) {
                                                                try {
                                                                    var checkSide = (PyObject) e.invokeMethod(checkSideIt, "__next__");
                                                                    idxTuple = e.newPyTuple(checkAxis, checkSide);
                                                                    var skipNewThis = skip.getItem(idxTuple);
                                                                    if (skipNewThis.isTrue()) {
                                                                        continue;
                                                                    }
                                                                    var idxTupleInner = e.newPyTuple(checkAxis, checkSide, cursor.getItem(checkV), cursor.getItem(checkU));
                                                                    var checkViewColor = views.getItem(idxTupleInner);
                                                                    var checkViewDepth = depths.getItem(idxTupleInner);
                                                                    var isMaxSide = (PyObject) e.invokeMethod(checkSide, "__ne__", e.fromJava(0));
                                                                    var isDepthOccluded = isMaxSide.isTrue() ? (PyObject) e.invokeMethod(checkViewDepth, "__le__", cursor.getItem(checkAxis)) : (PyObject) e.invokeMethod(cursor.getItem(checkAxis), "__le__", checkViewDepth);
                                                                    if (isDepthOccluded.isTrue()) {
                                                                        var isColorDifferent = (PyObject) e.invokeMethod(checkViewColor, "__ne__", color);
                                                                        if (isColorDifferent.isTrue()) {
                                                                            isConsistent = e.getFalse();
                                                                            break;
                                                                        }
                                                                    }
                                                                } catch (Exception ex) {
                                                                    break;
                                                                }
                                                            }
                                                            if (isConsistent.isFalse()) {
                                                                break;
                                                            }
                                                        } catch (Exception ex) {
                                                            break;
                                                        }
                                                    }
                                                    if (isConsistent.isTrue()) {
                                                        break;
                                                    }

                                                    //Clear out voxel
                                                    voxelsRemoved = (PyObject) e.invokeMethod(voxelsRemoved, "__add__", e.fromJava(1));
                                                    volume.setItem(volIdx, maskColor);
                                                } catch (Exception ex) {
                                                    break;
                                                }
                                            }
                                            //Update depth value
                                            depth.setItem(e.newPyTuple(cursor.getItem(v), cursor.getItem(u)), cursor.getItem(projAxis));
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
            cond = (PyObject) (e.invokeMethod(voxelsRemoved, "__gt__", e.fromJava(0)));
        }

        ////Do a final pass to fill in any missing colors
        volume.setItem((PyObject) e.invokeMethod(volume, "__lt__", e.fromJava(0)), e.fromJava(0xff00ff));

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
        return (PyObject) e.invokeMethod(e.eval("np.array(views)"), "reshape", e.fromJava(3), e.fromJava(2), sideLen, sideLen);
    }
}

