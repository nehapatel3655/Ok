package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import android.view.View
import android.view.WindowManager

class AutomationService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var stepTrackerText: TextView? = null
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentConfig: UserConfig? = null
    
    private var currentStep = 1
    private var lastClickTime = 0L
    private var step10Job: Job? = null
    
    private var isRunning = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        setupOverlay()
        scope.launch {
            val db = AppDatabase.getDatabase(this@AutomationService)
            currentConfig = db.configDao().getConfig().firstOrNull()
        }
    }

    private fun setupOverlay() {
        if (windowManager == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        val context = this
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0x88000000.toInt())
            setPadding(16, 16, 16, 16)
        }

        stepTrackerText = TextView(context).apply {
            text = "Step: $currentStep"
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(stepTrackerText)

        val btnToggle = Button(context).apply {
            text = "Start / Stop"
            setOnClickListener {
                isRunning = !isRunning
                if (isRunning) currentStep = 1
                updateStepUI()
            }
        }
        layout.addView(btnToggle)

        val btnClose = Button(context).apply {
            text = "Close (X)"
            setOnClickListener {
                isRunning = false
                overlayView?.let { windowManager?.removeView(it) }
                overlayView = null
            }
        }
        layout.addView(btnClose)

        overlayView = layout
        windowManager?.addView(overlayView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val node = event.source ?: return
        
        handlePopups(node)
        
        if (System.currentTimeMillis() - lastClickTime < 300) return
        
        val mode = currentConfig?.mode ?: "Normal Signup"
        
        if (event.packageName == "com.instagram.android") {
            tryPerformCurrentStep(node)
        }
    }
    
    private fun tryPerformCurrentStep(root: AccessibilityNodeInfo) {
        when (currentStep) {
            1 -> clickByText(root, "Profile", 2)
            2 -> clickByText(root, "Add Instagram account", 3)
            3 -> clickByText(root, "Create new account", 4)
            4 -> clickByText(root, "Next", 5)
            5 -> clickByText(root, "Yes, continue", 6)
            6 -> clickByText(root, "I agree", 7)
            7 -> clickByText(root, "Skip", 8) // Skip Profile Picture
            8 -> clickByText(root, "Profile", 9) // Go Profile again
            9 -> clickByText(root, "Edit profile", 10)
            10 -> {
                if (clickByText(root, "Switch to professional account", 11)) {
                    step10Job?.cancel()
                } else if (step10Job == null || step10Job?.isActive == false) {
                    step10Job = scope.launch {
                        delay(4000)
                        if (currentStep == 10) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                }
            }
            11 -> {
                if (clickByText(root, "Next", 12) || clickByText(root, "Continue", 12)) {}
            }
            12 -> {
                if (clickByText(root, "Artist", 12)) {
                    scope.launch {
                        delay(1000)
                        clickByText(rootInActiveWindow ?: root, "Done", 13)
                    }
                }
            }
            13 -> {
                if (clickByText(root, "Done", 14) || clickByText(root, "Next", 14)) {}
            }
            14 -> clickByText(root, "Next", 15)
            15 -> clickByText(root, "Not now", 16)
            16 -> clickByText(root, "Close", 17)
        }
    }
    
    private fun clickByText(root: AccessibilityNodeInfo, text: String, nextStep: Int): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (n in nodes) {
            // Precise matcher
            if (n.text?.toString()?.equals(text, ignoreCase = true) == true || n.contentDescription?.toString()?.equals(text, ignoreCase = true) == true) {
                if (clickNode(n)) {
                    lastClickTime = System.currentTimeMillis()
                    currentStep = nextStep
                    updateStepUI()
                    return true
                }
            }
        }
        return false
    }

    private fun handlePopups(root: AccessibilityNodeInfo) {
        val dismissTexts = listOf("Skip", "Cancel", "Got it", "Not now", "Deny", "Don't allow")
        for (text in dismissTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.text?.toString() == text && clickNode(node)) {
                    lastClickTime = System.currentTimeMillis()
                    return
                }
            }
        }
    }

    private fun updateStepUI() {
        scope.launch {
            stepTrackerText?.text = if (isRunning) "Step: $currentStep/16" else "Paused. Step: $currentStep"
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val path = Path()
            path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            dispatchGesture(builder.build(), null, null)
            return true
        }
        return false
    }

    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
    }
}
