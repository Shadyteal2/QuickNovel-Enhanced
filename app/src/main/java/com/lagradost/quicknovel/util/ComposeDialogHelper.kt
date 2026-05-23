package com.lagradost.quicknovel.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard

object ComposeDialogHelper {

    private fun setupViewTreeOwners(dialog: Dialog, context: Context, composeView: ComposeView) {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is androidx.fragment.app.FragmentActivity) {
                break
            }
            currentContext = currentContext.baseContext
        }
        val activity = currentContext as? androidx.fragment.app.FragmentActivity
            ?: com.lagradost.quicknovel.CommonActivity.activity as? androidx.fragment.app.FragmentActivity

        val lifecycleOwner = activity
        val viewModelStoreOwner = activity
        val savedStateRegistryOwner = activity

        if (lifecycleOwner != null) {
            composeView.setViewTreeLifecycleOwner(lifecycleOwner)
            dialog.window?.decorView?.setViewTreeLifecycleOwner(lifecycleOwner)
        }
        if (viewModelStoreOwner != null) {
            composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            dialog.window?.decorView?.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
        }
        if (savedStateRegistryOwner != null) {
            composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            dialog.window?.decorView?.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        }
    }

    @Composable
    fun DialogSurface(
        title: String,
        message: String,
        positiveButtonText: String,
        negativeButtonText: String,
        onPositiveClick: () -> Unit,
        onNegativeClick: () -> Unit,
        customContent: @Composable (ColumnScope.() -> Unit)? = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .glassCard(shape = RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                
                if (customContent != null) {
                    customContent()
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onNegativeClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = negativeButtonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = onPositiveClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = positiveButtonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Shows the warning dialog when manually importing a provider.
     */
    fun showImportProviderDialog(
        context: Context,
        onConfirm: (doNotShowAgain: Boolean) -> Unit,
        onCancel: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val composeView = ComposeView(context).apply {
            setContent {
                QuickNovelTheme {
                    var doNotShowAgain by remember { mutableStateOf(false) }
                    
                    DialogSurface(
                        title = "Import Provider",
                        message = "Join the NeoQN telegram/discord to get the latest providers apk, you can find the social links in settings and import it",
                        positiveButtonText = "OK",
                        negativeButtonText = context.getString(R.string.cancel),
                        onPositiveClick = {
                            onConfirm(doNotShowAgain)
                            dialog.dismiss()
                        },
                        onNegativeClick = {
                            onCancel()
                            dialog.dismiss()
                        },
                        customContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        doNotShowAgain = !doNotShowAgain
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = doNotShowAgain,
                                    onCheckedChange = { doNotShowAgain = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.secondary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Do not show again",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
        
        dialog.setContentView(composeView)
        setupViewTreeOwners(dialog, context, composeView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.6f)
        }
        dialog.show()
    }

    /**
     * Shows a confirmation dialog for resetting the background.
     */
    fun showResetBackgroundDialog(
        context: Context,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val composeView = ComposeView(context).apply {
            setContent {
                QuickNovelTheme {
                    DialogSurface(
                        title = context.getString(R.string.reset_background),
                        message = context.getString(R.string.reset_background_summary),
                        positiveButtonText = context.getString(R.string.reset_background),
                        negativeButtonText = context.getString(android.R.string.cancel),
                        onPositiveClick = {
                            onConfirm()
                            dialog.dismiss()
                        },
                        onNegativeClick = {
                            dialog.dismiss()
                        }
                    )
                }
            }
        }
        
        dialog.setContentView(composeView)
        setupViewTreeOwners(dialog, context, composeView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.6f)
        }
        dialog.show()
    }
}
