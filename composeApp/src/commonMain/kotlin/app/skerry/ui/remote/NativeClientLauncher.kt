package app.skerry.ui.remote

import app.skerry.shared.host.Host

/**
 * Checks whether native system remote desktop clients (e.g. Remmina / FreeRDP / VNCViewer / mstsc)
 * can be launched for this host profile.
 */
expect fun canLaunchNativeClient(host: Host): Boolean

/**
 * Launches the native system remote desktop client for [host] in the background.
 * Supports custom ports for RDP and VNC. Returns true if the process started successfully.
 */
expect fun launchNativeRemoteClient(host: Host): Boolean
