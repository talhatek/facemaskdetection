package com.tek.mask_detection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import com.softbankrobotics.facemaskdetection.FaceMaskDetection
import com.softbankrobotics.facemaskdetection.detector.FaceMaskDetector
import com.softbankrobotics.facemaskdetection.utils.OpenCVUtils
import com.tek.mask_detection.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var facing = CameraSelector.LENS_FACING_FRONT
    private lateinit var myCapturer: MyCapturer
    private lateinit var checkCameraPermission: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        OpenCVUtils.loadOpenCV(this)
        checkCameraPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    startCamera()
                } else {
                    finish()
                    exitProcess(0)
                }
            }
        requestedOrientation = if (deviceIsTablet()) {

            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        }
        savedInstanceState?.getInt("facing", 0)?.let {
            facing = it
        }

        binding.fabSwitchFacing.setOnClickListener {
            facing =
                if (facing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            recreate()
        }
        checkCameraPermission.launch(Manifest.permission.CAMERA)


    }

    private fun startCamera() {
        val preview = Preview.Builder().build().also { preview ->
            preview.setSurfaceProvider(binding.testPreview.surfaceProvider)
        }
        val detector = MyAizooFaceMaskDetector(this, deviceIsTablet(), facing)
        myCapturer = MyCapturer(this, this, preview, facing)
        val detection = FaceMaskDetection(detector, myCapturer)

        detection.start { faces ->

            val filteredFaces = faces.filter { it.confidence >= 0.6 }
            Log.e("facesCount", filteredFaces.size.toString())
            when (filteredFaces.size) {
                0 -> {
                    setNoOneVisible()

                }
                1 -> {
                    oneFaceFound(filteredFaces.first())
                }
                else -> {
                    oneFaceFound(findWhoIsAtMiddle(filteredFaces))
                }
            }


        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("facing", facing)
        super.onSaveInstanceState(outState)

    }


    private fun oneFaceFound(face: FaceMaskDetector.DetectedFace) {

        if (face.hasMask) {
            setMasked()
        } else {
            setUnMasked()
        }


    }

    private fun findWhoIsAtMiddle(filteredList: List<FaceMaskDetector.DetectedFace>): FaceMaskDetector.DetectedFace {
        val tmp = mutableListOf<Double>()

        filteredList.forEach { detectedFace ->
            tmp.add(((detectedFace.bb.right - ((detectedFace.bb.right - detectedFace.bb.left) / 2)) / 639.0))
        }
        tmp.apply {
            return filteredList[this.indexOf(this.closestValue(0.5))]
        }


    }


    @SuppressLint("SetTextI18n")
    fun setMasked() {
        runOnUiThread {
            binding.maskText.text = "YOU HAVE A MASK !"
            binding.maskText.setTextColor(Color.parseColor("#ff99cc00"))
            binding.maskText.visibility = View.VISIBLE

        }

    }

    @SuppressLint("SetTextI18n")
    fun setNoOneVisible() {
        runOnUiThread {

            binding.maskText.visibility = View.INVISIBLE

        }

    }

    @SuppressLint("SetTextI18n")
    fun setUnMasked() {
        runOnUiThread {
            binding.maskText.text = "YOU DO NOT HAVE A MASK !"
            binding.maskText.setTextColor(Color.parseColor("#ffcc0000"))
            binding.maskText.visibility = View.VISIBLE

        }

    }

    private fun deviceIsTablet(): Boolean {
        return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

    }


    private fun MutableList<Double>.closestValue(value: Double) = minByOrNull { abs(value - it) }
}