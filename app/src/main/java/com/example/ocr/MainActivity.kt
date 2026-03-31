package com.example.ocr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector

class MainActivity : AppCompatActivity() {

    private lateinit var cropImageView: CropImageView
    private lateinit var btnLoad: Button
    private lateinit var btnResetImage: Button
    private lateinit var cbCrop: CheckBox
    private lateinit var btnApplyCrop: Button

    private lateinit var etClaheLimit: EditText
    private lateinit var btnApplyClahe: Button

    private lateinit var btnFindQR: Button
    private lateinit var btnWarpQR: Button

    private lateinit var spinnerThresh: Spinner
    private lateinit var etBlockSize: EditText
    private lateinit var btnApplyThresh: Button

    private lateinit var spinnerMorph: Spinner
    private lateinit var etMorphKernel: EditText
    private lateinit var btnApplyMorph: Button
    
    private lateinit var btnApplyReconstruct: Button

    private lateinit var btnApplyAll: Button

    private lateinit var spinnerLibrary: Spinner
    private lateinit var btnTest: Button
    private lateinit var tvResult: TextView

    private var originalBitmap: Bitmap? = null
    private var baseBitmap: Bitmap? = null
    // The bitmap resulting from sequential OpenCV processing
    private var currentProcessedBitmap: Bitmap? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            originalBitmap = bitmap
            baseBitmap = bitmap
            currentProcessedBitmap = bitmap
            
            cropImageView.setImageBitmap(bitmap)
            cropImageView.onNewImageLoaded()
            
