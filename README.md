The front side (i.e. the z=0 plane) in MagicaVoxel is the transpose of the left view.

Testing correctness of Java version:

```
run () {
	python3 test_case_to_java_obj.py $1 && javac --module-path /Users/rieckenj/java-modules --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml -d bin src/main/java/org/example/shapecarvejavafx/ShapeCarver.java && java -cp bin org.example.shapecarvejavafx.ShapeCarver $1
}
python3 ~/repos/blender-lba/vox/shapecarverconv.py "$(cat volume.txt )"
```

Testing correctness of generated Python:

```
sh convert.sh && python3 main.py >|/tmp/volume_py.txt && git diff /tmp/volume{,_py}.txt
```

TODOs:

- [ ] Add conversion to Julia
- [x] Create manual quiz for deriving StaticArrays.jl meta-programming
- [x] Create manual quiz for deriving NumPy types
- [x] Migrate to using my coordinate conventions, i.e. get rid of the transposition in the beginning
- [x] Vectorize the Python code
- [ ] Add a conversion in the Haskell code to rewrite NumPy array indexing to regular Python code
- [ ] Add parser from the generated Python code back to the AST
- [ ] Add pretty-printer from the AST back to Detroit code
- [x] Use pretty-printing library
- [ ] Return multiple results (possibly zero if no volume consistent with the views was found)
- [ ] Use QuickCheck to find a minimal volume that fails the invariant that the volume is consistent with the views
