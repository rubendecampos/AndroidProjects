package com.example.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import kotlinx.android.synthetic.*
import java.io.IOException
import java.util.*

class SerialSocket : AppCompatActivity() {

    companion object{
        val TAG = "SerialSocket"
        val myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var bluetoothSocket: BluetoothSocket? = null
        var bluetoothAdapter: BluetoothAdapter? = null
        var deviceAddress: String = ""
        var isConnected: Boolean = false
    }

    var btnHello: Button? = null
    var btnDisconnect: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serial_socket)

        deviceAddress = intent.getStringExtra("Device address")

        ConnectToDevice(this).execute()

        btnHello = findViewById(R.id.btnHello)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        btnHello!!.setOnClickListener{ sendData("Hello world") }
        btnDisconnect!!.setOnClickListener{ disconnect() }
    }

    fun sendData(data: String){
        if(bluetoothSocket != null){
            try {
                bluetoothSocket!!.outputStream.write(data.toByteArray())
            }catch(e: IOException){
                e.printStackTrace()
            }
        }
    }

    fun disconnect(){
        if(bluetoothSocket!=null){
            try{
                bluetoothSocket!!.close()
                bluetoothSocket = null
                isConnected = false
            }catch(e: IOException){
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context): AsyncTask<Void, Void, String>() {
        private var connectSuccess = true
        private val context: Context

        init{
            this.context = c
        }

        override fun doInBackground(vararg params: Void?): String? {
            try{
                if(bluetoothSocket == null || !isConnected){
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    bluetoothSocket!!.connect()
                }
            }catch(e: IOException){
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            Log.d(TAG, "Connecting...")
            Toast.makeText(context,"Connecting... Please wait",Toast.LENGTH_SHORT).show()
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if(!connectSuccess){
                Log.e(TAG, "Couldn't connect")
                Toast.makeText(context,"Couldn't connect",Toast.LENGTH_SHORT).show()
            }else{
                isConnected = true
                Log.e(TAG, "Connected")
                Toast.makeText(context,"Connected",Toast.LENGTH_SHORT).show()
            }
        }

    }
}
