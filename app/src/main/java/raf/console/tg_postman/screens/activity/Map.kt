package raf.console.tg_postman.screens.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await
import raf.console.tg_postman.screens.MapPickerScreen

class MapPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val cameraPositionState = rememberCameraPositionState()
                    var hasMovedToCurrentLocation by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    // Получаем текущее местоположение
                    LaunchedEffect(Unit) {
                        // Проверяем разрешения
                        val hasPermission = ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission && !hasMovedToCurrentLocation) {
                            val fusedClient =
                                LocationServices.getFusedLocationProviderClient(context)
                            val location = fusedClient.lastLocation.await()

                            location?.let {
                                val currentLatLng = LatLng(it.latitude, it.longitude)
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                                )
                                hasMovedToCurrentLocation = true
                            }
                        }
                    }

                    MapPickerScreen(
                        onLocationSelected = { lat, lon ->
                            val result = Intent().apply {
                                putExtra("latitude", lat)
                                putExtra("longitude", lon)
                            }
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
