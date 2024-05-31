package org.example.shapecarvejavafx

import javafx.application.Application
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.*
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.image.WritablePixelFormat
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Background
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.paint.PhongMaterial
import javafx.scene.shape.Box
import javafx.stage.Stage
import java.util.*

class HelloApplication : Application() {
    private var mouseDeltaX = 0.0
    private var mouseDeltaY = 0.0
    private var mouseOldX = 0.0
    private var mouseOldY = 0.0
    private var mousePosX = 0.0
    private var mousePosY = 0.0

    override fun start(stage: Stage) {
        val sceneWidth = 800.0
        val sceneHeight = 600.0
        val cameraTransform = Xform()

        val sceneRoot = Group()
        val scene = SubScene(sceneRoot, sceneWidth, sceneHeight, true, SceneAntialiasing.BALANCED)
        scene.fill = Color.ALICEBLUE
        val camera = PerspectiveCamera(true)

        //setup camera transform for rotational support
        cameraTransform.setTranslate(0.0, 0.0, 0.0)
        cameraTransform.children.add(camera)
        camera.nearClip = 0.1
        camera.farClip = 10000.0
        camera.translateZ = -70.0
        camera.translateX = 10.0
        cameraTransform.ry.angle = -35.0
        cameraTransform.rx.angle = -10.0
        val light = PointLight(Color.WHITE)
        cameraTransform.children.add(light)
        light.translateX = camera.translateX
        light.translateY = camera.translateY
        light.translateZ = camera.translateZ
        scene.camera = camera

        val views = views

        val group = groupOfBoxes(intArrayOf(16, 16, 16))

        group.children.add(cameraTransform)


        sceneRoot.children.addAll(group)

        setUpEvents(scene, camera, cameraTransform)

        val carver = ShapeCarver()
        val output = carver.carve(views, 0, booleanArrayOf(false, false, false, false, false, false))
        val coroutine = ContentCoroutine(output, group)

        val rootScene = setUpRootScene(scene, coroutine)
        stage.scene = rootScene
        stage.show()
    }

    private fun groupOfBoxes(_dims: IntArray): Group {
        Objects.requireNonNull(_dims)
        val dims = Arrays.stream(_dims).boxed().toList()

        val g = Group()
        val pos = IntArray(3) // x, y, z
        pos[2] = 0
        while (pos[2] < dims[2]) {
            pos[1] = 0
            while (pos[1] < dims[1]) {
                pos[0] = 0
                while (pos[0] < dims[0]) {
                    val box = Box(1.0, 1.0, 1.0)
                    box.translateX = pos[0].toDouble()
                    box.translateY = pos[1].toDouble()
                    box.translateZ = pos[2].toDouble()
                    box.material = PhongMaterial(Color.ANTIQUEWHITE)
                    g.children.add(box)
                    pos[0]++
                }
                pos[1]++
            }
            pos[2]++
        }
        return g
    }

