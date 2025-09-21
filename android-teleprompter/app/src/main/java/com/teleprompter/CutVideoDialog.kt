package com.teleprompter

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class CutVideoDialog(
    private val onCut: (startTime: Long, endTime: Long) -> Unit
) : DialogFragment() {
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_cut_video, null)
        
        val startTimeEdit = view.findViewById<EditText>(R.id.editStartTime)
        val endTimeEdit = view.findViewById<EditText>(R.id.editEndTime)
        val btnCut = view.findViewById<Button>(R.id.btnCut)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        
        btnCut.setOnClickListener {
            val startTime = parseTimeToMs(startTimeEdit.text.toString())
            val endTime = parseTimeToMs(endTimeEdit.text.toString())
            
            if (startTime >= 0 && endTime > startTime) {
                onCut(startTime, endTime)
                dismiss()
            } else {
                // Show error
                startTimeEdit.error = "Invalid time range"
            }
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Cut Video")
            .setView(view)
            .create()
    }
    
    private fun parseTimeToMs(timeString: String): Long {
        return try {
            val parts = timeString.split(":")
            when (parts.size) {
                1 -> parts[0].toLong() * 1000 // seconds
                2 -> parts[0].toLong() * 60 * 1000 + parts[1].toLong() * 1000 // mm:ss
                3 -> parts[0].toLong() * 60 * 60 * 1000 + parts[1].toLong() * 60 * 1000 + parts[2].toLong() * 1000 // hh:mm:ss
                else -> -1
            }
        } catch (e: NumberFormatException) {
            -1
        }
    }
}