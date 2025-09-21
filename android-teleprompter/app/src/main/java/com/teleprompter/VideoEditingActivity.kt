package com.teleprompter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.teleprompter.databinding.ActivityVideoEditingBinding
import com.teleprompter.video.VideoClip
import com.teleprompter.video.VideoTimelineAdapter
import com.teleprompter.video.VideoProcessor
import java.io.File

class VideoEditingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoEditingBinding
    private var player: ExoPlayer? = null
    private lateinit var videoProcessor: VideoProcessor
    private lateinit var timelineAdapter: VideoTimelineAdapter
    private val videoClips = mutableListOf<VideoClip>()
    private var currentVideoUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        videoProcessor = VideoProcessor(this)
        setupPlayer()
        setupTimeline()
        setupControls()
        
        // Get video URI from intent
        currentVideoUri = intent.getParcelableExtra("video_uri")
        currentVideoUri?.let { uri ->
            loadVideo(uri)
        }
    }
    
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player
    }
    
    private fun setupTimeline() {
        timelineAdapter = VideoTimelineAdapter { clip ->
            // Handle clip selection
            playClip(clip)
        }
        binding.timelineRecyclerView.apply {
            adapter = timelineAdapter
            layoutManager = LinearLayoutManager(this@VideoEditingActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }
    
    private fun setupControls() {
        binding.btnCut.setOnClickListener {
            showCutDialog()
        }
        
        binding.btnAddTransition.setOnClickListener {
            showTransitionDialog()
        }
        
        binding.btnAddEffect.setOnClickListener {
            showEffectDialog()
        }
        
        binding.btnExport.setOnClickListener {
            exportVideo()
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun loadVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        
        // Add initial clip to timeline
        val clip = VideoClip(
            id = System.currentTimeMillis().toString(),
            uri = uri,
            startTime = 0,
            endTime = getDuration(uri),
            name = "Original Video"
        )
        videoClips.add(clip)
        timelineAdapter.updateClips(videoClips)
    }
    
    private fun getDuration(uri: Uri): Long {
        // This would typically use MediaMetadataRetriever
        // For now, return a default duration
        return 30000 // 30 seconds default
    }
    
    private fun playClip(clip: VideoClip) {
        val mediaItem = MediaItem.fromUri(clip.uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.seekTo(clip.startTime)
        player?.play()
    }
    
    private fun showCutDialog() {
        val dialog = CutVideoDialog { startTime, endTime ->
            cutVideo(startTime, endTime)
        }
        dialog.show(supportFragmentManager, "cut_dialog")
    }
    
    private fun showTransitionDialog() {
        val dialog = TransitionDialog { transitionType ->
            addTransition(transitionType)
        }
        dialog.show(supportFragmentManager, "transition_dialog")
    }
    
    private fun showEffectDialog() {
        val dialog = EffectDialog { effectType ->
            addEffect(effectType)
        }
        dialog.show(supportFragmentManager, "effect_dialog")
    }
    
    private fun cutVideo(startTime: Long, endTime: Long) {
        currentVideoUri?.let { uri ->
            videoProcessor.cutVideo(uri, startTime, endTime) { success, outputUri ->
                if (success && outputUri != null) {
                    val newClip = VideoClip(
                        id = System.currentTimeMillis().toString(),
                        uri = outputUri,
                        startTime = 0,
                        endTime = endTime - startTime,
                        name = "Cut ${videoClips.size + 1}"
                    )
                    videoClips.add(newClip)
                    timelineAdapter.updateClips(videoClips)
                    Toast.makeText(this, "Video cut successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to cut video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun addTransition(transitionType: String) {
        if (videoClips.size >= 2) {
            val lastClip = videoClips.last()
            lastClip.transition = transitionType
            timelineAdapter.updateClips(videoClips)
            Toast.makeText(this, "Transition added: $transitionType", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Need at least 2 clips for transitions", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addEffect(effectType: String) {
        val selectedClip = videoClips.firstOrNull() // For simplicity, apply to first clip
        selectedClip?.let { clip ->
            clip.effects.add(effectType)
            timelineAdapter.updateClips(videoClips)
            Toast.makeText(this, "Effect added: $effectType", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun exportVideo() {
        if (videoClips.isEmpty()) {
            Toast.makeText(this, "No clips to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnExport.isEnabled = false
        binding.btnExport.text = "Exporting..."
        
        videoProcessor.mergeClips(videoClips) { success, outputUri ->
            runOnUiThread {
                binding.btnExport.isEnabled = true
                binding.btnExport.text = "Export"
                
                if (success && outputUri != null) {
                    Toast.makeText(this, "Video exported successfully", Toast.LENGTH_LONG).show()
                    // Optionally share or show the exported video
                    shareVideo(outputUri)
                } else {
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun shareVideo(uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}