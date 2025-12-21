package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class BluetoothHelper(
    private val activity: Activity,
    private val listener: Listener
) {

    companion object {
        private const val TAG = "BluetoothHelper"
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val REQ_PERMISSION_CODE = 1001
    }

    interface Listener {
        fun onPermissionRequired(permissions: Array<String>, requestCode: Int)
        fun onPairedDevices(devices: List<BluetoothDevice>)
        fun onConnecting(device: BluetoothDevice)
        fun onConnected(device: BluetoothDevice)
        fun onDisconnected()
        fun onConnectionFailed(reason: String)
        fun onDataReceived(text: String)
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var readThreadRunning = false

    /* =========================================================
       PERMISSION HELPERS
    ========================================================= */

    private fun hasBtConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

    private fun hasBtScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

    fun ensurePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBtConnectPermission())
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!hasBtScanPermission())
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            listener.onPermissionRequired(
                permissions.toTypedArray(),
                REQ_PERMISSION_CODE
            )
        }
    }

    /* =========================================================
       LIST PAIRED DEVICES
    ========================================================= */

    fun listPairedDevices() {
        val adapter = bluetoothAdapter ?: run {
            listener.onConnectionFailed("Bluetooth tidak tersedia")
            return
        }

        if (!hasBtConnectPermission()) {
            listener.onConnectionFailed("Permission BLUETOOTH_CONNECT belum diberikan")
            return
        }

        try {
            val devices = adapter.bondedDevices?.toList() ?: emptyList()
            listener.onPairedDevices(devices)
        } catch (e: SecurityException) {
            listener.onConnectionFailed("Akses Bluetooth ditolak sistem")
        }
    }

    /* =========================================================
       CONNECT BY MAC ADDRESS
    ========================================================= */

    fun connect(macAddress: String) {
        val adapter = bluetoothAdapter ?: run {
            listener.onConnectionFailed("Bluetooth tidak tersedia")
            return
        }

        if (!adapter.isEnabled) {
            listener.onConnectionFailed("Bluetooth mati")
            return
        }

        if (!hasBtConnectPermission()) {
            listener.onConnectionFailed("Permission BLUETOOTH_CONNECT belum diberikan")
            return
        }

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            listener.onConnectionFailed("MAC Address tidak valid")
            return
        } catch (e: SecurityException) {
            listener.onConnectionFailed("Akses Bluetooth ditolak")
            return
        }

        connectDevice(device)
    }

    /* =========================================================
       CONNECT TO DEVICE
    ========================================================= */

    private fun connectDevice(device: BluetoothDevice) {
        listener.onConnecting(device)

        thread {
            try {
                socket?.close()

                if (hasBtScanPermission()) {
                    try {
                        bluetoothAdapter?.cancelDiscovery()
                    } catch (_: SecurityException) {}
                }

                val tmpSocket =
                    device.createRfcommSocketToServiceRecord(SPP_UUID)

                tmpSocket.connect()

                socket = tmpSocket
                inStream = tmpSocket.inputStream
                outStream = tmpSocket.outputStream

                activity.runOnUiThread {
                    listener.onConnected(device)
                }

                startReadingLoop()

            } catch (e: IOException) {
                Log.e(TAG, "Connect failed", e)
                activity.runOnUiThread {
                    listener.onConnectionFailed("Gagal terhubung")
                }
                disconnect()
            }
        }
    }

    /* =========================================================
       READ DATA LOOP
    ========================================================= */

    private fun startReadingLoop() {
        val input = inStream ?: return
        readThreadRunning = true

        thread {
            val buffer = ByteArray(1024)
            val sb = StringBuilder()

            while (readThreadRunning) {
                try {
                    val bytes = input.read(buffer)
                    if (bytes <= 0) break

                    sb.append(String(buffer, 0, bytes))
                    var idx = sb.indexOf("\n")

                    while (idx != -1) {
                        val line = sb.substring(0, idx).trim()
                        sb.delete(0, idx + 1)
                        if (line.isNotEmpty()) {
                            activity.runOnUiThread {
                                listener.onDataReceived(line)
                            }
                        }
                        idx = sb.indexOf("\n")
                    }
                } catch (e: IOException) {
                    break
                }
            }
            disconnect()
        }
    }

    /* =========================================================
       SEND DATA (OPTIONAL)
    ========================================================= */

    fun write(text: String) {
        try {
            outStream?.write(text.toByteArray())
            outStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Write failed", e)
        }
    }

    /* =========================================================
       DISCONNECT
    ========================================================= */

    fun disconnect() {
        readThreadRunning = false
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}

        socket = null
        inStream = null
        outStream = null

        activity.runOnUiThread {
            listener.onDisconnected()
        }
    }

    fun isConnected(): Boolean =
        socket?.isConnected == true
}
