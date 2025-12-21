package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.databinding.NavbarBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.net.Uri


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navbarHelper: NavbarHelper
    private lateinit var bluetoothHelper: BluetoothHelper

    private val db = FirebaseFirestore.getInstance()
    private val machines = mutableListOf<Machine>()
    private var machineListener: ListenerRegistration? = null

    private var isBluetoothConnected = false

    // ======================================================
    // LIFECYCLE
    // ======================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavbar()
        setupButtons()
        setupBluetooth()
        setupUploadButton()
        listenMachinesFromFirestore()

        binding.tvOpenMap.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

        binding.cardMonitoring.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

    }

    override fun onPause() {
        super.onPause()
        bluetoothHelper.disconnect()
    }

    // ======================================================
    // BUTTON & NAV
    // ======================================================
    private fun setupButtons() {

        binding.tvOpenMap.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

        binding.cardMonitoring.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }

        binding.tvAddMachine.setOnClickListener {
            startActivity(Intent(this, AddMachineActivity::class.java))
        }
    }

    private fun setupNavbar() {
        navbarHelper = NavbarHelper(
            binding.bottomNavigation,
            onNavigationItemSelected = {
                when (it) {
                    1 -> startActivity(Intent(this, DataActivity::class.java))
                    3 -> startActivity(Intent(this, UserActivity::class.java))
                }
            },
            onFabClicked = {
                startActivity(Intent(this, QRScannerActivity::class.java))
            }
        )
        navbarHelper.selectNavItem(0)
    }

    // ======================================================
    // BLUETOOTH
    // ======================================================
    private fun setupBluetooth() {

        bluetoothHelper = BluetoothHelper(this, object : BluetoothHelper.Listener {

            override fun onPermissionRequired(permissions: Array<String>, requestCode: Int) {
                ActivityCompat.requestPermissions(this@MainActivity, permissions, requestCode)
            }

            override fun onPairedDevices(devices: List<android.bluetooth.BluetoothDevice>) {
                showPairedDevicesDialog(devices)
            }

            override fun onConnecting(device: android.bluetooth.BluetoothDevice) {
                Toast.makeText(this@MainActivity, "Menghubungkan ke ESP32...", Toast.LENGTH_SHORT).show()
            }

            override fun onConnected(device: android.bluetooth.BluetoothDevice) {
                isBluetoothConnected = true
                updateBluetoothWarning()
                Toast.makeText(this@MainActivity, "Bluetooth terhubung", Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                isBluetoothConnected = false
                updateBluetoothWarning()
            }

            override fun onConnectionFailed(reason: String) {
                isBluetoothConnected = false
                updateBluetoothWarning()
                Toast.makeText(this@MainActivity, reason, Toast.LENGTH_SHORT).show()
            }

            override fun onDataReceived(text: String) {
                runOnUiThread {
                    binding.tvBeratSampah.text = "${text.trim()} gram"
                }
            }
        })

        bluetoothHelper.ensurePermissions()

        binding.tvBeratSampah.setOnClickListener { tryOpenBluetooth() }
        binding.hoverBluetoothWarning.setOnClickListener { tryOpenBluetooth() }

        updateBluetoothWarning()
    }

    private fun tryOpenBluetooth() {
        if (hasBluetoothPermission()) {
            bluetoothHelper.listPairedDevices()
        } else {
            bluetoothHelper.ensurePermissions()
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun showPairedDevicesDialog(devices: List<android.bluetooth.BluetoothDevice>) {

        if (devices.isEmpty()) {
            Toast.makeText(this, "Tidak ada device ter-pair", Toast.LENGTH_SHORT).show()
            return
        }

        val names = devices.map {
            try {
                "${it.name ?: "ESP32"} - ${it.address}"
            } catch (e: SecurityException) {
                "ESP32"
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih ESP32")
            .setItems(names) { _, which ->
                if (hasBluetoothPermission()) {
                    bluetoothHelper.connect(devices[which].address)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateBluetoothWarning() {
        binding.hoverBluetoothWarning.visibility =
            if (isBluetoothConnected) View.GONE else View.VISIBLE
    }

    // ======================================================
    // UPLOAD BERAT
    // ======================================================
    private fun setupUploadButton() {

        binding.btnUploadData.setOnClickListener {

            val raw = binding.tvBeratSampah.text.toString()
                .replace("gram", "")
                .trim()

            val berat = raw.toDoubleOrNull()
            if (berat == null) {
                Toast.makeText(this, "Data belum valid", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = hashMapOf(
                "berat" to berat,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("IoTDevices")
                .document("sensor_berat")
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Data berhasil diupload", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ======================================================
    // FIRESTORE MESIN
    // ======================================================
    private fun listenMachinesFromFirestore() {

        machineListener?.remove()

        machineListener = db.collection("Data_Incenerator")
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) return@addSnapshotListener

                binding.machineContainer.removeAllViews()
                binding.machineLocationsList.removeAllViews() // 🔥 HAPUS "Memuat lokasi mesin"
                machines.clear()

                if (snapshot.isEmpty) {
                    binding.machineLocationsList.addView(TextView(this).apply {
                        text = "Belum ada data mesin"
                        setTextColor(Color.GRAY)
                        gravity = Gravity.CENTER
                    })
                    return@addSnapshotListener
                }

                snapshot.documents.forEach { doc ->

                    val machine = Machine(
                        name = doc.id,
                        status = doc.getBoolean("Status") ?: false,
                        latitude = doc.getDouble("Latitude") ?: 0.0,
                        longitude = doc.getDouble("Longitude") ?: 0.0,
                        credential = doc.getString("Credential") ?: "",
                        mapLink = doc.getString("mapLink") ?: ""
                    )

                    machines.add(machine)
                    binding.machineContainer.addView(createMachineCard(machine))
                }

                // 🔥 UPDATE LIST LOKASI
                updateMachineLocationsList()
            }
    }

    private fun updateMachineLocationsList() {

        val container = binding.machineLocationsList
        container.removeAllViews()

        val validMachines = machines.filter {
            it.latitude != 0.0 && it.longitude != 0.0
        }

        if (validMachines.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Tidak ada mesin dengan lokasi valid"
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(24, 48, 24, 48)
                textSize = 14f
            })
            return
        }

        validMachines.forEach { machine ->

            // ================= CARD =================
            val card = CardView(this).apply {
                radius = 18f
                cardElevation = 6f
                setCardBackgroundColor(Color.WHITE)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            }

            // ================= ROW =================
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }

            // ================= NAMA MESIN =================
            val name = TextView(this).apply {
                text = machine.name
                textSize = 15f
                setTextColor(Color.BLACK)
            }

            // ================= KOORDINAT =================
            val coord = TextView(this).apply {
                text = "Lat: ${machine.latitude}\nLng: ${machine.longitude}"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                setPadding(0, 8, 0, 12)
            }

            // ================= BUTTON MAP =================
            val btnMap = TextView(this).apply {
                text = "Buka di Google Maps"
                textSize = 13f
                setTextColor(Color.parseColor("#2196F3"))
                setPadding(0, 8, 0, 0)
                setOnClickListener {
                    openMap(machine)
                }
            }

            row.addView(name)
            row.addView(coord)
            row.addView(btnMap)
            card.addView(row)
            container.addView(card)
        }
    }



    private fun openMap(machine: Machine) {
        val uri = Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${machine.latitude},${machine.longitude}"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }




    private fun createMachineCard(machine: Machine): View {

        val card = CardView(this).apply {
            radius = 20f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val name = TextView(this).apply {
            text = machine.name
            setTextColor(Color.BLACK)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val status = TextView(this).apply {
            text = if (machine.status) "Aktif" else "Non Aktif"
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            background = resources.getDrawable(
                if (machine.status)
                    R.drawable.bg_status_active
                else
                    R.drawable.bg_status_inactive,
                theme
            )
        }

        layout.addView(name)
        layout.addView(status)
        card.addView(layout)

        return card
    }
}