            tvResult.text = getString(R.string.result_text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "Unable to load OpenCV", Toast.LENGTH_LONG).show()
        }

        btnLoad.setOnClickListener { selectImageLauncher.launch("image/*") }
        
        btnResetImage.setOnClickListener {
            baseBitmap?.let {
                currentProcessedBitmap = it
                cropImageView.setImageBitmap(it)
            }
        }

        cbCrop.setOnCheckedChangeListener { _, isChecked ->
            cropImageView.isCropEnabled = isChecked
        }

        btnApplyCrop.setOnClickListener { 
            applyStep { mat -> doCrop(mat) } 
            baseBitmap = currentProcessedBitmap
        }
        btnApplyClahe.setOnClickListener { applyStep { mat -> doClahe(mat) } }
        btnFindQR.setOnClickListener { applyStep { mat -> doFindQR(mat) } }
        btnWarpQR.setOnClickListener { 
            // Only update currentProcessedBitmap if warp was actually performed
            val warped = doWarpQR()
            if (warped != null) updateProcessedBitmap(warped)
        }
        btnApplyReconstruct.setOnClickListener { applyStep { mat -> doReconstruct(mat) } }
        btnApplyThresh.setOnClickListener { applyStep { mat -> doThresh(mat) } }
        btnApplyMorph.setOnClickListener { applyStep { mat -> doMorph(mat) } }

        btnApplyAll.setOnClickListener {
            if (originalBitmap == null) return@setOnClickListener
            var mat = Mat()
            Utils.bitmapToMat(originalBitmap, mat)

            if (cbCrop.isChecked) mat = doCrop(mat)
            mat = doClahe(mat)
            mat = doFindQR(mat)
            val warped = doWarpQR()
            if (warped != null) mat = warped
            mat = doThresh(mat)
            mat = doMorph(mat)

            updateProcessedBitmap(mat)
            Toast.makeText(this, "Applied All", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            if (currentProcessedBitmap == null) {
                Toast.makeText(this, "No image to test", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val selectedLib = spinnerLibrary.selectedItem?.toString() ?: ""
            if (selectedLib.contains("ML Kit")) {
                decodeWithMLKit(currentProcessedBitmap!!)
            } else {
                decodeWithZXing(currentProcessedBitmap!!)
            }
        }
    }

    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        btnLoad = findViewById(R.id.btnLoad)
        btnResetImage = findViewById(R.id.btnResetImage)
        cbCrop = findViewById(R.id.cbCrop)
        btnApplyCrop = findViewById(R.id.btnApplyCrop)
        
        etClaheLimit = findViewById(R.id.etClaheLimit)
        btnApplyClahe = findViewById(R.id.btnApplyClahe)
        
        btnFindQR = findViewById(R.id.btnFindQR)
        btnWarpQR = findViewById(R.id.btnWarpQR)
        
        spinnerThresh = findViewById(R.id.spinnerThresh)
        etBlockSize = findViewById(R.id.etBlockSize)
        btnApplyThresh = findViewById(R.id.btnApplyThresh)
        
        spinnerMorph = findViewById(R.id.spinnerMorph)
        etMorphKernel = findViewById(R.id.etMorphKernel)
        btnApplyMorph = findViewById(R.id.btnApplyMorph)
        
        btnApplyReconstruct = findViewById(R.id.btnApplyReconstruct)
        
        btnApplyAll = findViewById(R.id.btnApplyAll)
        spinnerLibrary = findViewById(R.id.spinnerLibrary)
        btnTest = findViewById(R.id.btnTest)
        tvResult = findViewById(R.id.tvResult)

        val threshOptions = arrayOf("Otsu", "Adaptive")
        spinnerThresh.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, threshOptions)

        val morphOptions = arrayOf("Close (Fix Breaks)", "Open (Remove Noise)", "Dilate (Thicken White)", "Erode (Thicken Black)")
        spinnerMorph.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, morphOptions)
    }

    private fun applyStep(operation: (Mat) -> Mat) {
        if (currentProcessedBitmap == null) return
        val mat = Mat()
        Utils.bitmapToMat(currentProcessedBitmap, mat)
        
        val resultMat = operation(mat)
        updateProcessedBitmap(resultMat)
    }

    private fun updateProcessedBitmap(mat: Mat) {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        currentProcessedBitmap = bmp
        cropImageView.setImageBitmap(bmp)
    }

    private fun doCrop(mat: Mat): Mat {
        if (!cbCrop.isChecked) return mat
        val rectF = cropImageView.getCropRectBitmapCoords() ?: return mat
        val r = Rect(rectF.left.toInt(), rectF.top.toInt(), rectF.width().toInt(), rectF.height().toInt())
        if (r.x >= 0 && r.y >= 0 && r.x + r.width <= mat.cols() && r.y + r.height <= mat.rows()) {
            return Mat(mat, r)
        }
        return mat
    }

    private fun doClahe(mat: Mat): Mat {
        val limitTxt = etClaheLimit.text?.toString() ?: ""
        val limit = if (limitTxt.isNotEmpty()) limitTxt.toDouble() else 2.0
        
        val grayMat = Mat()
        if (mat.channels() == 3 || mat.channels() == 4) {
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        } else {
            mat.copyTo(grayMat)
        }
        
        val clahe = Imgproc.createCLAHE(limit, Size(8.0, 8.0))
        val result = Mat()
        clahe.apply(grayMat, result)
        
        // Convert back to BGR for consistency
        val coloredResult = Mat()
        Imgproc.cvtColor(result, coloredResult, Imgproc.COLOR_GRAY2BGR)
        return coloredResult
    }

    // Debugging variables for Align
    var debugAlignPointsBitmap: Bitmap? = null
    var debugAlignStraightBitmap: Bitmap? = null
    
    // Logic state for separated Find -> Warp
    private var lastFoundPoints: Mat? = null
    private var cleanMatForWarp: Mat? = null

    private fun doFindQR(mat: Mat): Mat {
        var bestGrayMat = Mat()
        val detector = QRCodeDetector()
        val points = Mat()
        var isDetected = false
        
        // Multi-stage heuristic search: If raw grayscale fails, OpenCV will automatically 
        // try applying CLAHE, and then Adaptive Thresholding to force a detection.
        val strategies = listOf(
            { // 1. Raw Grayscale
              val g = Mat()
              if (mat.channels() == 3 || mat.channels() == 4) Imgproc.cvtColor(mat, g, Imgproc.COLOR_BGR2GRAY) else mat.copyTo(g)
              g
            },
            { // 2. CLAHE Contrast Equalization
              val g = Mat()
              if (mat.channels() == 3 || mat.channels() == 4) Imgproc.cvtColor(mat, g, Imgproc.COLOR_BGR2GRAY) else mat.copyTo(g)
              val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
              clahe.apply(g, g)
              g
            },
            { // 3. Adaptive Thresholding
              val g = Mat()
              if (mat.channels() == 3 || mat.channels() == 4) Imgproc.cvtColor(mat, g, Imgproc.COLOR_BGR2GRAY) else mat.copyTo(g)
              Imgproc.adaptiveThreshold(g, g, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 51, 2.0)
              g
            }
        )

        try {
            for (strategy in strategies) {
                bestGrayMat = strategy()
                isDetected = detector.detect(bestGrayMat, points)
                if (isDetected && points.rows() > 0 && points.cols() == 4) {
                    break // Stop if we successfully found the 4 corners
                }
            }
            
            val debugMat = mat.clone()
            if (isDetected && points.rows() > 0 && points.cols() == 4) {
                // Save points and clean image for the Warp step!
                lastFoundPoints = points.clone()
                cleanMatForWarp = mat.clone()
                
                // Draw detected bounds (polygon) around QR core
                for (i in 0 until 4) {
                    val p1 = org.opencv.core.Point(points.get(0, i)[0], points.get(0, i)[1])
                    val p2 = org.opencv.core.Point(points.get(0, (i + 1) % 4)[0], points.get(0, (i + 1) % 4)[1])
                    Imgproc.line(debugMat, p1, p2, org.opencv.core.Scalar(0.0, 255.0, 0.0), 5)
                    Imgproc.circle(debugMat, p1, 10, org.opencv.core.Scalar(255.0, 0.0, 0.0), -1)
                }
                
                // Save debug bitmap showing what lines were found
                val debugBmp = Bitmap.createBitmap(debugMat.cols(), debugMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(debugMat, debugBmp)
                debugAlignPointsBitmap = debugBmp

                Toast.makeText(this@MainActivity, "Đã quét thấy! Bấm Warp QR để cắt nắn phẳng.", Toast.LENGTH_SHORT).show()
                return debugMat
            } else {
                Toast.makeText(this@MainActivity, "Không thể quét được 4 góc mốc.", Toast.LENGTH_LONG).show()
                return mat
            }
        } catch (e: Exception) {
            Log.e("QRCodeAlign", "Alignment failed: " + e.message)
            Toast.makeText(this@MainActivity, "Alignment Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return mat
    }

    private fun doWarpQR(): Mat? {
        val points = lastFoundPoints
        val srcMat = cleanMatForWarp
        if (points == null || points.empty() || points.cols() != 4 || srcMat == null) {
            Toast.makeText(this@MainActivity, "Vui lòng bấm Find QR trước!", Toast.LENGTH_SHORT).show()
            return null
        }
        
        try {
            val p0x = points.get(0, 0)[0]; val p0y = points.get(0, 0)[1]
            val p1x = points.get(0, 1)[0]; val p1y = points.get(0, 1)[1]
            val p2x = points.get(0, 2)[0]; val p2y = points.get(0, 2)[1]
            val p3x = points.get(0, 3)[0]; val p3y = points.get(0, 3)[1]

            // Calculate precise exact bounding dimension
            val widthA = Math.hypot(p2x - p3x, p2y - p3y)
            val widthB = Math.hypot(p1x - p0x, p1y - p0y)
            val destWidth = Math.max(widthA, widthB)

            val heightA = Math.hypot(p1x - p2x, p1y - p2y)
            val heightB = Math.hypot(p0x - p3x, p0y - p3y)
            val destHeight = Math.max(heightA, heightB)

            val destSize = Math.max(destWidth, destHeight)

            val srcPoints = org.opencv.core.MatOfPoint2f(
                org.opencv.core.Point(p0x, p0y),
                org.opencv.core.Point(p1x, p1y),
                org.opencv.core.Point(p2x, p2y),
                org.opencv.core.Point(p3x, p3y)
            )
            
            val dstPoints = org.opencv.core.MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(destSize, 0.0),
                org.opencv.core.Point(destSize, destSize),
                org.opencv.core.Point(0.0, destSize)
            )

            // Biến đổi nắn khung hình thẳng góc tự động
            val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            val warpedMat = Mat()
            Imgproc.warpPerspective(srcMat, warpedMat, transform, Size(destSize, destSize))

            Toast.makeText(this@MainActivity, "Đã cắt nguyên bản cỡ ${destSize.toInt()}x${destSize.toInt()}!", Toast.LENGTH_SHORT).show()
            
            // Clear cache
            lastFoundPoints = null
            cleanMatForWarp = null
            
            return warpedMat
        } catch (e: Exception) {
            Log.e("QRCodeWarp", "Warp failed: " + e.message)
            Toast.makeText(this@MainActivity, "Lỗi Warp: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun doThresh(mat: Mat): Mat {
        val grayMat = Mat()
        if (mat.channels() == 3 || mat.channels() == 4) {
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        } else {
            mat.copyTo(grayMat)
        }

        val binaryMat = Mat()
        val threshType = spinnerThresh.selectedItem?.toString() ?: ""
        if (threshType == "Otsu") {
            Imgproc.threshold(grayMat, binaryMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        } else {
            val blockText = etBlockSize.text?.toString() ?: ""
            val block = if (blockText.isNotEmpty()) blockText.toInt() else 51
            val finalBlock = if (block % 2 == 0) block + 1 else if (block < 3) 3 else block
            Imgproc.adaptiveThreshold(
                grayMat, binaryMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, finalBlock, 2.0
            )
        }
        
        val coloredResult = Mat()
        Imgproc.cvtColor(binaryMat, coloredResult, Imgproc.COLOR_GRAY2BGR)
        return coloredResult
    }

    private fun doMorph(mat: Mat): Mat {
        val kText = etMorphKernel.text?.toString() ?: ""
        val kSize = if (kText.isNotEmpty()) kText.toDouble() else 3.0
        val finalK = if (kSize % 2 == 0.0) kSize + 1.0 else kSize
        
        val element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(finalK, finalK))
        val morphTypeStr = spinnerMorph.selectedItem?.toString() ?: ""
        
        val op = when {
            morphTypeStr.contains("Close") -> Imgproc.MORPH_CLOSE
            morphTypeStr.contains("Open") -> Imgproc.MORPH_OPEN
            morphTypeStr.contains("Dilate") -> Imgproc.MORPH_DILATE
            else -> Imgproc.MORPH_ERODE
        }
        
        val resultMat = Mat()
        Imgproc.morphologyEx(mat, resultMat, op, element)
        return resultMat
    }

    private fun doReconstruct(mat: Mat): Mat {
        try {
            val gray = Mat()
            if (mat.channels() == 3 || mat.channels() == 4) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY) else mat.copyTo(gray)
            Imgproc.threshold(gray, gray, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            var bestN = 0
            var maxScore = -1

            // Search for the grid size N (21, 25, 29... 57)
            for (version in 1..15) {
                val n = 21 + (version - 1) * 4
                val small = Mat()
                Imgproc.resize(gray, small, Size(n.toDouble(), n.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.threshold(small, small, 127.0, 255.0, Imgproc.THRESH_BINARY)
                
                var score = 0
                for (r in 0 until 7) {
                    for (c in 0 until 7) {
                        val isBlack = small.get(r, c)[0] < 127.0
                        val isExpectedBlack = (r == 0 || r == 6 || c == 0 || c == 6) || (r in 2..4 && c in 2..4)
                        if (isBlack == isExpectedBlack) score++
                    }
                }
                if (score > maxScore) { maxScore = score; bestN = n }
            }

            if (maxScore > 35) { // 49 possible points, 35+ is a strong match
                val optimalSmall = Mat()
                Imgproc.resize(gray, optimalSmall, Size(bestN.toDouble(), bestN.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.threshold(optimalSmall, optimalSmall, 127.0, 255.0, Imgproc.THRESH_BINARY)
                
                val digitalQR = Mat()
                // Upscale to match original mat size using INTER_NEAREST to get sharp, blocky edges ("như in bằng máy")
                Imgproc.resize(optimalSmall, digitalQR, Size(mat.cols().toDouble(), mat.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_NEAREST)
                
                val colored = Mat()
                Imgproc.cvtColor(digitalQR, colored, Imgproc.COLOR_GRAY2BGR)
                Toast.makeText(this@MainActivity, "Reconstructed Digital QR: ${bestN}x${bestN}", Toast.LENGTH_SHORT).show()
                return colored
            } else {
                Toast.makeText(this@MainActivity, "Không thể tính được ma trận lưới QR.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("QRCodeRecon", "Reconstruct failed: " + e.message)
            Toast.makeText(this@MainActivity, "Reconstruct Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return mat
    }

    // Decoding Methods
    private fun decodeWithMLKit(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        tvResult.text = "Processing with ML Kit..."

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (barcode in barcodes) {
                        sb.append("Value: \n").append(barcode.rawValue).append("\n\n")
                    }
                    tvResult.text = sb.toString()
                } else {
                    tvResult.text = "ML Kit: No barcode/QR found."
                }
            }
            .addOnFailureListener { e ->
                tvResult.text = "ML Kit Error: ${e.message}"
            }
    }

    private fun decodeWithZXing(bitmap: Bitmap) {
        tvResult.text = "Processing with ZXing..."
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        val reader = MultiFormatReader()
        try {
            val result = reader.decode(binaryBitmap)
            tvResult.text = "Value: \n${result.text}"
        } catch (e: NotFoundException) {
            tvResult.text = "ZXing: No barcode/QR found."
        } catch (e: Exception) {
            tvResult.text = "ZXing Error: ${e.message}"
        }
    }
}
