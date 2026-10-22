package com.messagebackup.app.domain.migration

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Handles high-speed direct Wi-Fi local network migration between two phones without USB cables or a PC.
 *
 * Sender (Old Phone):
 * - Launches an embedded local HTTP/TCP socket server on an ephemeral port.
 * - Generates a QR Code containing `odin-migration://<IP>:<PORT>`.
 * - Streams the selected conversation backup archive directly over local Wi-Fi.
 *
 * Receiver (New Phone):
 * - Scans the QR code or enters the target IP:PORT.
 * - Connects via TCP socket and streams the backup file with live progress.
 * - Automatically hands off the received archive to the restore engine.
 */
object DirectLocalMigrationManager {

    const val DEFAULT_PORT = 8998
    const val PROTOCOL_PREFIX = "odin-migration://"

    /**
     * Resolves the primary local IPv4 address of this device on Wi-Fi or hotspot.
     * Prioritizes wlan/ap/swlan site-local addresses over cellular rmnet or VPN tun interfaces.
     */
    fun getLocalIpAddress(): String? {
        try {
            var fallbackSiteLocal: String? = null
            var fallbackAny: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = iface.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("dummy")) {
                    continue
                }
                val isWifiOrHotspot = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("eth")

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (isWifiOrHotspot && addr.isSiteLocalAddress) {
                            return hostAddr
                        }
                        if (addr.isSiteLocalAddress && fallbackSiteLocal == null) {
                            fallbackSiteLocal = hostAddr
                        } else if (fallbackAny == null) {
                            fallbackAny = hostAddr
                        }
                    }
                }
            }
            return fallbackSiteLocal ?: fallbackAny
        } catch (_: Exception) {}
        return null
    }

    /**
     * Generates a high-resolution QR Code Bitmap encoding the local IP and migration port.
     */
    fun generateMigrationQrBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Sender Service: Listens for an incoming connection and streams the backup file directly.
     */
    suspend fun serveBackupFile(
        file: File,
        port: Int = DEFAULT_PORT,
        onClientConnected: (clientIp: String) -> Unit,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(port)
            serverSocket.soTimeout = 120_000 // 2 minute pairing timeout

            val socket = serverSocket.accept()
            onClientConnected(socket.inetAddress.hostAddress ?: "Unknown Client")

            val totalBytes = file.length()
            val input = BufferedInputStream(FileInputStream(file))
            val output = BufferedOutputStream(socket.getOutputStream())

            // Write 8-byte file length header
            val lengthBytes = java.nio.ByteBuffer.allocate(8).putLong(totalBytes).array()
            output.write(lengthBytes)
            output.flush()

            val buffer = ByteArray(64 * 1024) // 64 KB buffer for high Wi-Fi throughput
            var bytesRead: Int
            var sentBytes = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                sentBytes += bytesRead
                onProgress(sentBytes, totalBytes)
            }
            output.flush()

            socket.close()
            input.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Receiver Client: Connects to Sender's IP and port and downloads the streamed backup archive.
     */
    suspend fun receiveBackupFile(
        host: String,
        port: Int = DEFAULT_PORT,
        outputDestinationFile: File,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(host, port)
            socket.soTimeout = 60_000

            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(FileOutputStream(outputDestinationFile))

            // Read 8-byte header for total length
            val lengthHeader = ByteArray(8)
            var headerBytesRead = 0
            while (headerBytesRead < 8) {
                val r = input.read(lengthHeader, headerBytesRead, 8 - headerBytesRead)
                if (r == -1) break
                headerBytesRead += r
            }

            val totalBytes = if (headerBytesRead == 8) {
                java.nio.ByteBuffer.wrap(lengthHeader).long
            } else {
                -1L
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalReceived = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalReceived += bytesRead
                onProgress(totalReceived, totalBytes)
            }
            output.flush()

            socket.close()
            output.close()
            input.close()

            if (totalBytes > 0 && totalReceived < totalBytes) {
                return@withContext Result.failure(Exception("Transfer interrupted ($totalReceived / $totalBytes bytes received)."))
            }

            Result.success(outputDestinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
