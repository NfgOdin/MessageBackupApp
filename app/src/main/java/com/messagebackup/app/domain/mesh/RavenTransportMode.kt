package com.messagebackup.app.domain.mesh

/**
 * Multi-transport modes supported by Raven Mesh.
 * Allows nodes to bridge between Wi-Fi Hotspots, Bluetooth Low Energy (BLE) off-grid,
 * and 4G/5G Cellular data channels.
 */
enum class RavenTransportMode(val title: String, val description: String, val icon: String) {
    AUTO("Adaptive Multi-Path", "Intelligently routes via Wi-Fi LAN, Cellular P2P, or BLE based on signal availability", "🌐"),
    WIFI_LAN("Local Wi-Fi / Hotspot", "Direct local TCP/IP socket transport on port 8744 (fastest bandwidth)", "📶"),
    BLUETOOTH_LE("Off-Grid BLE Radio", "Direct Bluetooth Low Energy GATT mesh; zero Wi-Fi or cellular needed", "⚡"),
    CELLULAR_P2P("Global 4G / 5G WebRTC", "Global peer-to-peer data channel signaling for remote contacts", "🛰️")
}
