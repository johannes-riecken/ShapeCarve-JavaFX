package org.example.shapecarvejavafx;

import javafx.application.Application;
import javafx.scene.*;
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

        var o = ShapeCarver.carve(new int[]{16, 16, 16}, views, 0, new boolean[]{false, false, false, false, false, false});
        Group group = create3DContent(o);
        group.getChildren().add(cameraTransform);


        sceneRoot.getChildren().addAll(group);

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

        StackPane sp = new StackPane();
        sp.setPrefSize(sceneWidth, sceneHeight);
        sp.setMaxSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
        sp.setMinSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
        sp.setBackground(Background.EMPTY);
        sp.getChildren().add(scene);
        sp.setPickOnBounds(false);

        scene.widthProperty().bind(sp.widthProperty());
        scene.heightProperty().bind(sp.heightProperty());

        stage.setScene(new Scene(sp));
        stage.show();
    }

    private static int[][] getViews() {
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
        return views;
    }

//    @Override
//    public void start(Stage stage) {
////        var views = new int[][]{{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16755790, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 0, 16755790, 16755790, 7833856, 0, 0, 0, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 14959671, 0, 16755790, 16755790, 7833856, 16690254, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 14959671, 16755790, 16755790, 7833856, 7833856, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 7833856, 14959671, 14959671, 7833856, 7833856, 16690254, 7833856, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 7833856, 14959671, 14959671, 16690254, 16690254, 16690254, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 16690254, 16690254, 16690254, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 7833856, 16690254, 7833856, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 7833856, 7833856, 7833856, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 14959671, 7833856, 16624718, 16624718, 7833856, 0, 0, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16755790, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 0, 16755790, 16755790, 7833856, 0, 0, 0, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 14959671, 0, 16755790, 16755790, 7833856, 16690254, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 14959671, 16755790, 16755790, 7833856, 7833856, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 7833856, 14959671, 14959671, 7833856, 7833856, 16690254, 7833856, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 7833856, 14959671, 14959671, 16690254, 16690254, 16690254, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 16690254, 16690254, 16690254, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 7833856, 16690254, 7833856, 16690254, 16690254, 7833856, 7833856, 7833856, 16690254, 16690254, 16690254, 14959671, 7833856, 7833856, 14959671, 14959671, 7833856, 7833856, 7833856, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 14959671, 7833856, 16624718, 16624718, 7833856, 0, 0, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 16755790, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 16755790, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 16755790, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 16755790, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 0, 16690254, 7833856, 16690254, 16755790, 16755790, 16690254, 7833856, 16690254, 0, 0, 0, 0, 0, 0, 0, 16624718, 16690254, 7833856, 16690254, 16755790, 16755790, 16690254, 7833856, 16690254, 16624718, 0, 0, 0, 0, 0, 0, 16624718, 16690254, 16690254, 16690254, 16755790, 16755790, 16690254, 16690254, 16690254, 16624718, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16690254, 16690254, 16690254, 16690254, 16690254, 16690254, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 14959671, 7833856, 7833856, 14959671, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 14959671, 7833856, 7833856, 14959671, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 14959671, 14959671, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 16690254, 16690254, 7833856, 14959671, 16690254, 14959671, 14959671, 16690254, 14959671, 7833856, 16690254, 16690254, 0, 0, 0, 0, 16690254, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 16690254, 16690254, 16690254, 0, 0, 0, 0, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 16690254, 16690254, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 0, 0, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 0, 0}, {0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 16624718, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 16624718, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16624718, 0, 0, 0, 0, 0, 0, 0, 16690254, 7833856, 7833856, 7833856, 7833856, 7833856, 7833856, 16690254, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16690254, 16690254, 16690254, 16690254, 16690254, 16690254, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 14959671, 7833856, 7833856, 14959671, 7833856, 7833856, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 14959671, 7833856, 7833856, 14959671, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 14959671, 14959671, 14959671, 14959671, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 16690254, 16690254, 7833856, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 7833856, 16690254, 16690254, 0, 0, 0, 0, 16690254, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 16690254, 16690254, 16690254, 0, 0, 0, 0, 16690254, 16690254, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 14959671, 16690254, 16690254, 0, 0, 0, 0, 0, 0, 14959671, 14959671, 14959671, 0, 0, 14959671, 14959671, 14959671, 0, 0, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 7833856, 7833856, 7833856, 0, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 0, 0, 0, 0, 7833856, 7833856, 7833856, 7833856, 0, 0}};
////        var o = ShapeCarver.carve(new int[]{16, 16, 16}, views, 0, new boolean[]{false, false, false, false, false, false});
////        Greedy.Mesh mesh = Greedy.greedyMesh(o.volume(), o.dims());
////        System.out.println(mesh);
////        Group g = create3DContent(o);
////        Group g = new Group();
////        var view = new CameraView(new SubScene(g, 160, 160));
////        view.setTranslateZ(-70);
////        var g2 = new Group(view);
////        Scene scene = new Scene(g2, 160, 160);
//        // add the mesh to the scene
////        g.getChildren().add(convertMeshToJavaFX(mesh));
//
////        System.out.println(create3DContentAsFXML(o));
////        // scale group 10x
////        g.setScaleX(10);
////        g.setScaleY(10);
////        g.setScaleZ(10);
////        g.setTranslateX(80);
////        g.setTranslateY(80);
////        g.setTranslateZ(80);
////        scene.setRoot(g);
//
//        var box = new Box(100, 100, 100);
//        box.setMaterial(new PhongMaterial(Color.RED));
//
//        var c = new PerspectiveCamera();
//        c.setNearClip(0.1);
//        c.setFarClip(15000.0);
//
//        c.setTranslateZ(-1500);
//        var scene = new Scene(new Group(box), 640, 480);
//        scene.setCamera(c);
//
////        var g = new Group();
////        var subScene = new SubScene(g, 640, 480);
////        var c = new CameraView(subScene);
////        g.getChildren().add(box);
////
////        var scene = new Scene(new Group(c), 640, 480);
//
//        stage.setTitle("Hello!");
//        stage.setScene(scene);
//        stage.show();
//    }


    public MeshView convertMeshToJavaFX(Greedy.Mesh mesh) {
        TriangleMesh triangleMesh = new TriangleMesh();

        // Add vertices to the TriangleMesh
        for (var vertex : mesh.vertices()) {
            triangleMesh.getPoints().addAll(vertex.get(0), vertex.get(1), vertex.get(2));
        }

        triangleMesh.getTexCoords().addAll(0.0f, 0.0f);

        // Add faces to the TriangleMesh
        for (var face : mesh.faces()) {
            triangleMesh.getFaces().addAll(face.get(0), 0, face.get(1), 0, face.get(2), 0);
            if (face.size() == 5) { // if the face is a quadrilateral
                triangleMesh.getFaces().addAll(face.get(2), 0, face.get(3), 0, face.get(0), 0);
            } else {
                throw new AssertionError("Face is not a quadrilateral");
            }
        }

        // Create a MeshView to display the TriangleMesh
        MeshView meshView = new MeshView(triangleMesh);

        // Set the MeshView's material to a PhongMaterial with the color of the face
        for (var face : mesh.faces()) {
            PhongMaterial material = new PhongMaterial(Color.rgb((face.get(4) >> 16) & 0xFF, (face.get(4) >> 8) & 0xFF, face.get(4) & 0xFF));
            meshView.setMaterial(material);
        }
        PhongMaterial blueMaterial = new PhongMaterial();
        blueMaterial.setDiffuseColor(Color.BLUE);
        meshView.setMaterial(blueMaterial);

        return meshView;
    }

    // serialize3DContent serializes the 3D content to FXML
    public static String create3DContentAsFXML(Output o) {
    StringBuilder fxml = new StringBuilder();
    var volume = o.volume();
    var dims = o.dims();
    var pos = new int[3]; // x, y, z
    for (pos[2] = 0; pos[2] < dims[2]; pos[2]++) {
        for (pos[1] = 0; pos[1] < dims[1]; pos[1]++) {
            for (pos[0] = 0; pos[0] < dims[0]; pos[0]++) {
                var color = volume[pos[0] + dims[0] * (pos[1] + dims[1] * pos[2])];
                if (color != 0) {
                    fxml.append("<Box depth=\"1.0\" height=\"1.0\" width=\"1.0\" translateX=\"")
                        .append(pos[0])
                        .append("\" translateY=\"")
                        .append(pos[1])
                        .append("\" translateZ=\"")
                        .append(pos[2])
                        .append("\">\n")
                        .append("<material>\n")
                        .append("<PhongMaterial diffuseColor=\"#")
                        .append(String.format("%06X", (0xFFFFFF & color)))
                        .append("\"/>\n")
                        .append("</material>\n")
                        .append("</Box>\n");
                }
            }
        }
    }
    return fxml.toString();
}

    public static Group create3DContent(Output o) {
        var g = new Group();
        var volume = o.volume();
        var dims = o.dims();
        var pos = new int[3]; // x, y, z
        for (pos[2] = 0; pos[2] < dims[2]; pos[2]++) {
            for (pos[1] = 0; pos[1] < dims[1]; pos[1]++) {
                for (pos[0] = 0; pos[0] < dims[0]; pos[0]++) {
                    var color = volume[pos[0] + dims[0] * (pos[1] + dims[1] * pos[2])];
                    if (color != 0) {
                        var box = new Box(1, 1, 1);
                        box.setTranslateX(pos[0]);
                        box.setTranslateY(pos[1]);
                        box.setTranslateZ(pos[2]);
                        box.setMaterial(new PhongMaterial(Color.rgb((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)));
                        g.getChildren().add(box);
                    }
                }
            }
        }

        return g;
    }

    public static void main(String[] args) {
        launch();
    }
}
