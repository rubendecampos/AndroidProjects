package com.example.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    val TAG = "MainActivity"

    lateinit var listViewDevices: ListView
    lateinit var btnGetDevices: Button

    private var arrayAdapter: ArrayAdapter<*>? = null
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listViewDevices = findViewById(R.id.devicesList)
        btnGetDevices = findViewById(R.id.getDevices)


        btnGetDevices.setOnClickListener{
            if(bluetoothAdapter==null){
                Toast.makeText(getApplicationContext(),"Bluetooth Not Supported",Toast.LENGTH_SHORT).show()
            }
            else{
                val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.getBondedDevices()
                val list: ArrayList<String> = ArrayList()
                val deviceList: ArrayList<BluetoothDevice> = ArrayList()
                if(pairedDevices.size > 0){
                    pairedDevices.forEach{
                        val deviceName: String = it.name
                        val macAddress: String = it.address
                        list.add("Name: "+deviceName+"\nMAC Address: "+macAddress+"\n")
                        deviceList.add(it)
                    }

                    arrayAdapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, list)
                    listViewDevices.adapter = arrayAdapter
                    listViewDevices.onItemClickListener = AdapterView.OnItemClickListener{_, _, position, _ ->
                        val device: BluetoothDevice = deviceList[position]
                        val address: String = device.address

                        val intent = Intent(this, SerialSocket::class.java)
                        intent.putExtra("Device address", address)
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
