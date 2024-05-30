package org.example.shapecarvejavafx;

import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class HelloApplication extends Application {
    private double mouseDeltaX;
    private double mouseDeltaY;
    private double mouseOldX;
    private double mouseOldY;
    private double mousePosX;
    private double mousePosY;

    @Override
    public void start(Stage stage) {

        PerspectiveCamera camera;
        final double sceneWidth = 800;
        final double sceneHeight = 600;
        final Xform cameraTransform = new Xform();

        Group sceneRoot = new Group();
        SubScene scene = new SubScene(sceneRoot, sceneWidth, sceneHeight, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.ALICEBLUE);
        camera = new PerspectiveCamera(true);

        //setup camera transform for rotational support
        cameraTransform.setTranslate(0, 0, 0);
        cameraTransform.getChildren().add(camera);
        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        camera.setTranslateZ(-70);
        camera.setTranslateX(10);
        cameraTransform.ry.setAngle(-35.0);
        cameraTransform.rx.setAngle(-10.0);
        PointLight light = new PointLight(Color.WHITE);
        cameraTransform.getChildren().add(light);
        light.setTranslateX(camera.getTranslateX());
        light.setTranslateY(camera.getTranslateY());
        light.setTranslateZ(camera.getTranslateZ());
        scene.setCamera(camera);

        var views = getViews();

        Group group = groupOfBoxes(new int[]{16, 16, 16});

        group.getChildren().add(cameraTransform);


        sceneRoot.getChildren().addAll(group);

        setUpEvents(scene, camera, cameraTransform);

        var carver = new ShapeCarver();
        var output = carver.carve(views, 0, new boolean[]{false, false, false, false, false, false});
        ContentCoroutine coroutine = new ContentCoroutine(output, group);

        Scene rootScene = setUpRootScene(scene, coroutine);
        stage.setScene(rootScene);
        stage.show();
    }

    private @NotNull Group groupOfBoxes(int[] _dims) {
        Objects.requireNonNull(_dims);
        List<Integer> dims = Arrays.stream(_dims).boxed().toList();

        var g = new Group();
        var pos = new int[3]; // x, y, z
        for (pos[2] = 0; pos[2] < dims.get(2); pos[2]++) {
            for (pos[1] = 0; pos[1] < dims.get(1); pos[1]++) {
                for (pos[0] = 0; pos[0] < dims.get(0); pos[0]++) {
                    var box = new Box(1, 1, 1);
                    box.setTranslateX(pos[0]);
                    box.setTranslateY(pos[1]);
                    box.setTranslateZ(pos[2]);
                    box.setMaterial(new PhongMaterial(Color.ANTIQUEWHITE));
                    g.getChildren().add(box);
                }
            }
        }
        return g;
    }

    private static Scene setUpRootScene(SubScene scene, ContentCoroutine coroutine) {
        Objects.requireNonNull(scene);
        StackPane sp = new StackPane();
        sp.setPrefSize(800.0, 600.0);
        sp.setMaxSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
        sp.setMinSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
        sp.setBackground(Background.EMPTY);
        sp.getChildren().add(scene);
        var button = new Button("Next");
        button.setTranslateX(100);
        button.setTranslateY(100);
        button.setOnAction(_ -> iterate(coroutine));
        sp.getChildren().add(button);
        sp.setPickOnBounds(false);

        scene.widthProperty().bind(sp.widthProperty());
        scene.heightProperty().bind(sp.heightProperty());

        return new Scene(sp);
    }

    private static void iterate(ContentCoroutine coroutine) {
        if (coroutine == null) {
            return;
        }
        if (!coroutine.isComplete()) {
            coroutine.next();  // This will run the computation for one slice of z and then return
            // You can add other logic here if needed between updates, such as UI refreshes or pauses
        }
    }

    private void setUpEvents(SubScene scene, PerspectiveCamera camera, Xform cameraTransform) {
        Objects.requireNonNull(scene);
        Objects.requireNonNull(camera);
        Objects.requireNonNull(cameraTransform);

        //First person shooter keyboard movement
        scene.setOnKeyPressed(event -> {
            double change = 10.0;
            //Add shift modifier to simulate "Running Speed"
            if (event.isShiftDown()) {
                change = 50.0;
            }
            //What key did the user press?
            KeyCode keycode = event.getCode();
            //Step 2c: Add Zoom controls
            if (keycode == KeyCode.W) {
                camera.setTranslateZ(camera.getTranslateZ() + change);
            }
            if (keycode == KeyCode.S) {
                camera.setTranslateZ(camera.getTranslateZ() - change);
            }
            //Step 2d:  Add Strafe controls
            if (keycode == KeyCode.A) {
                camera.setTranslateX(camera.getTranslateX() - change);
            }
            if (keycode == KeyCode.D) {
                camera.setTranslateX(camera.getTranslateX() + change);
            }
        });

        scene.setOnMousePressed((MouseEvent me) -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });
        scene.setOnMouseDragged((MouseEvent me) -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseDeltaX = (mousePosX - mouseOldX);
            mouseDeltaY = (mousePosY - mouseOldY);

            double modifier = 10.0;
            double modifierFactor = 0.1;

            if (me.isControlDown()) {
                modifier = 0.1;
            }
            if (me.isShiftDown()) {
                modifier = 50.0;
            }
            if (me.isPrimaryButtonDown()) {
                cameraTransform.ry.setAngle(((cameraTransform.ry.getAngle() + mouseDeltaX * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180);  // +
                cameraTransform.rx.setAngle(((cameraTransform.rx.getAngle() - mouseDeltaY * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180);  // -
            } else if (me.isSecondaryButtonDown()) {
                double z = camera.getTranslateZ();
                double newZ = z + mouseDeltaX * modifierFactor * modifier;
                camera.setTranslateZ(newZ);
            } else if (me.isMiddleButtonDown()) {
                cameraTransform.t.setX(cameraTransform.t.getX() + mouseDeltaX * modifierFactor * modifier * 0.3);  // -
                cameraTransform.t.setY(cameraTransform.t.getY() + mouseDeltaY * modifierFactor * modifier * 0.3);  // -
            }
        });
    }

    private static List<List<Integer>> getViews() {
        var views = new int[6][256];
        var img = new Image("file:///Users/rieckenj/Pictures/cross.png");
        var reader = img.getPixelReader();
        var width = (int) img.getWidth();
        var height = (int) img.getHeight();
        var pixels = new int[width * height];
        reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = pixels[i] & 0x00ffffff; // discard transparency value
        }
        views[0] = pixels;
        views[1] = pixels;
        views[2] = pixels;
        views[3] = pixels;
        views[4] = pixels;
        views[5] = pixels;
        return Arrays.stream(views).map(e -> Arrays.stream(e).boxed().toList()).toList();
    }

    public static void create3DContent(Output o, Group g) {
        Objects.requireNonNull(o);
        Objects.requireNonNull(g);

        var volume = o.volume();
        var dims = o.dims();
        var pos = new int[3]; // x, y, z
        for (pos[2] = 0; pos[2] < dims.get(2); pos[2]++) {
            for (pos[1] = 0; pos[1] < dims.get(1); pos[1]++) {
                for (pos[0] = 0; pos[0] < dims.get(0); pos[0]++) {
                    var color = volume.get(pos[0] + dims.get(0) * (pos[1] + dims.get(1) * pos[2]));
                    var box = (Box) g.getChildren().get(pos[0] + dims.get(0) * (pos[1] + dims.get(1) * pos[2]));
                    if (color != 0) {
                        box.setMaterial(new PhongMaterial(Color.rgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)));
                    } else {
                        box.setVisible(false);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
