package com.tonio.libre2clock.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EmergencyAccessibilityService : AccessibilityService() {

    companion object {
        fun isServiceEnabled(context: Context): Boolean {
            val expectedService = "${context.packageName}/${EmergencyAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.contains(expectedService)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b" || packageName == "com.whatsappdual") {
            autoClickSendButton(rootInActiveWindow)
        }
    }

    private fun autoClickSendButton(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        // 1. Buscar botón de envío por IDs conocidos de WhatsApp
        val sendButtonNodeIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsappdual:id/send"
        )

        for (nodeId in sendButtonNodeIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(nodeId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    } else if (node.parent != null && node.parent.isClickable) {
                        node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }
        }

        // 2. Buscar por descripción o texto "Enviar" / "Send"
        val sendKeywords = listOf("Enviar", "Send", "enviar", "send")
        for (keyword in sendKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    } else if (node.parent != null && node.parent.isClickable) {
                        node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}
}
