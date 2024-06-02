package org.example.shapecarvejavafx

import javafx.application.Application
import javafx.beans.property.SimpleIntegerProperty
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.fxyz3d.utils.CameraTransformer

class HelloApplication : Application() {
    private var mouseDeltaX = 0.0
    private var mouseDeltaY = 0.0
    private var mouseOldX = 0.0
    private var mouseOldY = 0.0
    private var mousePosX = 0.0
    private var mousePosY = 0.0

    private lateinit var carver: ShapeCarver

    override fun start(stage: Stage) {
        val sceneWidth = 800.0
        val sceneHeight = 600.0
        val cameraTransform = CameraTransformer()

        val sceneRoot = Group()
        val scene = SubScene(sceneRoot, sceneWidth, sceneHeight, true, SceneAntialiasing.BALANCED).apply {
            fill = Color.ALICEBLUE
        }
        val camera = PerspectiveCamera(true).apply {
            nearClip = 0.1
            farClip = 10000.0
            translateZ = -70.0
            translateX = 10.0
        }

        // Setup camera transform for rotational support
        cameraTransform.setTranslate(0.0, 0.0, 0.0)
        cameraTransform.children.add(camera)
        cameraTransform.ry.angle = -35.0
        cameraTransform.rx.angle = -10.0
        val light = PointLight(Color.WHITE).apply {
            translateX = camera.translateX
            translateY = camera.translateY
            translateZ = camera.translateZ
        }
        cameraTransform.children.add(light)
        scene.camera = camera

        val group = groupOfBoxes(intArrayOf(16, 16, 16)).apply {
            children.add(cameraTransform)
        }

        sceneRoot.children.addAll(group)

        setUpEvents(scene, camera, cameraTransform)

        val dims: IntArray = intArrayOf(16, 16, 16) // cuboid shape
        val volume: MutableList<SimpleIntegerProperty> = MutableList(dims[0] * dims[1] * dims[2]) { _ -> SimpleIntegerProperty(-1) }
        val output = Output(volume, dims)
        carver = ShapeCarver(output)
        processSlices(dims, volume, group)

        val rootScene = setUpRootScene(scene)
        stage.scene = rootScene
        stage.show()
    }

    private fun processSlices(dims: IntArray, volume: MutableList<SimpleIntegerProperty>, group: Group) {
        for (z in 0 until dims[2]) {
            for (y in 0 until dims[1]) {
                for (x in 0 until dims[0]) {
                    val index = x + dims[0] * (y + dims[1] * z)
                    val color = volume[index]
                    val box = group.children[index] as Box
                    color.addListener { _, _, newValue ->
                        run {
                            box.isVisible = newValue != 0
                            box.material = PhongMaterial(
                                Color.rgb(
                                    (color.get() shr 16) and 0xFF,
                                    (color.get() shr 8) and 0xFF,
                                    color.get() and 0xFF
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun groupOfBoxes(dims: IntArray): Group {
        require(dims.isNotEmpty())
        val g = Group()
        val pos = IntArray(3) // x, y, z

        repeat(dims[2]) { z ->
            pos[2] = z
            repeat(dims[1]) { y ->
                pos[1] = y
                repeat(dims[0]) { x ->
                    pos[0] = x
                    val box = Box(1.0, 1.0, 1.0).apply {
                        translateX = x.toDouble()
                        translateY = y.toDouble()
                        translateZ = z.toDouble()
                        material = PhongMaterial(Color.ANTIQUEWHITE)
                    }
                    g.children.add(box)
                }
            }
        }
        return g
    }

    private fun setUpEvents(scene: SubScene, camera: PerspectiveCamera, cameraTransform: CameraTransformer) {
        // First person shooter keyboard movement
        scene.setOnKeyPressed { event: KeyEvent ->
            var change = 10.0
            // Add shift modifier to simulate "Running Speed"
            if (event.isShiftDown) {
                change = 50.0
            }
            // What key did the user press?
            when (event.code) {
                KeyCode.W -> camera.translateZ += change
                KeyCode.S -> camera.translateZ -= change
                KeyCode.A -> camera.translateX -= change
                KeyCode.D -> camera.translateX += change
                else -> {}
            }
        }

        scene.setOnMousePressed { me: MouseEvent ->
            mousePosX = me.sceneX
            mousePosY = me.sceneY
            mouseOldX = me.sceneX
            mouseOldY = me.sceneY
        }

        scene.setOnMouseDragged { me: MouseEvent ->
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
            when {
                me.isPrimaryButtonDown -> {
                    cameraTransform.ry.angle =
                        ((cameraTransform.ry.angle + mouseDeltaX * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180
                    cameraTransform.rx.angle =
                        ((cameraTransform.rx.angle - mouseDeltaY * modifierFactor * modifier * 2.0) % 360 + 540) % 360 - 180
                }
                me.isSecondaryButtonDown -> {
                    val newZ = camera.translateZ + mouseDeltaX * modifierFactor * modifier
                    camera.translateZ = newZ
                }
                me.isMiddleButtonDown -> {
                    cameraTransform.t.x += mouseDeltaX * modifierFactor * modifier * 0.3
                    cameraTransform.t.y += mouseDeltaY * modifierFactor * modifier * 0.3
                }
            }
        }
    }

    private fun getViews(): List<List<Int>> {
        val urls = parameters.raw
        return urls.map { url ->
            getView(url).toList()
        }
    }

    private fun getView(url: String): IntArray {
        val img = Image(url)
        val reader = img.pixelReader
        val width = img.width.toInt()
        val height = img.height.toInt()
        val pixels = IntArray(width * height)
        reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width)
        for (i in pixels.indices) {
            pixels[i] = pixels[i] and 0x00ffffff // discard transparency value
        }
        return pixels
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setUpRootScene(scene: SubScene): Scene {
        val sp = StackPane().apply {
            prefWidth = 800.0
            prefHeight = 600.0
            maxWidth = StackPane.USE_COMPUTED_SIZE
            maxHeight = StackPane.USE_COMPUTED_SIZE
            minWidth = StackPane.USE_COMPUTED_SIZE
            minHeight = StackPane.USE_COMPUTED_SIZE
            background = Background.EMPTY
            children.add(scene)
            isPickOnBounds = false
        }
        val views = getViews()
        CoroutineScope(Dispatchers.Main).launch {
            carver.carve(views, 0, booleanArrayOf(false, false, false, false, false, false))
        }
        val button = Button("Next").apply {
            translateX = 100.0
            translateY = 100.0
            setOnAction {
                CoroutineScope(Dispatchers.Main).launch {
                    if (!carver.channel.isClosedForReceive) {
                        carver.channel.receive()
                    } else {
                        println("Carving complete")
                    }
                }
            }
        }
        sp.children.add(button)
        scene.widthProperty().bind(sp.widthProperty())
        scene.heightProperty().bind(sp.heightProperty())

        return Scene(sp)
    }
}

fun main(vararg args: String) {
    Application.launch(HelloApplication::class.java, *args)
}
