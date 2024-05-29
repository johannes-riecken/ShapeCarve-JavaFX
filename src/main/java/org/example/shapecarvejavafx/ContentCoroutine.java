package org.example.shapecarvejavafx;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

public class ContentCoroutine {
    private Output output;
    private Group group;
    private int[] dims;
    private int[] volume;
    private int[] pos = new int[3]; // x, y, z initialized to 0,0,0
    private boolean isComplete = false;

    public ContentCoroutine(Output o, Group g) {
        this.output = o;
        this.group = g;
        this.dims = o.dims();
        this.volume = o.volume();
        this.pos[2] = 0;  // Initialize the outermost loop variable
    }

    public boolean isComplete() {
        return isComplete;
    }

    // Method to perform one slice of work and yield control
    public void next() {
        if (isComplete) return;

        int z = pos[2];
        for (int y = 0; y < dims[1]; y++) {
            for (int x = 0; x < dims[0]; x++) {
                int index = x + dims[0] * (y + dims[1] * z);
                int color = volume[index];
                Box box = (Box) group.getChildren().get(index);
                if (color != 0) {
                    box.setMaterial(new PhongMaterial(Color.rgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)));
                } else {
                    box.setVisible(false);
                }
            }
        }

        // Move to the next z-slice
        pos[2]++;
        if (pos[2] >= dims[2]) {
            isComplete = true;  // Mark completion if the outer loop is done
        }
    }
}
