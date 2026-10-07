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

        // 1. Buscar botón de envío por IDs de vista conocidos de WhatsApp
        val sendButtonNodeIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsappdual:id/send"
        )

        for (nodeId in sendButtonNodeIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(nodeId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (clickNodeOrParent(node)) return
                }
            }
        }

        // 2. Búsqueda recursiva por descripción o texto "Enviar" / "Send"
        findAndClickSendByDescription(rootNode)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun findAndClickSendByDescription(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (desc.contains("enviar") || desc.contains("send") || text == "enviar" || text == "send") {
            if (clickNodeOrParent(node)) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSendByDescription(child)) return true
        }

        return false
    }

    override fun onInterrupt() {}
}
