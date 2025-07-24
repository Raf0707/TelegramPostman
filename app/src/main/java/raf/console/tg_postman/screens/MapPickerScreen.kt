package raf.console.tg_postman.screens


import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

// MapPickerScreen.kt
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapPickerScreen(onLocationSelected: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 3f)
    }
    val coroutineScope = rememberCoroutineScope()



    LaunchedEffect(permissionState.status) @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]) {
        if (permissionState.status.isGranted) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.

            }
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    coroutineScope.launch {
                        cameraState.animate(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f))
                    }

                }
            }
        } else {
            permissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedLocation != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val lat = selectedLocation!!.latitude
                        val lon = selectedLocation!!.longitude
                        onLocationSelected(lat, lon)
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(),
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    Text("Подтвердить точку")
                }
            }
        }


    ) { padding ->
        GoogleMap(
            modifier = Modifier.fillMaxSize().padding(padding),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionState.status.isGranted),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true),
            onMapClick = { latLng ->
                selectedLocation = latLng
            }
        ) {
            selectedLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Выбрано",
                    snippet = "${it.latitude.format()}, ${it.longitude.format()}"
                )
            }
            currentLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Моя позиция"
                )
            }
        }
    }
}

// Helper extension
private fun Double.format() = String.format("%.6f", this)
