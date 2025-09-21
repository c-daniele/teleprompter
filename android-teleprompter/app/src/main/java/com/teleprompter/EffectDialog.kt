package com.teleprompter

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class EffectDialog(
    private val onEffectSelected: (String) -> Unit
) : DialogFragment() {
    
    private val effects = arrayOf(
        "brightness",
        "contrast",
        "saturation",
        "blur",
        "grayscale",
        "sepia"
    )
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Choose Effect")
            .setItems(effects) { _, which ->
                onEffectSelected(effects[which])
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}