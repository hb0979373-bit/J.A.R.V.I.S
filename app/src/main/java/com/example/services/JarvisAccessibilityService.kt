package com.example.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenContextInfo(
    val activePackage: String,
    val visibleTexts: List<String>,
    val interactiveElements: List<String>,
    val summary: String
)

class JarvisAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""
        currentActivePackage = pkg
        Log.d(TAG, "Active Screen Package: $pkg | Class: $cls")
    }

    override fun onInterrupt() {
        Log.d(TAG, "JarvisAccessibilityService interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.i(TAG, "J.A.R.V.I.S Accessibility Neural Link connected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false
    }

    fun captureCurrentScreenContext(): ScreenContextInfo {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            return ScreenContextInfo(
                activePackage = currentActivePackage,
                visibleTexts = emptyList(),
                interactiveElements = emptyList(),
                summary = "Accessibility service is active, but the current active window node is unavailable (e.g. secure screen or home transition)."
            )
        }

        val texts = mutableListOf<String>()
        val interactives = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank()) {
                texts.add(text)
            } else if (!desc.isNullOrBlank()) {
                texts.add(desc)
            }

            if (node.isClickable) {
                val label = text ?: desc ?: node.viewIdResourceName ?: "Action Button"
                interactives.add("Button: $label")
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)

        val uniqueTexts = texts.distinct().take(15)
        val uniqueInteractives = interactives.distinct().take(8)

        val summary = if (uniqueTexts.isNotEmpty()) {
            "Active screen in application '${currentActivePackage}'. Visible elements: ${uniqueTexts.joinToString(" | ")}"
        } else {
            "Active in application '${currentActivePackage}' with no discernible text elements."
        }

        return ScreenContextInfo(
            activePackage = currentActivePackage,
            visibleTexts = uniqueTexts,
            interactiveElements = uniqueInteractives,
            summary = summary
        )
    }

    companion object {
        private const val TAG = "JarvisAccessibility"
        var isRunning: Boolean = false
            private set
        var currentActivePackage: String = ""
            private set
        private var instance: JarvisAccessibilityService? = null

        fun getScreenContext(): ScreenContextInfo? {
            return instance?.captureCurrentScreenContext()
        }

        fun lockScreen(): Boolean {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
            } else {
                false
            }
        }

        fun performHome(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        }

        fun performBack(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        }

        fun performNotifications(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        }
    }
}

