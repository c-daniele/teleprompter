package com.teleprompter

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class TransitionDialog(
    private val onTransitionSelected: (String) -> Unit
) : DialogFragment() {
    
    private val transitions = arrayOf(
        "fade",
        "dissolve",
        "wipe",
        "slide"
    )
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Choose Transition")
            .setItems(transitions) { _, which ->
                onTransitionSelected(transitions[which])
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}