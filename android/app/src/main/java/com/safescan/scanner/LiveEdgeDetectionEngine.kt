package com.safescan.scanner

import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.common.InputImage

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.common.InputImage
import com.safescan.android.scanner.Point

class LiveEdgeDetectionEngine {
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .build()
    
    private val detector = ObjectDetection.getClient(options)
    
    fun process(imageProxy: ImageProxy, onResult: (List<Point>) -> Unit) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        detector.process(image)
            .addOnSuccessListener { results ->
                val obj = results.firstOrNull()
                if (obj != null) {
                    val rect = obj.boundingBox
                    onResult(listOf(
                        Point(rect.left.toDouble(), rect.top.toDouble()),
                        Point(rect.right.toDouble(), rect.top.toDouble()),
                        Point(rect.right.toDouble(), rect.bottom.toDouble()),
                        Point(rect.left.toDouble(), rect.bottom.toDouble())
                    ))
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
