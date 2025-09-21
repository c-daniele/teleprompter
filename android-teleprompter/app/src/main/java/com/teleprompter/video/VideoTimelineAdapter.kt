package com.teleprompter.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teleprompter.R

class VideoTimelineAdapter(
    private val onClipClick: (VideoClip) -> Unit
) : RecyclerView.Adapter<VideoTimelineAdapter.ClipViewHolder>() {
    
    private var clips = listOf<VideoClip>()
    
    fun updateClips(newClips: List<VideoClip>) {
        clips = newClips
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_clip, parent, false)
        return ClipViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.bind(clips[position])
    }
    
    override fun getItemCount() = clips.size
    
    inner class ClipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.clipName)
        private val durationText: TextView = itemView.findViewById(R.id.clipDuration)
        private val effectsText: TextView = itemView.findViewById(R.id.clipEffects)
        
        fun bind(clip: VideoClip) {
            nameText.text = clip.name
            durationText.text = formatDuration(clip.duration)
            effectsText.text = if (clip.effects.isNotEmpty()) {
                "Effects: ${clip.effects.joinToString(", ")}"
            } else {
                "No effects"
            }
            
            itemView.setOnClickListener {
                onClipClick(clip)
            }
            
            // Visual indicator for transitions
            if (clip.transition != null) {
                itemView.setBackgroundResource(android.R.color.holo_blue_light)
            } else {
                itemView.setBackgroundResource(android.R.color.darker_gray)
            }
        }
        
        private fun formatDuration(durationMs: Long): String {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }
    }
}