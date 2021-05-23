package com.tek.mask_detection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.softbankrobotics.facemaskdetection.capturer.CameraCapturer
import com.softbankrobotics.facemaskdetection.capturer.YuvToRgbConverter
import java.util.concurrent.Executors

class MyCapturer(
    private val androidContext: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val preview: Preview,
    private val facing: Int
) : CameraCapturer {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var bitmapBuffer: Bitmap
    private lateinit var camera: Camera

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    override fun start(onPictureCb: (Bitmap, Long) -> Unit): Future<Unit> {
        val promise = Promise<Unit>()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(androidContext)
        cameraProviderFuture.addListener({


            val cameraProvider = cameraProviderFuture.get()
            val hasFrontCam = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            Log.e("face", "has camera $hasFrontCam")

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val converter = YuvToRgbConverter(androidContext)

            imageAnalysis.setAnalyzer(executor, { image ->
                if (!::bitmapBuffer.isInitialized) {

                    val imageRotationDegrees = image.imageInfo.rotationDegrees
                    Log.e("face", "imageRotationDegrees  $imageRotationDegrees")
                    bitmapBuffer = Bitmap.createBitmap(
                        image.width, image.height, Bitmap.Config.ARGB_8888
                    )

                }
                val imageTime = System.currentTimeMillis()


                image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }
                onPictureCb(bitmapBuffer, imageTime)
            })

            promise.setOnCancel {
                imageAnalysis.clearAnalyzer()
                executor.shutdown()
                cameraProvider.unbindAll()
            }
            val cameraSelector = CameraSelector.Builder().requireLensFacing(facing).build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )


            } catch (ex: Exception) {
                Log.e("face", "Use case binding failed", ex)
            }

        }, ContextCompat.getMainExecutor(androidContext))

        return promise.future
    }

    fun torchMode(boolean: Boolean) {
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(boolean)
        }
    }

}