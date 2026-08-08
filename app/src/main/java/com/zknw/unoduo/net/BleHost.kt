package com.zknw.unoduo.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.util.Log
import java.util.ArrayDeque

/**
 * The host side of the link: a GATT server that advertises the room and talks to
 * exactly one guest. Every GATT operation runs on a private handler thread and
 * notifications go through a queue, because Android only tolerates one outstanding
 * notification at a time.
 */
@SuppressLint("MissingPermission")
class BleHost(
    private val context: Context,
    private val roomCode: String,
    private val listener: Listener
) {

    interface Listener {
        fun onGuestConnected()
        fun onGuestDisconnected()
        fun onMessage(msg: NetMsg)
        fun onError(message: String)
        fun onAdvertising()
    }

    private val thread = HandlerThread("ble-host").apply { start() }
    private val handler = Handler(thread.looper)

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private var peer: BluetoothDevice? = null
    private var subscribed = false
    private var mtu = 23
    private val reassembler = Framing.Reassembler()

    private val outbox = ArrayDeque<ByteArray>()
    private var sending = false
    private var stopped = false

    fun start() = handler.post {
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            listener.onError("Bluetooth désactivé")
            return@post
        }
        val gattServer = manager.openGattServer(context, serverCallback)
        if (gattServer == null) {
            listener.onError("Impossible d'ouvrir le serveur Bluetooth")
            return@post
        }
        server = gattServer

        val service = BluetoothGattService(Ble.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val tx = BluetoothGattCharacteristic(
            Ble.TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        tx.addDescriptor(
            BluetoothGattDescriptor(
                Ble.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        val rx = BluetoothGattCharacteristic(
            Ble.RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(tx)
        service.addCharacteristic(rx)
        txCharacteristic = tx

        if (!gattServer.addService(service)) {
            listener.onError("Impossible de publier le service Bluetooth")
        }
    }

    private fun startAdvertising() {
        val adapter = manager.adapter ?: return
        val le = adapter.bluetoothLeAdvertiser
        if (le == null) {
            listener.onError("Cet appareil ne peut pas émettre en Bluetooth LE")
            return
        }
        advertiser = le

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // The 128-bit service UUID already fills most of the 31-byte payload, so the
        // room code travels in the scan response instead.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(Ble.SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(Ble.MANUFACTURER_ID, roomCode.toByteArray(Charsets.US_ASCII))
            .build()

        le.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            listener.onAdvertising()
        }

        override fun onStartFailure(errorCode: Int) {
            listener.onError(
                when (errorCode) {
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Émission BLE non supportée par cet appareil"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Trop d'émissions BLE en cours"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Émission déjà en cours"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Données d'émission trop grandes"
                    else -> "Échec de l'émission BLE ($errorCode)"
                }
            )
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising()
                else listener.onError("Service Bluetooth refusé ($status)")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            handler.post {
                if (stopped) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val current = peer
                    if (current != null && current.address != device.address) {
                        // The room is full: politely drop anybody else.
                        server?.cancelConnection(device)
                        return@post
                    }
                    peer = device
                    reassembler.reset()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (peer?.address == device.address) {
                        peer = null
                        subscribed = false
                        sending = false
                        outbox.clear()
                        reassembler.reset()
                        listener.onGuestDisconnected()
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, newMtu: Int) {
            handler.post { mtu = newMtu.coerceAtLeast(23) }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val enabling = value != null &&
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            handler.post {
                if (descriptor.uuid == Ble.CCCD_UUID && peer?.address == device.address) {
                    subscribed = enabling
                    if (enabling) {
                        listener.onGuestConnected()
                        pump()
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            val bytes = value ?: return
            handler.post {
                if (peer?.address != device.address) return@post
                val payload = reassembler.feed(bytes) ?: return@post
                val msg = Wire.decode(payload)
                if (msg != null) listener.onMessage(msg)
                else Log.w(TAG, "Message illisible (${payload.size} octets)")
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            handler.post {
                sending = false
                pump()
            }
        }
    }

    fun send(msg: NetMsg) = handler.post {
        if (stopped) return@post
        val frames = Framing.split(Wire.encode(msg), mtu)
        outbox.addAll(frames)
        pump()
    }

    private fun pump() {
        if (sending || stopped) return
        val device = peer ?: return
        if (!subscribed) return
        val tx = txCharacteristic ?: return
        val frame = outbox.poll() ?: return
        sending = true
        val ok = notify(device, tx, frame)
        if (!ok) {
            // Retry once shortly after; the stack is usually just busy.
            sending = false
            outbox.addFirst(frame)
            handler.postDelayed({ pump() }, 40)
        }
    }

    @Suppress("DEPRECATION")
    private fun notify(
        device: BluetoothDevice,
        tx: BluetoothGattCharacteristic,
        frame: ByteArray
    ): Boolean {
        val gattServer = server ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer.notifyCharacteristicChanged(device, tx, false, frame) ==
                    BluetoothStatusCodesCompat.SUCCESS
            } else {
                tx.value = frame
                gattServer.notifyCharacteristicChanged(device, tx, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "notify a échoué", e)
            false
        }
    }

    fun stop() {
        handler.post {
            if (stopped) return@post
            stopped = true
            outbox.clear()
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {
            }
            advertiser = null
            try {
                peer?.let { server?.cancelConnection(it) }
                server?.close()
            } catch (_: Exception) {
            }
            server = null
            peer = null
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "BleHost"
    }
}

/** [android.bluetooth.BluetoothStatusCodes] only exists from API 31, so mirror the one value we need. */
internal object BluetoothStatusCodesCompat {
    const val SUCCESS = 0
}
