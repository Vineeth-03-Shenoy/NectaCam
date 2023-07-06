package com.example.nectacam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var captureButton: Button? = null
    private var galleryButton: Button? = null
    private var selectedItem: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        captureButton = findViewById(R.id.captureButton)
        galleryButton = findViewById(R.id.galleryButton)

        val enhancementSpinner: Spinner = findViewById(R.id.enhancementSpinner)
        enhancementSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedItem = parent?.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle case when no enhancement is selected
            }
        }

        surfaceHolder = surfaceView?.holder
        surfaceHolder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                openCamera()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                // No action needed
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                closeCamera()
            }
        })

        captureButton?.setOnClickListener(View.OnClickListener { capturePhoto() })
        galleryButton?.setOnClickListener(View.OnClickListener { openGallery() })

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager?.cameraIdList?.get(0)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST_CODE
            )
        } else {
            try {
                cameraManager?.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCameraCaptureSession()
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraDevice?.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        cameraDevice?.close()
                    }
                }, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun createCameraCaptureSession() {
        try {
            val characteristics = cameraManager?.getCameraCharacteristics(cameraId!!)
            val map =
                characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(SurfaceHolder::class.java)
            val previewSize = outputSizes?.get(0) // You can choose the desired size from the list

            imageReader = ImageReader.newInstance(
                previewSize?.width ?: 1,
                previewSize?.height ?: 1,
                ImageFormat.JPEG,
                1
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                selectedItem?.let { saveImageToGallery(image, it) }
                image?.close()
            }, null)

            val surface = surfaceHolder?.surface
            val imageSurface = imageReader?.surface

            cameraDevice?.createCaptureSession(
                listOfNotNull(surface, imageSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        startPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to configure camera capture session",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Log.e(TAG, "Error creating camera capture session: " + e.message)
        }
    }

    private fun startPreview() {
        try {
            val captureRequestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surface = surfaceHolder?.surface
            if (surface != null) {
                captureRequestBuilder?.addTarget(surface)
            }
            cameraCaptureSession?.setRepeatingRequest(
                captureRequestBuilder?.build()!!,
                null,
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun capturePhoto() {
        if (cameraDevice != null) {
            try {
                val captureRequestBuilder =
                    cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureRequestBuilder?.addTarget(imageReader?.surface!!)

                cameraCaptureSession?.capture(
                    captureRequestBuilder?.build()!!,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            // Image captured successfully
                        }

                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure
                        ) {
                            super.onCaptureFailed(session, request, failure)
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to capture image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    null
                )
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveImageToGallery(image: Image, selectedItem: String) {

        val buffer = enhanceImage(image, selectedItem)
        val bytes = ByteArray(buffer!!.remaining())
        buffer.get(bytes)

        val values = ContentValues()
        values.put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "IMG_" + System.currentTimeMillis() + ".jpg"
        )
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        val contentResolver = contentResolver
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
        var outputStream: OutputStream? = null
        try {
            outputStream = contentResolver.openOutputStream(uri!!)
            if (outputStream != null) {
                outputStream.write(bytes)
                Toast.makeText(
                    this@MainActivity,
                    "Image saved to gallery",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
        private val ORIENTATION_DEFAULT_VALUES = SparseIntArray()

        init {
            ORIENTATION_DEFAULT_VALUES.append(Surface.ROTATION_0, 90)
            ORIENTATION_DEFAULT_VALUES.append(Surface.ROTATION_90, 0)
            ORIENTATION_DEFAULT_VALUES.append(Surface.ROTATION_180, 270)
            ORIENTATION_DEFAULT_VALUES.append(Surface.ROTATION_270, 180)
        }
    }

    private fun enhanceImage(image: Image, selectedItem: String): ByteBuffer? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Apply the selected enhancement
        val enhancedBitmap = when (selectedItem) {
            "Histogram Equalization" -> histogramEqualization(bitmap)
            "Contrast Stretching" -> contrastStretching(bitmap)
            else -> bitmap // Default case: no enhancement
        }

        val stream = ByteArrayOutputStream()
        enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val enhancedBytes = stream.toByteArray()

        val enhancedBuffer = ByteBuffer.allocateDirect(enhancedBytes.size)
        enhancedBuffer.put(enhancedBytes)
        enhancedBuffer.rewind()

        return enhancedBuffer
    }

    private fun histogramEqualization(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogramR = IntArray(256)
        val histogramG = IntArray(256)
        val histogramB = IntArray(256)

        for (pixel in pixels) {
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            histogramR[red]++
            histogramG[green]++
            histogramB[blue]++
        }

        val cumulativeHistogramR = IntArray(256)
        val cumulativeHistogramG = IntArray(256)
        val cumulativeHistogramB = IntArray(256)

        cumulativeHistogramR[0] = histogramR[0]
        cumulativeHistogramG[0] = histogramG[0]
        cumulativeHistogramB[0] = histogramB[0]

        for (i in 1 until 256) {
            cumulativeHistogramR[i] = cumulativeHistogramR[i - 1] + histogramR[i]
            cumulativeHistogramG[i] = cumulativeHistogramG[i - 1] + histogramG[i]
            cumulativeHistogramB[i] = cumulativeHistogramB[i - 1] + histogramB[i]
        }

        val scaleFactor = 255.0 / (width * height)

        val enhancedPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val red = Color.red(pixels[i])
            val green = Color.green(pixels[i])
            val blue = Color.blue(pixels[i])

            val enhancedRed = (cumulativeHistogramR[red] * scaleFactor).toInt()
            val enhancedGreen = (cumulativeHistogramG[green] * scaleFactor).toInt()
            val enhancedBlue = (cumulativeHistogramB[blue] * scaleFactor).toInt()

            val enhancedPixel = Color.rgb(enhancedRed, enhancedGreen, enhancedBlue)
            enhancedPixels[i] = enhancedPixel
        }

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        enhancedBitmap.setPixels(enhancedPixels, 0, width, 0, 0, width, height)

        return enhancedBitmap
    }

    private fun contrastStretching(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find minimum and maximum luminance values
        var minLuminance = 255
        var maxLuminance = 0
        for (pixel in pixels) {
            val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            minLuminance = minOf(minLuminance, luminance)
            maxLuminance = maxOf(maxLuminance, luminance)
        }

        // Perform contrast stretching
        val enhancedPixels = IntArray(width * height)
        val scaleFactor = 255.0 / (maxLuminance - minLuminance)
        for (i in pixels.indices) {
            val red = Color.red(pixels[i])
            val green = Color.green(pixels[i])
            val blue = Color.blue(pixels[i])

            val enhancedRed = ((red - minLuminance) * scaleFactor).toInt().coerceIn(0, 255)
            val enhancedGreen = ((green - minLuminance) * scaleFactor).toInt().coerceIn(0, 255)
            val enhancedBlue = ((blue - minLuminance) * scaleFactor).toInt().coerceIn(0, 255)

            val enhancedPixel = Color.rgb(enhancedRed, enhancedGreen, enhancedBlue)
            enhancedPixels[i] = enhancedPixel
        }

        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        enhancedBitmap.setPixels(enhancedPixels, 0, width, 0, 0, width, height)

        return enhancedBitmap
    }





}
