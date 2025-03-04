package com.example.gyroscopeballgame

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

// Maze data and wall definitions (designed with a fixed size of 1000 x 1500 units)
data class Maze(
    val mazeWidth: Float,
    val mazeHeight: Float,
    val walls: List<Wall>,
    val entrance: Offset,
    val exit: Offset
)

data class Wall(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

// Sample maze with a fixed outer wall thickness of 20 units
val sampleMaze = Maze(
    mazeWidth = 1000f,
    mazeHeight = 1500f,
    walls = listOf(
        // Outer boundary walls (20-unit thick walls)
        Wall(0f, 0f, 1000f, 20f),         // Top border
        Wall(0f, 1480f, 1000f, 20f),      // Bottom border
        Wall(0f, 0f, 20f, 1500f),         // Left border
        Wall(980f, 0f, 20f, 1500f),       // Right border

        // Section 1: Upper-left area
        Wall(100f, 100f, 200f, 20f),      // Horizontal wall
        Wall(100f, 100f, 20f, 300f),      // Vertical wall
        Wall(280f, 100f, 20f, 200f),      // Vertical wall

        // Section 2: Upper-middle area
        Wall(350f, 100f, 20f, 400f),      // Vertical wall
        Wall(350f, 400f, 50f, 20f),       // Horizontal passage
        Wall(450f, 400f, 50f, 20f),
        Wall(500f, 100f, 20f, 400f),      // Vertical wall

        // Section 3: Central area
        Wall(100f, 500f, 200f, 20f),      // Upper horizontal wall
        Wall(100f, 500f, 20f, 300f),      // Left vertical wall
        Wall(350f, 600f, 20f, 150f),      // Right vertical wall
        Wall(100f, 800f, 300f, 20f),      // Lower horizontal wall
        Wall(150f, 650f, 20f, 100f),      // Inner small wall (increased complexity)
        Wall(150f, 650f, 200f, 20f),

        // Section 4: Lower-left area
        Wall(100f, 900f, 20f, 300f),      // Vertical wall
        Wall(100f, 900f, 200f, 20f),      // Horizontal wall

        // Section 5: Lower-middle area
        Wall(350f, 900f, 20f, 400f),      // Vertical wall
        Wall(350f, 1100f, 150f, 20f),     // Horizontal wall

        // Section 6: Upper-right area
        Wall(600f, 100f, 20f, 400f),      // Vertical wall
        Wall(600f, 100f, 75f, 20f),       // Horizontal wall
        Wall(725f, 100f, 55f, 20f),       // Horizontal wall
        Wall(780f, 100f, 20f, 400f),      // Vertical wall

        // Section 7: Right side (increased complexity)
        Wall(600f, 500f, 200f, 20f),      // Horizontal wall
        Wall(780f, 500f, 20f, 300f),      // Vertical wall
        Wall(600f, 800f, 200f, 20f),      // Horizontal wall
        Wall(600f, 800f, 20f, 200f),      // Vertical wall
        Wall(780f, 800f, 20f, 200f),       // Vertical wall
        Wall(600f, 600f, 150f, 20f),
        Wall(600f, 600f, 20f, 150f),
        Wall(730f, 600f, 20f, 150f),
        Wall(600f, 750f, 150f, 20f),
        Wall(400f, 1200f, 450f, 20f),



        ),
    entrance = Offset(30f, 30f),         // Entrance at the top-left corner
    exit = Offset(950f, 1450f)           // Exit at the bottom-right corner
)



// Improved collision detection: Constrain movement to a valid area, then attempt horizontal/vertical adjustments
fun checkMazeCollision(oldPos: Offset, newPos: Offset, radius: Float, maze: Maze): Offset {
    val minX = 20f + radius
    val maxX = maze.mazeWidth - 20f - radius
    val minY = 20f + radius
    val maxY = maze.mazeHeight - 20f - radius
    val clamped = Offset(newPos.x.coerceIn(minX, maxX), newPos.y.coerceIn(minY, maxY))

    // Define a collision check function
    fun collides(pos: Offset): Boolean {
        val left = pos.x - radius
        val right = pos.x + radius
        val top = pos.y - radius
        val bottom = pos.y + radius
        return maze.walls.any { wall ->
            val wallLeft = wall.x
            val wallRight = wall.x + wall.width
            val wallTop = wall.y
            val wallBottom = wall.y + wall.height
            right > wallLeft && left < wallRight && bottom > wallTop && top < wallBottom
        }
    }

    // If no collision, return the clamped position
    if (!collides(clamped)) return clamped

    // Horizontal and vertical adjustments
    val horizontalPos = Offset(clamped.x, oldPos.y)
    val verticalPos = Offset(oldPos.x, clamped.y)
    val horizontalCollides = collides(horizontalPos)
    val verticalCollides = collides(verticalPos)

    return when {
        !horizontalCollides -> horizontalPos
        !verticalCollides -> verticalPos
        else -> oldPos
    }
}

// Extension function to calculate the distance between two points
fun Offset.distanceTo(other: Offset): Float {
    return sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))
}

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null

    private var pitch by mutableStateOf(0f)
    private var roll by mutableStateOf(0f)
    private var lastTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MazeGameScreen(pitch = pitch, roll = roll)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val timestamp = it.timestamp
                if (lastTimestamp != 0L) {
                    val dt = (timestamp - lastTimestamp) * 1e-9f
                    val sensitivityFactor = 3f  // Change this value to adjust sensitivity
                    pitch += it.values[0] * sensitivityFactor * dt
                    roll += it.values[1] * sensitivityFactor * dt
                }
                lastTimestamp = timestamp
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No handling needed
    }
}

