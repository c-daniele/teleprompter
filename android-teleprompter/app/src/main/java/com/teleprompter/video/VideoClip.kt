package com.teleprompter.video

import android.net.Uri

data class VideoClip(
    val id: String,
    val uri: Uri,
    val startTime: Long, // in milliseconds
    val endTime: Long,   // in milliseconds
    val name: String,
    var transition: String? = null,
    val effects: MutableList<String> = mutableListOf()
) {
    val duration: Long
        get() = endTime - startTime
}