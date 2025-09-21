package com.teleprompter.video

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class VideoProcessor(private val context: Context) {
    
    private val outputDir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
        "EditedVideos"
    ).apply {
        if (!exists()) mkdirs()
    }
    
    fun cutVideo(
        inputUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: (Boolean, Uri?) -> Unit
    ) {
        val outputFile = generateOutputFile("cut")
        val outputUri = Uri.fromFile(outputFile)
        
        val mediaItem = MediaItem.fromUri(inputUri)
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startTimeMs)
            .setEndPositionMs(endTimeMs)
            .build()
        
        val clippedMediaItem = mediaItem.buildUpon()
            .setClippingConfiguration(clippingConfiguration)
            .build()
        
        val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem).build()
        
        val transformer = Transformer.Builder(context)
            .build()
        
        transformer.start(editedMediaItem, outputUri.toString())
        
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                callback(true, outputUri)
            }
            
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                callback(false, null)
            }
        })
    }
    
    fun applyEffect(
        inputUri: Uri,
        effectType: String,
        callback: (Boolean, Uri?) -> Unit
    ) {
        val outputFile = generateOutputFile("effect_$effectType")
        val outputUri = Uri.fromFile(outputFile)
        
        val mediaItem = MediaItem.fromUri(inputUri)
        
        // For now, we'll create a simple copy operation
        // Effects can be implemented when more advanced Media3 effects become available
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        
        val transformer = Transformer.Builder(context)
            .build()
        
        transformer.start(editedMediaItem, outputUri.toString())
        
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                callback(true, outputUri)
            }
            
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                callback(false, null)
            }
        })
    }
    
    fun mergeClips(
        clips: List<VideoClip>,
        callback: (Boolean, Uri?) -> Unit
    ) {
        if (clips.isEmpty()) return callback(false, null)
        
        val outputFile = generateOutputFile("merged")
        val outputUri = Uri.fromFile(outputFile)
        
        val editedMediaItems = clips.map { clip ->
            val mediaItem = MediaItem.fromUri(clip.uri)
            EditedMediaItem.Builder(mediaItem).build()
        }
        
        val sequence = EditedMediaItemSequence(editedMediaItems)
        val composition = Composition.Builder(sequence).build()
        
        val transformer = Transformer.Builder(context)
            .build()
        
        transformer.start(composition, outputUri.toString())
        
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                callback(true, outputUri)
            }
            
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                callback(false, null)
            }
        })
    }
    
    
    private fun generateOutputFile(prefix: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val randomId = Random.nextInt(1000, 9999)
        return File(outputDir, "${prefix}_${timestamp}_$randomId.mp4")
    }
}