@Composable
fun MazeGameScreen(pitch: Float, roll: Float) {
    // Use BoxWithConstraints to get available dimensions and scale the maze proportionally
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()

        // Calculate scaling factor and centering offset based on design dimensions (1000 x 1500)
        val scaleFactor = min(canvasWidthPx / sampleMaze.mazeWidth, canvasHeightPx / sampleMaze.mazeHeight)
        val offsetX = (canvasWidthPx - sampleMaze.mazeWidth * scaleFactor) / 2f
        val offsetY = (canvasHeightPx - sampleMaze.mazeHeight * scaleFactor) / 2f

        // Ball design radius and movement speed (maze coordinate system)
        val ballRadius = 15f
        val speedFactor = 300f

        // Ball position, initially placed at the entrance
        var ballPos by remember { mutableStateOf(sampleMaze.entrance) }
        // Whether the player has reached the exit
        var win by remember { mutableStateOf(false) }

        val currentPitch by rememberUpdatedState(newValue = pitch)
        val currentRoll by rememberUpdatedState(newValue = roll)

        // Game update loop using LaunchedEffect to start an infinite loop
        LaunchedEffect(Unit) {
            while (true) {
                if (!win) {
                    val dt = 0.016f  // 60 FPS
                    // Calculate ball movement; adjust direction signs based on actual gameplay feel
                    val delta = Offset(currentRoll * speedFactor * dt, currentPitch * speedFactor * dt)
                    val newPos = ballPos + delta
                    ballPos = checkMazeCollision(ballPos, newPos, ballRadius, sampleMaze)
                    // Check if the exit has been reached (using distance check)
                    if (ballPos.distanceTo(sampleMaze.exit) < ballRadius * 1.5f) {
                        win = true
                        delay(2000)
                        ballPos = sampleMaze.entrance
                        win = false
                    }
                }
                delay(16L)
            }
        }

        // Draw the maze and ball
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw walls
            sampleMaze.walls.forEach { wall ->
                drawRect(
                    color = Color.DarkGray,
                    topLeft = Offset(wall.x * scaleFactor + offsetX, wall.y * scaleFactor + offsetY),
                    size = Size(wall.width * scaleFactor, wall.height * scaleFactor)
                )
            }
            // Draw entrance (green) and exit (blue) markers
            drawRect(
                color = Color.Green,
                topLeft = Offset(sampleMaze.entrance.x * scaleFactor + offsetX, sampleMaze.entrance.y * scaleFactor + offsetY),
                size = Size(30f * scaleFactor, 30f * scaleFactor)
            )
            drawRect(
                color = Color.Blue,
                topLeft = Offset(sampleMaze.exit.x * scaleFactor + offsetX, sampleMaze.exit.y * scaleFactor + offsetY),
                size = Size(30f * scaleFactor, 30f * scaleFactor)
            )
            // Draw the ball
            drawCircle(
                color = Color.Red,
                radius = ballRadius * scaleFactor,
                center = Offset(ballPos.x * scaleFactor + offsetX, ballPos.y * scaleFactor + offsetY)
            )
            // Display "WIN" message
            if (win) {
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "WIN!",
                        canvasWidthPx / 2,
                        canvasHeightPx / 2,
                        android.graphics.Paint().apply {
                            textSize = 64f
                            color = android.graphics.Color.MAGENTA
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MazeGamePreview() {
    MazeGameScreen(pitch = 0.0f, roll = 0.0f)
}