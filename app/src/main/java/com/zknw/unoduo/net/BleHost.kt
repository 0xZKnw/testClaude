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
import android.bluetooth.BluetoothStatusCodes
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
 * The host side of the link: a GATT server that advertises the room and talks to up to
 * [maxGuests] guests at once. Every GATT operation runs on a private handler thread,
 * and each guest gets its own send queue because Android only tolerates one outstanding
 * notification per device — a shared queue would let a slow phone stall the table.
 *
 * Guests are addressed by their Bluetooth address, which the caller treats as an opaque
 * key: seats are assigned above this layer.
 */
@SuppressLint("MissingPermission")
class BleHost(
    private val context: Context,
    private val roomCode: String,
    private val maxGuests: Int,
    private val listener: Listener
) {

    interface Listener {
        /** The guest has subscribed and can now be sent messages. */
        fun onGuestReady(key: String)
        fun onGuestGone(key: String)
        fun onMessage(key: String, msg: NetMsg)
        fun onError(message: String)
        fun onAdvertising()
    }

    /** Everything that belongs to one connected guest. */
    private class Peer(val device: BluetoothDevice) {
        var subscribed = false
        var mtu = 23
        val reassembler = Framing.Reassembler()
        val outbox = ArrayDeque<ByteArray>()
        var sending = false
    }

    private val thread = HandlerThread("ble-host").apply { start() }
    private val handler = Handler(thread.looper)

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    /** Insertion-ordered so seats can be handed out in the order people joined. */
    private val peers = LinkedHashMap<String, Peer>()

    private var stopped = false
    private var advertising = false

    /** Once the deal starts the door is closed, so latecomers are not left half-in. */
    private var open = true

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

    /** Shuts the door: the room stops being discoverable, connected guests stay. */
    fun closeRoom() = handler.post {
        open = false
        stopAdvertising()
    }

    /** Reopens the room, e.g. when someone leaves before the deal. */
    fun reopenRoom() = handler.post {
        open = true
        refreshAdvertising()
    }

    private fun refreshAdvertising() {
        if (stopped) return
        if (open && peers.size < maxGuests) startAdvertising() else stopAdvertising()
    }

    private fun stopAdvertising() {
        if (!advertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        }
        advertising = false
    }

    private fun startAdvertising() {
        if (stopped || advertising) return
        val adapter = manager.adapter ?: return
        val le = adapter.bluetoothLeAdvertiser
        if (le == null) {
            listener.onError("Cet appareil ne peut pas émettre en Bluetooth LE")
            return
        }
        advertiser = le

        // BALANCED emits every 250 ms instead of every 100 ms: the lobby can stay open a
        // long while, and since the guest scans continuously it still finds the room on
        // its first sweep. Transmit power stays at maximum — the saving is in how often
        // the radio wakes, not how loud it speaks, and range is not worth trading.
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
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

        advertising = true
        le.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            listener.onAdvertising()
        }

        override fun onStartFailure(errorCode: Int) {
            handler.post { advertising = false }
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
                if (status == BluetoothGatt.GATT_SUCCESS) refreshAdvertising()
                else listener.onError("Service Bluetooth refusé ($status)")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            handler.post {
                if (stopped) return@post
                val key = device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!peers.containsKey(key) && (!open || peers.size >= maxGuests)) {
                        // Room full or already dealing: turn them away rather than
                        // holding a connection that will never get a seat.
                        server?.cancelConnection(device)
                        return@post
                    }
                    peers.getOrPut(key) { Peer(device) }
                    refreshAdvertising()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (peers.remove(key) != null) {
                        refreshAdvertising()
                        listener.onGuestGone(key)
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, newMtu: Int) {
            val key = device?.address ?: return
            handler.post { peers[key]?.mtu = newMtu.coerceAtLeast(23) }
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
                if (descriptor.uuid != Ble.CCCD_UUID) return@post
                val peer = peers[device.address] ?: return@post
                peer.subscribed = enabling
                if (enabling) {
                    listener.onGuestReady(device.address)
                    pump(peer)
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
                val peer = peers[device.address] ?: return@post
                val payload = peer.reassembler.feed(bytes) ?: return@post
                val msg = Wire.decode(payload)
                if (msg != null) listener.onMessage(device.address, msg)
                else Log.w(TAG, "Message illisible (${payload.size} octets)")
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            val key = device?.address ?: return
            handler.post {
                val peer = peers[key] ?: return@post
                peer.sending = false
                pump(peer)
            }
        }
    }

    /** Sends to one guest. Unknown keys are ignored: the guest may have just left. */
    fun send(key: String, msg: NetMsg) = handler.post {
        if (stopped) return@post
        val peer = peers[key] ?: return@post
        enqueue(peer, msg)
    }

    /** Sends the same message to everyone currently in the room. */
    fun broadcast(msg: NetMsg) = handler.post {
        if (stopped) return@post
        peers.values.forEach { enqueue(it, msg) }
    }

    private fun enqueue(peer: Peer, msg: NetMsg) {
        peer.outbox.addAll(Framing.split(Wire.encode(msg), peer.mtu))
        pump(peer)
    }

    private fun pump(peer: Peer) {
        if (peer.sending || stopped || !peer.subscribed) return
        val tx = txCharacteristic ?: return
        val frame = peer.outbox.poll() ?: return
        peer.sending = true
        if (!notify(peer.device, tx, frame)) {
            // Retry once shortly after; the stack is usually just busy.
            peer.sending = false
            peer.outbox.addFirst(frame)
            handler.postDelayed({ pump(peer) }, 40)
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
                    BluetoothStatusCodes.SUCCESS
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
            stopAdvertising()
            advertiser = null
            try {
                peers.values.forEach { server?.cancelConnection(it.device) }
                server?.close()
            } catch (_: Exception) {
            }
            server = null
            peers.clear()
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "BleHost"
    }
}
