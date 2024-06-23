package org.example.shapecarvejavafx;

import ai.djl.Model;
import ai.djl.ModelException;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.*;
import ai.djl.util.Utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MatrixProductExample {

    public static void main(String[] args) throws ModelException, IOException, TranslateException {
        Path scriptPath = Paths.get("/Users/rieckenj/repos/java/ShapeCarve-JavaFX/src/main/java/org/example/shapecarvejavafx/matrix_product.py");

        // Load the Python model
        Model model = Model.newInstance("matrix_product", "Python");
        model.setBlock(null);

        // Define a translator
        Translator<NDList, NDArray> translator = new Translator<NDList, NDArray>() {

            @Override
            public NDArray processOutput(TranslatorContext ctx, NDList list) throws Exception {
                return list.singletonOrThrow();
            }

            @Override
            public NDList processInput(TranslatorContext ctx, NDList input) throws Exception {
                return input;
            }

            @Override
            public Batchifier getBatchifier() {
                return null;
            }
        };

        try (NDManager manager = NDManager.newBaseManager()) {
            // Create two NDArrays to pass to the Python function
            NDArray A = manager.create(new float[]{1, 2, 3, 4, 5, 6}, new Shape(2, 3));
            NDArray B = manager.create(new float[]{7, 8, 9, 10, 11, 12}, new Shape(3, 2));

            // Run the Python function
            NDArray result = model.newPredictor(translator).predict(new NDList(A, B));

            // Process and print the result
            System.out.println(result);
        }
    }
}
