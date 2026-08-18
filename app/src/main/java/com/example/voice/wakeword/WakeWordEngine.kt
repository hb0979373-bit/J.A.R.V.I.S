package com.example.voice.wakeword

interface WakeWordEngine {
    val isRunning: Boolean
    val supportedPhrases: List<String>
    fun startWakeWordDetection(onWakeWordDetected: (String) -> Unit)
    fun stopWakeWordDetection()
}

/**
 * Android-compliant wake-word spotter that respects foreground microphone lifecycles.
 */
class JarvisWakeWordEngine : WakeWordEngine {

    override var isRunning: Boolean = false
        private set

    override val supportedPhrases: List<String> = listOf("JARVIS", "HEY JARVIS", "OK JARVIS")

    private var onWakeCallback: ((String) -> Unit)? = null

    override fun startWakeWordDetection(onWakeWordDetected: (String) -> Unit) {
        this.onWakeCallback = onWakeWordDetected
        this.isRunning = true
    }

    override fun stopWakeWordDetection() {
        this.isRunning = false
        this.onWakeCallback = null
    }

    fun checkAndTriggerWakeWord(phrase: String): Boolean {
        if (!isRunning) return false
        val normalized = phrase.uppercase().trim()
        val matched = supportedPhrases.any { normalized.contains(it) }
        if (matched) {
            onWakeCallback?.invoke(phrase)
            return true
        }
        return false
    }
}
