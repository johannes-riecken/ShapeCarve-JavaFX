#!/bin/sh
set -xeu
perl -nE 'if (/"fill"/ ... /0xff00ff/) {s/^ {8}//; print;}' src/main/java/org/example/shapecarvejavafx/ShapeCarver.java >|/tmp/shape_carver.java
runhaskell -package=extra -package=parsec -package=parser-combinators -package=hspec -package=prettyprinter -package=text PythonConverter.hs </tmp/shape_carver.java >|snippet.py
perl fill_template.pl template.py
