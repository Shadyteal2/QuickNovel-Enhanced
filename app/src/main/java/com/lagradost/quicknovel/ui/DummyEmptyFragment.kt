package com.lagradost.quicknovel.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class DummyEmptyFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return View(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // Prevent receiving touch inputs
            isClickable = false
            isFocusable = false
        }
    }
}
