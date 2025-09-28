package com.teleprompter

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.teleprompter.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewBinding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    private var scrollAnimator: ValueAnimator? = null
    private var scrollSpeed = 0.5f
    private var isScrolling = false
    private var accumulatedScroll = 0f
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var availableMicrophones = mutableListOf<MicrophoneInfo>()
    private var selectedMicrophoneIndex = 0
    
    data class MicrophoneInfo(
        val deviceInfo: AudioDeviceInfo?,
        val displayName: String,
        val isBuiltIn: Boolean
    )
    
    private var audioDeviceCallback: Any? = null
    
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Initialize wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Teleprompter::RecordingWakeLock"
        )
        
        // Initialize audio manager and microphone detection
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeAudioDeviceCallback()
        updateAvailableMicrophones()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        setupUI()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupUI() {
        setupCameraSpinner()
        setupMicrophoneSpinner()
        setupControls()
        setupScriptInput()
        setupSpeedControl()
    }

    private fun setupCameraSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.camera_options,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding.cameraSpinner.adapter = adapter
        
        viewBinding.cameraSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                switchCamera(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupMicrophoneSpinner() {
        viewBinding.microphoneSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMicrophoneIndex = position
                Log.d(TAG, "Selected microphone: ${availableMicrophones.getOrNull(position)?.displayName}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupControls() {
        viewBinding.btnPlay.setOnClickListener { startScrolling() }
        viewBinding.btnPause.setOnClickListener { pauseScrolling() }
        viewBinding.btnRestart.setOnClickListener { restartScript() }
        viewBinding.btnRecord.setOnClickListener { captureVideo() }
        viewBinding.btnToggleInput.setOnClickListener { toggleScriptInput() }
    }

    private fun setupScriptInput() {
        viewBinding.scriptInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewBinding.scriptText.text = s.toString()
            }
        })
    }

    private fun setupSpeedControl() {
        // Set initial speed value display
        updateSpeedDisplay()
        
        viewBinding.speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map progress (0-100) to speed range (0.1-3.0)
                scrollSpeed = 0.1f + (progress / 100f) * 2.9f
                updateSpeedDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateSpeedDisplay() {
        viewBinding.speedValue.text = String.format("%.1f", scrollSpeed)
    }

    private fun startScrolling() {
        if (isScrolling) return
        isScrolling = true
        
        scrollAnimator?.cancel()
        // Reset accumulated scroll when starting
        accumulatedScroll = viewBinding.scriptScrollView.scrollY.toFloat()
        
        scrollAnimator = ValueAnimator.ofFloat(0f, Float.MAX_VALUE).apply {
            duration = Long.MAX_VALUE
            addUpdateListener { animator ->
                if (isScrolling) {
                    // Accumulate fractional pixels
                    accumulatedScroll += scrollSpeed
                    val targetScrollY = accumulatedScroll.toInt()
                    viewBinding.scriptScrollView.scrollTo(0, targetScrollY)
                }
            }
            start()
        }
    }

    private fun pauseScrolling() {
        isScrolling = false
        scrollAnimator?.cancel()
    }

    private fun restartScript() {
        pauseScrolling()
        viewBinding.scriptScrollView.scrollTo(0, 0)
        accumulatedScroll = 0f
    }

    private fun toggleScriptInput() {
        val isVisible = viewBinding.scriptInput.visibility == View.VISIBLE
        viewBinding.scriptInput.visibility = if (isVisible) View.GONE else View.VISIBLE
        viewBinding.viewFinder.visibility = if (isVisible) View.VISIBLE else View.GONE
        viewBinding.teleprompterOverlay.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera(position: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

            val cameraSelector = if (position == 0) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera switch failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        viewBinding.btnRecord.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            // Release wake lock when stopping recording
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Teleprompter")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED) {
                    configureSelectedMicrophone()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Acquire wake lock when recording starts
                        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
                        viewBinding.btnRecord.apply {
                            text = "Stop"
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        // Release wake lock when recording finishes
                        wakeLock?.let {
                            if (it.isHeld) {
                                it.release()
                            }
                        }
                        
                        // Reset audio configuration when recording stops
                        resetAudioConfiguration()
                        
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                        }
                        viewBinding.btnRecord.apply {
                            text = "Record"
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateAvailableMicrophones() {
        availableMicrophones.clear()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            
            // Add built-in microphone
            availableMicrophones.add(MicrophoneInfo(null, "Built-in Microphone", true))
            
            // Add external microphones
            devices.forEach { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                        availableMicrophones.add(MicrophoneInfo(device, "Wired Headset Microphone", false))
                    }
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        availableMicrophones.add(MicrophoneInfo(device, "USB Headset Microphone", false))
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        availableMicrophones.add(MicrophoneInfo(device, "Bluetooth Microphone", false))
                    }
                    AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        if (device.isSource) {
                            availableMicrophones.add(MicrophoneInfo(device, "USB Microphone", false))
                        }
                    }
                }
            }
        } else {
            // For older Android versions, just show basic options
            availableMicrophones.add(MicrophoneInfo(null, "Built-in Microphone", true))
            if (audioManager.isWiredHeadsetOn) {
                availableMicrophones.add(MicrophoneInfo(null, "Wired Headset Microphone", false))
            }
        }
        
        // Update UI on main thread
        runOnUiThread {
            updateMicrophoneSpinner()
        }
        
        Log.d(TAG, "Available microphones: ${availableMicrophones.map { it.displayName }}")
    }
    
    private fun updateMicrophoneSpinner() {
        val microphoneNames = availableMicrophones.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, microphoneNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding.microphoneSpinner.adapter = adapter
        
        // Auto-select wired microphone if available and not already selected
        if (selectedMicrophoneIndex == 0) {
            val wiredMicIndex = availableMicrophones.indexOfFirst { !it.isBuiltIn }
            if (wiredMicIndex >= 0) {
                selectedMicrophoneIndex = wiredMicIndex
                viewBinding.microphoneSpinner.setSelection(wiredMicIndex)
            }
        }
    }
    
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun PendingRecording.configureSelectedMicrophone() {
        val selectedMic = availableMicrophones.getOrNull(selectedMicrophoneIndex)
        
        if (selectedMic != null) {
            Log.d(TAG, "Configuring audio with selected microphone: ${selectedMic.displayName}")
            
            // Configure audio routing before enabling audio recording
            configureAudioRouting(selectedMic)
            
            // Enable audio recording
            withAudioEnabled()
            
            Log.d(TAG, "Audio recording enabled with microphone: ${selectedMic.displayName}")
        } else {
            // Fallback to default audio
            withAudioEnabled()
            Log.d(TAG, "Audio enabled with default microphone (no selection)")
        }
    }
    
    private fun configureAudioRouting(selectedMic: MicrophoneInfo) {
        try {
            // First, reset audio routing to default state
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            
            when {
                selectedMic.deviceInfo?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    // For Bluetooth microphones, start SCO and set audio mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    Log.d(TAG, "Configured Bluetooth SCO for microphone")
                }
                selectedMic.deviceInfo?.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    // For wired headsets, set appropriate audio mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                    
                    // For older Android versions, audio routing is handled by the mode setting
                    Log.d(TAG, "Configured wired headset routing")
                }
                selectedMic.deviceInfo?.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                selectedMic.deviceInfo?.type == AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    // For USB microphones, set communication mode
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                    Log.d(TAG, "Configured USB microphone routing")
                }
                selectedMic.isBuiltIn -> {
                    // For built-in microphone, use normal mode
                    audioManager.mode = AudioManager.MODE_NORMAL
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "Configured built-in microphone routing")
                }
                else -> {
                    // For other external microphones
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "Configured external microphone routing")
                }
            }
            
            // Give the audio system time to apply the routing changes
            Thread.sleep(100)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure audio routing for ${selectedMic.displayName}", e)
        }
    }
    
    private fun resetAudioConfiguration() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            Log.d(TAG, "Audio configuration reset to normal mode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset audio configuration", e)
        }
    }
    
    private fun initializeAudioDeviceCallback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                // Use reflection to avoid compile-time dependency on AudioDeviceCallback
                val callbackClass = Class.forName("android.media.AudioManager\$AudioDeviceCallback")
                val callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackClass.classLoader,
                    arrayOf(callbackClass)
                ) { _, method, _ ->
                    when (method.name) {
                        "onAudioDevicesAdded", "onAudioDevicesRemoved" -> {
                            updateAvailableMicrophones()
                        }
                    }
                    null
                }
                
                // Register the callback using reflection
                val registerMethod = AudioManager::class.java.getMethod(
                    "registerAudioDeviceCallback",
                    callbackClass,
                    android.os.Handler::class.java
                )
                registerMethod.invoke(audioManager, callback, null)
                audioDeviceCallback = callback
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize audio device callback", e)
                audioDeviceCallback = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scrollAnimator?.cancel()
        
        // Reset audio configuration
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            // Reset audio mode to normal
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset audio configuration", e)
        }
        
        // Unregister audio device callback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                val callbackClass = Class.forName("android.media.AudioManager\$AudioDeviceCallback")
                val unregisterMethod = AudioManager::class.java.getMethod(
                    "unregisterAudioDeviceCallback",
                    callbackClass
                )
                unregisterMethod.invoke(audioManager, audioDeviceCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister audio device callback", e)
            }
        }
        
        // Release wake lock if still held
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    companion object {
        private const val TAG = "TeleprompterApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).toTypedArray()
    }
}