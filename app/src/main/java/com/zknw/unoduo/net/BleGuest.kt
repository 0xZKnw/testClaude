package com.zknw.unoduo.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.util.Log
import java.util.ArrayDeque

/**
 * The guest side: scans for the advertised room code, connects, subscribes and then
 * exchanges framed messages. Writes are queued because a GATT client may only have
 * one write in flight.
 */
@SuppressLint("MissingPermission")
class BleGuest(
    private val context: Context,
    private val roomCode: String,
    private val listener: Listener
) {

    interface Listener {
        fun onSearching()
        fun onConnecting()
        fun onReady()
        fun onMessage(msg: NetMsg)
        fun onDisconnected()
        fun onError(message: String)
    }

    private val thread = HandlerThread("ble-guest").apply { start() }
    private val handler = Handler(thread.looper)

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var stopped = false
    private var ready = false
    private var mtu = 23
    private var connectAttempts = 0

    private val reassembler = Framing.Reassembler()
    private val outbox = ArrayDeque<ByteArray>()
    private var writing = false

    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            listener.onError("Salon introuvable. Vérifie que l'autre téléphone affiche bien le QR code.")
        }
    }

    fun start() = handler.post {
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onError("Bluetooth désactivé")
            return@post
        }
        val le = adapter.bluetoothLeScanner
        if (le == null) {
            listener.onError("Scan Bluetooth indisponible")
            return@post
        }
        scanner = le
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(Ble.SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanning = true
        listener.onSearching()
        le.startScan(filters, settings, scanCallback)
        handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val record = result?.scanRecord ?: return
            val advertised = record.getManufacturerSpecificData(Ble.MANUFACTURER_ID) ?: return
            val code = String(advertised, Charsets.US_ASCII).trim()
            if (!code.equals(roomCode, ignoreCase = true)) return
            val device = result.device ?: return
            handler.post {
                if (!scanning || stopped) return@post
                stopScan()
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                scanning = false
                listener.onError("Échec du scan Bluetooth ($errorCode)")
            }
        }
    }

    private fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    private fun connect(device: BluetoothDevice) {
        connectAttempts++
        listener.onConnecting()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (stopped) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED &&
                    status == BluetoothGatt.GATT_SUCCESS
                ) {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    if (!g.requestMtu(REQUESTED_MTU)) g.discoverServices()
                    return@post
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val wasReady = ready
                    cleanupGatt()
                    when {
                        wasReady -> listener.onDisconnected()
                        connectAttempts < MAX_CONNECT_ATTEMPTS -> {
                            // Status 133 on the first attempt is common; one retry fixes it.
                            handler.postDelayed({ if (!stopped) start() }, 400)
                        }

                        else -> listener.onError("Connexion perdue ($status)")
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            handler.post {
                mtu = if (status == BluetoothGatt.GATT_SUCCESS) newMtu.coerceAtLeast(23) else 23
                g.discoverServices()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post {
                if (stopped) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError("Services Bluetooth illisibles ($status)")
                    return@post
                }
                val service = g.getService(Ble.SERVICE_UUID)
                val tx = service?.getCharacteristic(Ble.TX_UUID)
                val write = service?.getCharacteristic(Ble.RX_UUID)
                if (tx == null || write == null) {
                    listener.onError("Salon incompatible")
                    return@post
                }
                rx = write
                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(Ble.CCCD_UUID)
                if (cccd == null) {
                    listener.onError("Notifications indisponibles")
                    return@post
                }
                writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                if (stopped) return@post
                if (descriptor.uuid != Ble.CCCD_UUID) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.onError("Abonnement refusé ($status)")
                    return@post
                }
                ready = true
                listener.onReady()
                pump()
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handler.post {
                writing = false
                pump()
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handler.post { handleIncoming(characteristic, value) }
        }

        @Deprecated("Kept for API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val value = characteristic.value ?: return
            handler.post { handleIncoming(characteristic, value) }
        }
    }

    private fun handleIncoming(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != Ble.TX_UUID) return
        val payload = reassembler.feed(value) ?: return
        val msg = Wire.decode(payload)
        if (msg != null) listener.onMessage(msg)
        else Log.w(TAG, "Message illisible (${payload.size} octets)")
    }

    fun send(msg: NetMsg) = handler.post {
        if (stopped) return@post
        outbox.addAll(Framing.split(Wire.encode(msg), mtu))
        pump()
    }

    @Suppress("DEPRECATION")
    private fun pump() {
        if (writing || stopped || !ready) return
        val g = gatt ?: return
        val characteristic = rx ?: return
        val frame = outbox.poll() ?: return
        writing = true
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    characteristic,
                    frame,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = frame
                g.writeCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            Log.w(TAG, "write a échoué", e)
            false
        }
        if (!ok) {
            writing = false
            outbox.addFirst(frame)
            handler.postDelayed({ pump() }, 40)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, value)
            } else {
                descriptor.value = value
                g.writeDescriptor(descriptor)
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeDescriptor a échoué", e)
            listener.onError("Abonnement impossible")
        }
    }

    private fun cleanupGatt() {
        ready = false
        writing = false
        outbox.clear()
        reassembler.reset()
        rx = null
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    fun stop() {
        handler.post {
            if (stopped) return@post
            stopped = true
            stopScan()
            cleanupGatt()
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "BleGuest"
        const val REQUESTED_MTU = 247
        const val SCAN_TIMEOUT_MS = 25_000L
        const val MAX_CONNECT_ATTEMPTS = 2
    }
}
