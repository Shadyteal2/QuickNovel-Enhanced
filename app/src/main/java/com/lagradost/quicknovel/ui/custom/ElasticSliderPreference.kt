package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.getSafeFloat

class ElasticSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var value: Float = 0f
    private var min: Float = 0f
    private var max: Float = 100f
    private var step: Float = 1f
    private var suffix: String = ""

    init {
        layoutResource = R.layout.setting_seekbar_tile
        
        val a = context.obtainStyledAttributes(attrs, R.styleable.ElasticSlider)
        min = a.getFloat(R.styleable.ElasticSlider_valueFrom, 0f)
        max = a.getFloat(R.styleable.ElasticSlider_valueTo, 100f)
        step = a.getFloat(R.styleable.ElasticSlider_stepSize, 1f)
        suffix = a.getString(R.styleable.ElasticSlider_valueSuffix) ?: ""
        a.recycle()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? = a.getFloat(index, 0f)
    override fun onSetInitialValue(defaultValue: Any?) {
        value = getSafeFloat(defaultValue as? Float ?: min)
    }

    private fun getSafeFloat(defaultValue: Float): Float {
        return preferenceManager.sharedPreferences?.getSafeFloat(key, defaultValue) ?: defaultValue
    }

    override fun persistFloat(value: Float): Boolean {
        if (!shouldPersist()) return false
        val editor = preferenceManager.sharedPreferences?.edit() ?: return false
        // Remove existing key (it might be an Integer) to prevent ClassCastException
        editor.remove(key)
        editor.putFloat(key, value)
        editor.apply()
        return true
    }

    override fun getPersistedFloat(defaultReturnValue: Float): Float {
        if (!shouldPersist()) return defaultReturnValue
        val sharedPrefs = preferenceManager.sharedPreferences ?: return defaultReturnValue
        
        // Attempt to read as Float, fallback to Int and migrate if necessary
        return try {
            sharedPrefs.getFloat(key, defaultReturnValue)
        } catch (e: Exception) {
            try {
                if (sharedPrefs.contains(key)) {
                    val intVal = sharedPrefs.getInt(key, defaultReturnValue.toInt())
                    // Migrate to Float for future reads
                    sharedPrefs.edit().remove(key).putFloat(key, intVal.toFloat()).apply()
                    intVal.toFloat()
                } else {
                    defaultReturnValue
                }
            } catch (e2: Exception) {
                defaultReturnValue
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val slider = holder.findViewById(R.id.seekbar) as? ElasticSlider
        slider?.let {
            it.valueFrom = min
            it.valueTo = max
            it.stepSize = step
            it.valueSuffix = suffix
            it.value = value
            
            it.setOnValueChangeListener { _, newValue, fromUser ->
                if (fromUser) {
                    if (callChangeListener(newValue)) {
                        value = newValue
                        persistFloat(newValue)
                    } else {
                        it.value = value
                    }
                }
            }
        }
    }
}
