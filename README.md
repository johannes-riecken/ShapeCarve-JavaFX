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
