package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.databinding.NavbarBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navbarHelper: NavbarHelper
    private val db = FirebaseFirestore.getInstance()
    private var machineListener: ListenerRegistration? = null
    private val machines = mutableListOf<Machine>()

    // Bluetooth
    private lateinit var bluetoothHelper: BluetoothHelper
    private val BLUETOOTH_REQ_CODE = BluetoothHelper.REQ_PERMISSION_CODE
    private var isBluetoothConnected = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavbar()
        setupCardListeners()
        setupAddMachineButton()
        setupMapButton()
        setupBeratSampahSection()
        setupUploadButton()

        /* ============================
               INIT BLUETOOTH
        ============================ */
        bluetoothHelper = BluetoothHelper(this, object : BluetoothHelper.Listener {

            override fun onPermissionRequired(permissions: Array<String>, requestCode: Int) {
                ActivityCompat.requestPermissions(this@MainActivity, permissions, requestCode)
            }

            override fun onPairedDevices(devices: List<android.bluetooth.BluetoothDevice>) {
                if (!hasBluetoothPermission()) return
                showPairedDevicesDialog(devices)
            }

            override fun onConnecting(device: android.bluetooth.BluetoothDevice) {
                if (!hasBluetoothPermission()) return

                try {
                    Toast.makeText(this@MainActivity, "Menghubungkan ke ${device.name}...", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(this@MainActivity, "Menghubungkan ke perangkat...", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onConnected(device: android.bluetooth.BluetoothDevice) {
                if (!hasBluetoothPermission()) return

                try {
                    Toast.makeText(this@MainActivity, "Terhubung ke ${device.name}", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(this@MainActivity, "Terhubung ke perangkat", Toast.LENGTH_SHORT).show()
                }

                isBluetoothConnected = true
                updateBluetoothWarning()
            }

            override fun onDisconnected() {
                Toast.makeText(this@MainActivity, "Bluetooth terputus", Toast.LENGTH_SHORT).show()
                isBluetoothConnected = false
                updateBluetoothWarning()
            }

            override fun onConnectionFailed(reason: String) {
                Toast.makeText(this@MainActivity, "Gagal koneksi: $reason", Toast.LENGTH_SHORT).show()
                isBluetoothConnected = false
                updateBluetoothWarning()
            }

            override fun onDataReceived(text: String) {
                binding.tvBeratSampah.text = text.trim()   // HANYA angka
            }
        })

        bluetoothHelper.ensurePermissions()

        binding.tvBeratSampah.setOnClickListener {
            if (hasBluetoothPermission()) bluetoothHelper.listPairedDevices()
            else bluetoothHelper.ensurePermissions()
        }

        binding.hoverBluetoothWarning.setOnClickListener {
            if (hasBluetoothPermission()) bluetoothHelper.listPairedDevices()
            else bluetoothHelper.ensurePermissions()
        }

        listenMachinesFromFirestore()
    }

    /* ======================================================
               BLUETOOTH — CEK PERMISSION
    ====================================================== */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    /* ============================================
          BLUETOOTH — DIALOG PILIH DEVICE (SAFE)
    ============================================= */

    private fun showPairedDevicesDialog(devices: List<android.bluetooth.BluetoothDevice>) {
        if (!hasBluetoothPermission()) return

        if (devices.isEmpty()) {
            Toast.makeText(this, "Tidak ada device ter-paired. Pair dahulu di Settings.", Toast.LENGTH_SHORT).show()
            return
        }

        val names = devices.map { device ->
            try {
                "${device.name ?: "Unknown"} — ${device.address}"
            } catch (e: SecurityException) {
                "Perangkat — Alamat disembunyikan"
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih Device ESP32")
            .setItems(names) { _, which ->
                if (hasBluetoothPermission()) {
                    try {
                        val addr = try { devices[which].address } catch (e: SecurityException) { null }
                        if (addr != null) {
                            bluetoothHelper.connect(addr)
                        } else {
                            Toast.makeText(this, "Alamat perangkat tidak dapat diakses", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(this, "Alamat perangkat tidak dapat diakses", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    bluetoothHelper.ensurePermissions()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /* ============================================
             PERMISSION RESULT HANDLING
    ============================================= */

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == BLUETOOTH_REQ_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permission Bluetooth diberikan", Toast.LENGTH_SHORT).show()
                bluetoothHelper.listPairedDevices()
            } else {
                Toast.makeText(this, "Permission Bluetooth dibutuhkan untuk koneksi", Toast.LENGTH_SHORT).show()
            }
        }
    }


    /* ============================================
                     onPause
    ============================================= */

    override fun onPause() {
        super.onPause()
        machineListener?.remove()
        machineListener = null
        try { bluetoothHelper.disconnect() } catch (_: Exception) {}
    }


    /* ============================================
                 UPLOAD BERAT SAMPAH
    ============================================= */

    private fun setupUploadButton() {
        binding.btnUploadData.setOnClickListener {

            val rawValue = binding.tvBeratSampah.text.toString().trim()

            val beratValue = rawValue.toDoubleOrNull()

            if (beratValue == null) {
                Toast.makeText(this, "Data tidak valid / belum ada data dari sensor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Format timestamp WIB (jam:menit:detik)
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
            val waktuWIB = sdf.format(java.util.Date())

            val data = hashMapOf(
                "berat sampah" to beratValue,   // Number
                "timestamp" to waktuWIB         // String (HH:mm:ss)
            )

            FirebaseFirestore.getInstance()
                .collection("IoTDevices")
                .document("sensor berat")
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Data berhasil diupload!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }



    /* ============================================
             INDICATOR BLUETOOTH & WARNING
    ============================================= */

    private fun setupBeratSampahSection() {
        updateBluetoothWarning()
    }

    private fun updateBluetoothWarning() {
        binding.hoverBluetoothWarning.visibility =
            if (isBluetoothConnected) android.view.View.GONE else android.view.View.VISIBLE
    }


    /* ============================================
                     GOOGLE MAPS
    ============================================= */

    private fun setupMapButton() {
        binding.tvOpenMap.setOnClickListener { openFullMap() }
        binding.cardMonitoring.setOnClickListener { openFullMap() }
    }

    private fun openFullMap() {
        if (MapsUtils.isGooglePlayServicesAvailable(this)) {
            try {
                startActivity(Intent(this, MapsActivity::class.java))
            } catch (e: Exception) {
                openGoogleMapsExternal()
            }
        } else {
            openGoogleMapsExternal()
        }
    }

    private fun openGoogleMapsExternal() {
        if (machines.isEmpty()) {
            Toast.makeText(this, "Belum ada data mesin", Toast.LENGTH_SHORT).show()
            return
        }

        val validMachines = machines.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        if (validMachines.isEmpty()) {
            Toast.makeText(this, "Tidak ada mesin dengan lokasi valid", Toast.LENGTH_SHORT).show()
            return
        }

        val firstMachine = validMachines.first()
        val mapUrl = "https://www.google.com/maps/search/?api=1&query=${firstMachine.latitude},${firstMachine.longitude}"

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka Google Maps", Toast.LENGTH_SHORT).show()
        }
    }


    /* ============================================
             FIRESTORE — LIST MESIN
    ============================================= */

    private fun updateMachineLocationsList() {
        val container = binding.machineLocationsList
        container.removeAllViews()

        if (machines.isEmpty()) {
            val statusText = TextView(this).apply {
                text = "Belum ada data mesin"
                setTextColor(Color.GRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }
            container.addView(statusText)
            return
        }

        val validMachines = machines.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        if (validMachines.isEmpty()) {
            val statusText = TextView(this).apply {
                text = "Tidak ada mesin dengan lokasi valid"
                setTextColor(Color.GRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }
            container.addView(statusText)
            return
        }

        val headerText = TextView(this).apply {
            text = "${validMachines.size} Mesin Terdaftar"
            setTextColor(Color.parseColor("#333333"))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        container.addView(headerText)

        validMachines.take(5).forEach { machine ->
            val machineRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            val statusDot = android.view.View(this).apply {
                val params = LinearLayout.LayoutParams(12, 12)
                params.setMargins(0, 0, 12, 0)
                layoutParams = params
                setBackgroundResource(
                    if (machine.status) android.R.drawable.presence_online
                    else android.R.drawable.presence_busy
                )
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameText = TextView(this).apply {
                text = machine.name
                setTextColor(Color.parseColor("#333333"))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val coordText = TextView(this).apply {
                text = "📍 ${String.format("%.4f", machine.latitude)}, ${String.format("%.4f", machine.longitude)}"
                setTextColor(Color.parseColor("#666666"))
                textSize = 11f
            }

            info.addView(nameText)
            info.addView(coordText)

            val linkButton = TextView(this).apply {
                text = "Lihat"
                setTextColor(Color.parseColor("#2196F3"))
                textSize = 12f
                setOnClickListener { openMachineLocation(machine) }
            }

            machineRow.addView(statusDot)
            machineRow.addView(info)
            machineRow.addView(linkButton)

            container.addView(machineRow)

            if (machine != validMachines.last()) {
                val divider = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply { setMargins(0, 8, 0, 8) }
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
                container.addView(divider)
            }
        }

        if (validMachines.size > 5) {
            val showMoreButton = TextView(this).apply {
                text = "Lihat ${validMachines.size - 5} mesin lainnya"
                setTextColor(Color.parseColor("#2196F3"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
                setOnClickListener { openFullMap() }
            }
            container.addView(showMoreButton)
        }
    }

    private fun openMachineLocation(machine: Machine) {
        val url = if (machine.mapLink.isNotEmpty()) machine.mapLink
        else "https://www.google.com/maps/search/?api=1&query=${machine.latitude},${machine.longitude}"

        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka link", Toast.LENGTH_SHORT).show()
        }
    }


    /* ============================================
              NAVIGATION & QR SCANNER
    ============================================= */

    private fun setupNavbar() {
        val navbarBinding: NavbarBinding = binding.bottomNavigation
        navbarHelper = NavbarHelper(
            binding = navbarBinding,
            onNavigationItemSelected = { position -> handleNavigation(position) },
            onFabClicked = { handleQRScan() }
        )
        navbarHelper.selectNavItem(0)
    }

    private fun setupAddMachineButton() {
        binding.tvAddMachine.setOnClickListener {
            startActivity(Intent(this, AddMachineActivity::class.java))
        }
    }

    private fun setupCardListeners() = with(binding) {
        cardDataDisplay.setOnClickListener {
            Toast.makeText(this@MainActivity, "Data Display clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleNavigation(position: Int) {}

    private fun handleQRScan() {
        Toast.makeText(this, "Opening QR Scanner...", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, QRScannerActivity::class.java))
    }


    /* ============================================
                   FIRESTORE LISTENER
    ============================================= */

    private fun listenMachinesFromFirestore() {
        val container = binding.machineContainer
        machineListener?.remove()

        machineListener = db.collection("Data Incenerator")
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat data: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                container.removeAllViews()
                machines.clear()

                if (result == null || result.isEmpty) {
                    val emptyText = TextView(this).apply {
                        text = "Belum ada data mesin."
                        setTextColor(Color.GRAY)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, 32, 0, 32)
                    }
                    container.addView(emptyText)
                    updateMachineLocationsList()
                    return@addSnapshotListener
                }

                for (doc in result) {

                    val machineName = doc.id
                    val status = doc.getBoolean("Status") ?: false
                    val credential = doc.getString("Credential") ?: ""
                    val mapLink = doc.getString("mapLink") ?: ""

                    var latitude = doc.getDouble("Latitude") ?: 0.0
                    var longitude = doc.getDouble("Longitude") ?: 0.0

                    if (latitude == 0.0 && longitude == 0.0 && mapLink.isNotEmpty()) {
                        val coor = Machine.extractCoordinatesFromMapLink(mapLink)
                        latitude = coor?.first ?: 0.0
                        longitude = coor?.second ?: 0.0
                    }

                    machines.add(Machine(machineName, status, latitude, longitude, credential, mapLink))

                    val card = androidx.cardview.widget.CardView(this).apply {
                        radius = 20f
                        cardElevation = 6f
                        setCardBackgroundColor(Color.WHITE)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 0, 0, 20)
                        layoutParams = params
                    }

                    val innerLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(20, 24, 20, 24)
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val nameView = TextView(this).apply {
                        text = machineName
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val statusView = TextView(this).apply {
                        text = if (status) "Aktif" else "Non Aktif"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setPadding(28, 12, 28, 12)
                        background = resources.getDrawable(
                            if (status) R.drawable.bg_status_active else R.drawable.bg_status_inactive,
                            theme
                        )
                    }

                    val deleteButton = android.widget.ImageView(this).apply {
                        setImageResource(R.drawable.ic_trashbin)
                        setColorFilter(Color.parseColor("#F44336"))
                        setPadding(24, 12, 0, 12)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Konfirmasi Hapus")
                                .setMessage("Apakah Anda yakin ingin menghapus mesin \"$machineName\"?")
                                .setPositiveButton("Ya") { dialog, _ ->
                                    db.collection("Data Incenerator").document(machineName)
                                        .delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Mesin \"$machineName\" berhasil dihapus",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Gagal menghapus mesin: ${it.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Tidak") { dialog, _ -> dialog.dismiss() }
                                .show()
                        }
                    }

                    innerLayout.addView(nameView)
                    innerLayout.addView(statusView)
                    innerLayout.addView(deleteButton)

                    card.addView(innerLayout)
                    container.addView(card)
                }

                updateMachineLocationsList()
            }
    }

}
