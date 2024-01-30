package com.zyf.camera.helper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.getSystemService


@SuppressLint("MissingPermission")
class BluetoothHelper(private var context: Context) {
    private val TAG = "BluetoothHelper"
    private val mBluetoothManager = context.getSystemService<BluetoothManager>()!!
    private val mBluetoothAdapter = mBluetoothManager.adapter
    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    println("name = ${device.name}, address = ${device.address}")
                }
            }
        }
    }

    fun onCreate() {
        registerBluetoothReceiver()
    }

    fun onDestroy() {
        unregisterBluetoothReceiver()
    }

    fun registerBluetoothReceiver() {
        context.registerReceiver(receiver, filter, RECEIVER_EXPORTED)
    }

    fun unregisterBluetoothReceiver() {
        context.unregisterReceiver(receiver)
    }

    fun isBluetoothEnable(): Boolean {
        return mBluetoothAdapter.isEnabled
    }

    fun enableBluetooth() {
        mBluetoothAdapter.enable()
    }

    fun disableBluetooth() {
        mBluetoothAdapter.disable()
    }

    fun getBluetoothName(): String {
        return mBluetoothAdapter.name
    }

    fun setBluetoothName(name: String) {
        mBluetoothAdapter.name = name
    }

    fun getBluetoothAddress(): String {
        return mBluetoothAdapter.address
    }

    fun getBluetoothScanMode(): Int {
        return mBluetoothAdapter.scanMode
    }

    fun getBluetoothState(): Int {
        return mBluetoothAdapter.state
    }

    fun getBluetoothBondedDevices(): Set<android.bluetooth.BluetoothDevice> {
        return mBluetoothAdapter.bondedDevices
    }

    fun startBluetoothDiscovery() {
        mBluetoothAdapter.startDiscovery()
    }

    fun cancelBluetoothDiscovery() {
        mBluetoothAdapter.cancelDiscovery()
    }

    fun isBluetoothDiscovering(): Boolean {
        return mBluetoothAdapter.isDiscovering
    }

    fun printBluetoothDevices() {
        val devices = mBluetoothAdapter.bondedDevices
        for (device in devices) {
            println("name = ${device.name}, address = ${device.address}")
        }
    }

    fun scanBluetoothDevices() {
        mBluetoothAdapter.startDiscovery()
    }
}

