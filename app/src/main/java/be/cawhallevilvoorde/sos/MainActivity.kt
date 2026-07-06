package be.cawhallevilvoorde.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("caw_sos_local_data", Context.MODE_PRIVATE)
    }

    private var hasRequiredPermissions by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasRequiredPermissions = checkPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasRequiredPermissions = checkPermissions()

        if (!hasRequiredPermissions) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            MaterialTheme {
                CawSosApp()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return sms && fineLocation
    }

    @Composable
    private fun CawSosApp() {
        var firstName by remember { mutableStateOf(prefs.getString("firstName", "") ?: "") }
        var lastName by remember { mutableStateOf(prefs.getString("lastName", "") ?: "") }
        var sosNumber by remember { mutableStateOf(prefs.getString("sosNumber", "") ?: "") }

        var screen by remember {
            mutableStateOf(
                if (firstName.isBlank() || lastName.isBlank() || sosNumber.isBlank()) {
                    "setup"
                } else {
                    "home"
                }
            )
        }

        when (screen) {
            "setup" -> SetupScreen(firstName, lastName, sosNumber) { f, l, n ->
                saveUserData(f, l, n)
                firstName = f
                lastName = l
                sosNumber = n
                screen = "home"
            }

            "settings" -> SetupScreen(firstName, lastName, sosNumber) { f, l, n ->
                saveUserData(f, l, n)
                firstName = f
                lastName = l
                sosNumber = n
                screen = "home"
            }

            else -> HomeScreen(
                firstName = firstName,
                lastName = lastName,
                sosNumber = sosNumber,
                onSettingsClick = { screen = "settings" }
            )
        }
    }

    private fun saveUserData(firstName: String, lastName: String, sosNumber: String) {
        prefs.edit()
            .putString("firstName", firstName.trim())
            .putString("lastName", lastName.trim())
            .putString("sosNumber", sosNumber.trim())
            .apply()
    }

    @Composable
    private fun SetupScreen(
        currentFirstName: String,
        currentLastName: String,
        currentSosNumber: String,
        onSave: (String, String, String) -> Unit
    ) {
        var firstName by remember { mutableStateOf(currentFirstName) }
        var lastName by remember { mutableStateOf(currentLastName) }
        var sosNumber by remember { mutableStateOf(currentSosNumber) }

        val canSave = firstName.isNotBlank() && lastName.isNotBlank() && sosNumber.isNotBlank()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("CAW SOS", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Eerste configuratie", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Voornaam") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Naam") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = sosNumber,
                onValueChange = { sosNumber = it },
                label = { Text("SOS-nummer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(firstName, lastName, sosNumber) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Opslaan")
            }
        }
    }

    @Composable
    private fun HomeScreen(
        firstName: String,
        lastName: String,
        sosNumber: String,
        onSettingsClick: () -> Unit
    ) {
        var countdown by remember { mutableStateOf(0) }
        var isTest by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("") }

        LaunchedEffect(countdown) {
            if (countdown > 0) {
                delay(1000)
                countdown--

                if (countdown == 0) {
                    val result = sendSosSms(firstName, lastName, sosNumber, isTest)
                    statusMessage = result
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("CAW SOS", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            Text("Welkom")
            Text("$firstName $lastName", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(32.dp))

            if (countdown > 0) {
                Text("Bericht wordt verstuurd over $countdown seconden")
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        countdown = 0
                        statusMessage = "SOS geannuleerd."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Annuleren")
                }
            } else {
                Button(
                    onClick = {
                        isTest = false
                        statusMessage = ""
                        countdown = 5
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("SOS", color = Color.White)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        isTest = true
                        statusMessage = ""
                        countdown = 5
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test SOS")
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Instellingen")
                }
            }

            Spacer(Modifier.height(24.dp))

            if (!hasRequiredPermissions) {
                Text("SMS- en locatiepermissies zijn nodig voor deze app.", color = Color.Red)
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage)
            }
        }
    }

    private fun sendSosSms(
        firstName: String,
        lastName: String,
        sosNumber: String,
        isTest: Boolean
    ): String {
        return try {
            if (!checkPermissions()) {
                return "Geen toestemming voor SMS of locatie."
            }

            val dateTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val locationText = getLocationText()

            val message = buildString {
                if (isTest) {
                    append("TESTBERICHT\n\n")
                }
                append("CAW HALLE-VILVOORDE\n\n")
                append("SOS NOODMELDING\n\n")
                append("Naam medewerker: $firstName $lastName\n")
                append("Datum + tijd: $dateTime\n")
                append("Google Maps locatie: $locationText")
            }

            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(sosNumber, null, parts, null, null)

            if (isTest) "Testbericht verstuurd." else "SOS-bericht verstuurd."
        } catch (e: Exception) {
            "Fout bij versturen van SMS: ${e.message}"
        }
    }

    private fun getLocationText(): String {
        return try {
            val locationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!locationPermission) {
                return "Locatie niet beschikbaar"
            }

            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val location = gpsLocation ?: networkLocation

            if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Locatie niet beschikbaar"
            }
        } catch (e: Exception) {
            "Locatie niet beschikbaar"
        }
    }
}
