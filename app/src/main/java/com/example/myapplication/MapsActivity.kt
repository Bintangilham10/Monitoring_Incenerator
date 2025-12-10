package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val machines = mutableListOf<Machine>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Cek Google Play Services
        if (!MapsUtils.checkGooglePlayServices(this)) {
            Toast.makeText(this, "Google Play Services tidak tersedia", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = true
        }

        loadMachines()
    }

    private fun loadMachines() {
        db.collection("Data_Incenerator")
            .get()
            .addOnSuccessListener { result ->
                machines.clear()
                val boundsBuilder = LatLngBounds.Builder()
                var hasValidLocation = false

                for (doc in result) {
                    val name = doc.id
                    val status = doc.getBoolean("Status") ?: false
                    val credential = doc.getString("Credential") ?: ""
                    val latitude = doc.getDouble("Latitude")
                    val longitude = doc.getDouble("Longitude")

                    if (latitude != null && longitude != null) {
                        val machine = Machine(name, status, latitude, longitude, credential, "")
                        machines.add(machine)

                        val position = LatLng(latitude, longitude)
                        val markerIcon = createCustomMarker(status)

                        mMap.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(name)
                                .snippet(if (status) "Status: Aktif" else "Status: Non Aktif")
                                .icon(markerIcon)
                        )

                        boundsBuilder.include(position)
                        hasValidLocation = true
                    }
                }

                if (hasValidLocation) {
                    val bounds = boundsBuilder.build()
                    val padding = 100
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                } else {
                    val defaultLoc = LatLng(-6.2088, 106.8456)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 12f))
                    Toast.makeText(this, "Tidak ada mesin dengan lokasi valid", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat data: ${e.message}", Toast.LENGTH_SHORT).show()
                val defaultLoc = LatLng(-6.2088, 106.8456)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 12f))
            }
    }

    private fun createCustomMarker(isActive: Boolean): BitmapDescriptor {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = if (isActive) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

        val pinRadius = size / 3f
        val pinX = size / 2f
        val pinY = size / 3f

        canvas.drawCircle(pinX, pinY, pinRadius, paint)

        val path = android.graphics.Path()
        path.moveTo(pinX - pinRadius * 0.5f, pinY + pinRadius)
        path.lineTo(pinX + pinRadius * 0.5f, pinY + pinRadius)
        path.lineTo(pinX, size - 10f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(pinX, pinY, pinRadius - 3f, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
