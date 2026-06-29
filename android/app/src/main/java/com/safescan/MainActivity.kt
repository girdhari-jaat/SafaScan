package com.safescan
import android.Manifest, android.content.pm.PackageManager, android.os.Bundle, android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat, ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import android.content.Context, android.graphics.Canvas, Color, Paint, Path, PointF
import android.util.AttributeSet, android.view.View

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private val executor = Executors.newSingleThreadExecutor()
    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        if (!OpenCVLoader.initLocal()) { Toast.makeText(this, "OpenCV init failed!", Toast.LENGTH_LONG).show(); return }
        if (allPermissionsGranted()) startCamera() else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            imageAnalyzer.setAnalyzer(executor, DocumentAnalyzer { points -> runOnUiThread { overlayView.setPoints(points) } })
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageToMat(image: ImageProxy): Mat {
        val yBuffer: ByteBuffer = image.planes[0].buffer; val uBuffer: ByteBuffer = image.planes[1].buffer; val vBuffer: ByteBuffer = image.planes[2].buffer
        val nv21 = ByteArray(yBuffer.remaining() + uBuffer.remaining() + vBuffer.remaining())
        yBuffer.get(nv21, 0, yBuffer.remaining()); vBuffer.get(nv21, yBuffer.remaining(), vBuffer.remaining()); uBuffer.get(nv21, yBuffer.remaining() + vBuffer.remaining(), uBuffer.remaining())
        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1); yuvMat.put(0, 0, nv21)
        val bgrMat = Mat(); Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21, 3); yuvMat.release(); return bgrMat
    }

    private inner class DocumentAnalyzer(private val listener: (List<PointF>) -> Unit) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val mat = imageToMat(imageProxy)
            val docPoints = detectDocument(mat)?.map { PointF(it.x.toFloat(), it.y.toFloat()) }
            listener(docPoints?: emptyList()); mat.release(); imageProxy.close()
        }
    }

    private fun detectDocument(mat: Mat): List<org.opencv.core.Point>? {
        val gray = Mat(); val blurred = Mat(); val edges = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 75.0, 200.0)
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val biggest = contours.maxByOrNull { Imgproc.contourArea(it) }?: return null
        val approx = MatOfPoint2f(); Imgproc.approxPolyDP(MatOfPoint2f(*biggest.toArray()), approx, 0.02 * Imgproc.arcLength(MatOfPoint2f(*biggest.toArray()), true), true)
        return if (approx.toArray().size == 4) approx.toList() else null
    }
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

// YE CLASS BHI ISI FILE KE SAB SE END ME HAI
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { color = Color.GREEN; strokeWidth = 8f; style = Paint.Style.STROKE }
    private var points: List<PointF> = emptyList()
    fun setPoints(newPoints: List<PointF>) { points = newPoints; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size == 4) {
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            canvas.drawPath(path, paint)
        }
    }
}