    private fun setUpEvents(scene: SubScene, camera: PerspectiveCamera, cameraTransform: Xform) {
        Objects.requireNonNull(scene)
        Objects.requireNonNull(camera)
        Objects.requireNonNull(cameraTransform)

        //First person shooter keyboard movement
        scene.onKeyPressed = EventHandler { event: KeyEvent ->
            var change = 10.0
            //Add shift modifier to simulate "Running Speed"
            if (event.isShiftDown) {
                change = 50.0
            }
            //What key did the user press?
            val keycode = event.code
            //Step 2c: Add Zoom controls
            if (keycode == KeyCode.W) {
                camera.translateZ = camera.translateZ + change
            }
            if (keycode == KeyCode.S) {
                camera.translateZ = camera.translateZ - change
            }
            //Step 2d:  Add Strafe controls
            if (keycode == KeyCode.A) {
                camera.translateX = camera.translateX - change
            }
            if (keycode == KeyCode.D) {
                camera.translateX = camera.translateX + change
            }
        }

        scene.onMousePressed = EventHandler { me: MouseEvent ->
            mousePosX = me.sceneX
            mousePosY = me.sceneY
            mouseOldX = me.sceneX
            mouseOldY = me.sceneY
        }
        scene.onMouseDragged = EventHandler { me: MouseEvent ->
            mouseOldX = mousePosX
            mouseOldY = mousePosY
            mousePosX = me.sceneX
            mousePosY = me.sceneY
            mouseDeltaX = (mousePosX - mouseOldX)
            mouseDeltaY = (mousePosY - mouseOldY)

            var modifier = 10.0
            val modifierFactor = 0.1

            if (me.isControlDown) {
                modifier = 0.1
            }
            if (me.isShiftDown) {
                modifier = 50.0
            }
            if (me.isPrimaryButtonDown) {
                cameraTransform.ry.angle =
                    ((cameraTransform.ry.angle + mouseDeltaX * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180 // +
                cameraTransform.rx.angle =
                    ((cameraTransform.rx.angle - mouseDeltaY * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180 // -
            } else if (me.isSecondaryButtonDown) {
                val z = camera.translateZ
                val newZ = z + mouseDeltaX * modifierFactor * modifier
                camera.translateZ = newZ
            } else if (me.isMiddleButtonDown) {
                cameraTransform.t.x = cameraTransform.t.x + mouseDeltaX * modifierFactor * modifier * 0.3 // -
                cameraTransform.t.y = cameraTransform.t.y + mouseDeltaY * modifierFactor * modifier * 0.3 // -
            }
        }
    }

    companion object {
        private fun setUpRootScene(scene: SubScene, coroutine: ContentCoroutine): Scene {
            Objects.requireNonNull(scene)
            val sp = StackPane()
            sp.setPrefSize(800.0, 600.0)
            sp.setMaxSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE)
            sp.setMinSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE)
            sp.background = Background.EMPTY
            sp.children.add(scene)
            val button = Button("Next")
            button.translateX = 100.0
            button.translateY = 100.0
            button.onAction = EventHandler { `_`: ActionEvent? -> iterate(coroutine) }
            sp.children.add(button)
            sp.isPickOnBounds = false

            scene.widthProperty().bind(sp.widthProperty())
            scene.heightProperty().bind(sp.heightProperty())

            return Scene(sp)
        }

        private fun iterate(coroutine: ContentCoroutine?) {
            if (coroutine == null) {
                return
            }
            if (!coroutine.isComplete) {
                coroutine.next() // This will run the computation for one slice of z and then return
                // You can add other logic here if needed between updates, such as UI refreshes or pauses
            }
        }

        private val views: List<List<Int>>
            get() {
                val views = Array(6) { IntArray(256) }
                val img = Image("file:///Users/rieckenj/Pictures/cross.png")
                val reader = img.pixelReader
                val width = img.width.toInt()
                val height = img.height.toInt()
                val pixels = IntArray(width * height)
                reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width)
                for (i in pixels.indices) {
                    pixels[i] = pixels[i] and 0x00ffffff // discard transparency value
                }
                views[0] = pixels
                views[1] = pixels
                views[2] = pixels
                views[3] = pixels
                views[4] = pixels
                views[5] = pixels
                return Arrays.stream(views).map { e: IntArray? -> Arrays.stream(e).boxed().toList() }
                    .toList()
            }

        fun create3DContent(o: Output, g: Group) {
            Objects.requireNonNull(o)
            Objects.requireNonNull(g)

            val volume = o.volume()
            val dims = o.dims()
            val pos = IntArray(3) // x, y, z
            pos[2] = 0
            while (pos[2] < dims[2]) {
                pos[1] = 0
                while (pos[1] < dims[1]) {
                    pos[0] = 0
                    while (pos[0] < dims[0]) {
                        val color = volume[pos[0] + dims[0] * (pos[1] + dims[1] * pos[2])]
                        val box = g.children[pos[0] + dims[0] * (pos[1] + dims[1] * pos[2])] as Box
                        if (color != 0) {
                            box.material = PhongMaterial(
                                Color.rgb(
                                    (color shr 16) and 0xFF,
                                    color shr 8 and 0xFF,
                                    color and 0xFF
                                )
                            )
                        } else {
                            box.isVisible = false
                        }
                        pos[0]++
                    }
                    pos[1]++
                }
                pos[2]++
            }
        }
   }
}
fun main() {
    Application.launch(HelloApplication::class.java)
}