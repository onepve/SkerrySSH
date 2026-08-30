package app.skerry.ui.remote

import app.skerry.shared.host.Host

actual fun canLaunchNativeClient(host: Host): Boolean = false

actual fun launchNativeRemoteClient(host: Host): Boolean = false
