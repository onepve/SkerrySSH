package app.skerry.ui.remote

import app.skerry.shared.host.Host
import app.skerry.shared.ssh.ConnectionType
import app.skerry.shared.ssh.isRemoteDesktop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

actual fun canLaunchNativeClient(host: Host): Boolean = host.connectionType.isRemoteDesktop

actual fun launchNativeRemoteClient(host: Host): Boolean {
    if (!host.connectionType.isRemoteDesktop) return false
    val address = host.address.trim()
    if (address.isBlank()) return false

    CoroutineScope(Dispatchers.IO).launch {
        try {
            doLaunchNative(host, address)
        } catch (_: Throwable) {
        }
    }
    return true
}

private fun doLaunchNative(host: Host, address: String) {
    val isRdp = host.connectionType == ConnectionType.RDP
    val isVnc = host.connectionType == ConnectionType.VNC
    val defaultPort = if (isRdp) 3389 else 5900
    val port = if (host.port > 0) host.port else defaultPort
    val targetAddress = "$address:$port"
    val username = host.username.trim()
    val displayName = host.label.ifBlank { targetAddress }

    val os = System.getProperty("os.name", "").lowercase()
    when {
        os.contains("win") -> {
            launchWindows(isRdp, isVnc, targetAddress, username, displayName)
        }
        os.contains("mac") || os.contains("darwin") -> {
            launchMac(isRdp, isVnc, targetAddress, username)
        }
        else -> {
            launchLinux(isRdp, isVnc, targetAddress, username, displayName)
        }
    }
}

private fun launchLinux(isRdp: Boolean, isVnc: Boolean, targetAddress: String, username: String, displayName: String) {
    val tempDir = System.getProperty("java.io.tmpdir", "/tmp")
    val sanitized = displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val remminaFile = File(tempDir, "skerry_${if (isRdp) "rdp" else "vnc"}_$sanitized.remmina")
    remminaFile.deleteOnExit()

    val remminaContent = buildString {
        appendLine("[remmina]")
        appendLine("name=$displayName")
        appendLine("protocol=${if (isRdp) "RDP" else "VNC"}")
        appendLine("server=$targetAddress")
        if (username.isNotBlank() && isRdp) {
            appendLine("username=$username")
        }
        appendLine("colordepth=32")
        appendLine("window_maximize=1")
        appendLine("disableclipboard=0")
    }

    try {
        remminaFile.writeText(remminaContent, Charsets.UTF_8)
    } catch (_: Exception) {
    }

    if (remminaFile.exists() && tryStartProcess("remmina", "-c", remminaFile.absolutePath)) {
        return
    }

    if (isRdp) {
        val args = mutableListOf("xfreerdp", "/v:$targetAddress", "/dynamic-resolution", "+clipboard")
        if (username.isNotBlank()) {
            args.add("/u:$username")
        }
        if (tryStartProcess(*args.toTypedArray())) {
            return
        }
    } else if (isVnc) {
        if (tryStartProcess("vncviewer", targetAddress)) {
            return
        }
    }

    if (remminaFile.exists()) {
        tryStartProcess("xdg-open", remminaFile.absolutePath)
    }
}

private fun launchWindows(isRdp: Boolean, isVnc: Boolean, targetAddress: String, username: String, displayName: String) {
    if (isRdp) {
        val tempDir = System.getenv("TEMP") ?: System.getProperty("java.io.tmpdir", ".")
        val sanitized = displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val rdpFile = File(tempDir, "skerry_rdp_$sanitized.rdp")
        rdpFile.deleteOnExit()

        val rdpContent = buildString {
            appendLine("full address:s:$targetAddress")
            if (username.isNotBlank()) {
                appendLine("username:s:$username")
            }
            appendLine("prompt for credentials:i:1")
            appendLine("screen mode id:i:2")
            appendLine("use multimon:i:0")
            appendLine("audiomode:i:0")
            appendLine("redirectclipboard:i:1")
        }
        try {
            rdpFile.writeText(rdpContent, Charsets.UTF_8)
            if (tryStartProcess("mstsc.exe", rdpFile.absolutePath)) {
                return
            }
        } catch (_: Exception) {
        }
        tryStartProcess("mstsc.exe", "/v:$targetAddress")
    } else if (isVnc) {
        tryStartProcess("vncviewer.exe", targetAddress)
    }
}

private fun launchMac(isRdp: Boolean, isVnc: Boolean, targetAddress: String, username: String) {
    if (isRdp) {
        val rdpUri = if (username.isNotBlank()) {
            "rdp://full%20address=s:$targetAddress&username=s:$username"
        } else {
            "rdp://full%20address=s:$targetAddress"
        }
        tryStartProcess("open", rdpUri)
    } else if (isVnc) {
        tryStartProcess("open", "vnc://$targetAddress")
    }
}

private fun tryStartProcess(vararg cmd: String): Boolean {
    return try {
        val pb = ProcessBuilder(*cmd)
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        pb.start()
        true
    } catch (_: Throwable) {
        false
    }
}
