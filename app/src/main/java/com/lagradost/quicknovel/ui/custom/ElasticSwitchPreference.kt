package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.lagradost.quicknovel.R

class ElasticSwitchPreference(context: Context, attrs: AttributeSet?) : SwitchPreferenceCompat(context, attrs) {

    init {
        widgetLayoutResource = R.layout.widget_elastic_switch
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.findViewById(R.id.elastic_toggle) as? ElasticSwitch
        view?.let {
            it.isEnabled = isEnabled
            it.isClickable = isEnabled
            it.setOnCheckedChangeListener(null)
            it.isChecked = isChecked
            it.setOnCheckedChangeListener { _, checked ->
                if (!callChangeListener(checked)) {
                    it.isChecked = !checked
                    return@setOnCheckedChangeListener
                }
                isChecked = checked
            }
        }
        
        // Ensure title and summary follow our glass theme if needed, 
        // but standard preference style usually works well with our styles.xml AppTheme.
    }
}
