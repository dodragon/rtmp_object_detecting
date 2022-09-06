package com.dod.rtmptest

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import com.dod.rtmptest.databinding.ActivityMainBinding
import com.dod.rtmptest.util.DetectHelper
import com.dod.rtmptest.util.PathUtil
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera1
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), ConnectCheckerRtmp, TextureView.SurfaceTextureListener, DetectHelper.DetectorListener {

    private val url = "rtmp://192.168.0.33/hls/test"

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val detector by lazy { DetectHelper(context = this, objectDetectorListener = this) }

    private var currentDateAndTime = ""

    private val folder by lazy { PathUtil.getRecordPath() }

    lateinit var camera1: RtmpCamera1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(binding.root)

        permissionCheck()
        testInit(binding.texture)
    }

    private var permissionListener: PermissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            testInit(binding.texture)
        }

        override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
        }
    }

    private fun permissionCheck(){
        TedPermission.create()
            .setPermissionListener(permissionListener)
            .setPermissions(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .check()
    }

    fun testInit(textureView: TextureView){
        binding.startStopBtn.setOnClickListener(onClick)
        binding.recordBtn.setOnClickListener(onClick)
        binding.switchBtn.setOnClickListener(onClick)

        camera1 = RtmpCamera1(textureView, this)
        camera1.setReTries(10)

        textureView.surfaceTextureListener = this
    }

    override fun onAuthErrorRtmp() {
        runOnUiThread {
            Toast.makeText(this, "Auth Error", Toast.LENGTH_SHORT).show()
            camera1.stopStream()
        }
    }

    override fun onAuthSuccessRtmp() {
        runOnUiThread {
            Toast.makeText(this, "Auth Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailedRtmp(reason: String) {
        runOnUiThread {
            if(camera1.reTry(5000, reason, null)){
                Toast.makeText(this, "Retry", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Log.e("ERROR", reason)
                Toast.makeText(this, "Connection failed. $reason", Toast.LENGTH_SHORT)
                    .show()
                camera1.stopStream()
            }
        }
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {
    }

    override fun onConnectionSuccessRtmp() {
        runOnUiThread {
            Toast.makeText(this, "Connection Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnectRtmp() {
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
    }

    @SuppressLint("SetTextI18n")
    private val onClick = View.OnClickListener { v ->
        if(v.id == R.id.start_stop_btn){
            if(!camera1.isStreaming){
                if(camera1.isRecording || camera1.prepareAudio() && camera1.prepareVideo()){
                    binding.startStopBtn.text = "STOP"
                    camera1.startStream(url)
                }else {
                    Toast.makeText(this, "Error preparing stream, This device cant do it",
                        Toast.LENGTH_SHORT).show()
                }
            }else {
                binding.startStopBtn.text = "START"
                camera1.stopStream()
            }
        }else if(v.id == R.id.switch_btn){
            try{
                camera1.switchCamera()
            }catch (e: CameraOpenException){
                e.printStackTrace()
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }else if(v.id == R.id.record_btn){
            if(!camera1.isRecording){
                try{
                    if(!folder.exists()){
                        folder.mkdir()
                    }
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    currentDateAndTime = sdf.format(Date())
                    if(!camera1.isStreaming){
                        if(camera1.prepareAudio() && camera1.prepareVideo()){
                            camera1.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                            binding.recordBtn.text = "STOP RECORD"
                            Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error preparing stream, This device cant do it",
                                Toast.LENGTH_SHORT).show()
                        }
                    }else {
                        camera1.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                        binding.recordBtn.text = "STOP RECORD"
                        Toast.makeText(this, "Recording... ", Toast.LENGTH_SHORT).show()
                    }
                }catch (e: IOException){
                    camera1.stopRecord()
                    PathUtil.updateGallery(this, folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                    binding.recordBtn.text = "RECORD"
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            }else {
                camera1.stopRecord()
                PathUtil.updateGallery(this, folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                binding.recordBtn.text = "RECORD"
                Toast.makeText(this,
                    "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.e("textureListener", "onSurfaceTextureAvailable")
        camera1.startPreview()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.e("textureListener", "onSurfaceTextureSizeChanged")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.e("textureListener", "onSurfaceTextureDestroyed")

        if(camera1.isRecording){
            camera1.stopRecord()
            PathUtil.updateGallery(this, folder.absolutePath + "/" + currentDateAndTime + ".mp4")
            Toast.makeText(this,
                "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
                Toast.LENGTH_SHORT).show()
            currentDateAndTime = ""
        }
        if(camera1.isStreaming){
            camera1.stopStream()
        }
        camera1.stopPreview()

        detector.clearObjectDetector()
        binding.overlay.clear()

        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        detector.detect(binding.texture.bitmap!!, 0)
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        runOnUiThread {
            binding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            binding.overlay.invalidate()
        }
    }
}