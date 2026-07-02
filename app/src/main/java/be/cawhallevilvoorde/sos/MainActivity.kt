package be.cawhallevilvoorde.sos

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private val prefsName = "caw_sos_prefs"

    private val keyFirstName = "first_name"
    private val keyLastName = "last_name"
    private val keyTeam = "team"
    private val keyTcName = "tc_name"
    private val keyTcPhone = "tc_phone"

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var profileText: TextView

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastShakeTime = 0L
    private var shakeCount = 0
    private var lastSosTime = 0L

    private val shakeThreshold = 22.0f
    private val requiredShakes = 3
    private val shakeWindowMs = 2000L
    private val cooldownMs = 15000L

    private val teams = arrayOf(
        "Algemeen",
        "OverKop Halle",
        "OverKop Vilvoorde",
        "ILC",
        "Bravio",
        "Pivo",
        "Voogdij",
        "Andere"
    )

    private val teamCoordinators = arrayOf(
        Pair("TC Halle", "+32400000001"),
        Pair("TC Vilvoorde", "+32400000002"),
        Pair("TC OverKop", "+32400000003"),
        Pair("TC Pivo", "+32400000004"),
        Pair("Eigen nummer invullen", "manual")
    )

    private val permissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        createUi()
        requestNeededPermissions()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (!isProfileComplete()) {
            showFirstSetup()
        }
    }

    private fun createUi() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(42, 54, 42, 54)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "CAW SOS"
            textSize = 34f
            setTextColor(Color.rgb(176, 0, 32))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val intro = TextView(this).apply {
            text = "Druk op SOS of schud de gsm 3 keer.\nNa aftelling wordt een SMS met locatie verstuurd naar de Teamcoördinator."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(40, 40, 40))
            setPadding(0, 0, 0, 22)
        }

        profileText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(40, 40, 40))
            setPadding(0, 0, 0, 24)
        }
        refreshProfileText()

        val sosButton = Button(this).apply {
            text = "SOS"
            textSize = 36f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(176, 0, 32))
            isAllCaps = false
            minHeight = 235
            minWidth = 235
            setOnClickListener { startSosCountdown("Knop") }
        }

        val testButton = Button(this).apply {
            text = "Test SOS"
            isAllCaps = false
            setOnClickListener { startSosCountdown("Test") }
        }

        val editButton = Button(this).apply {
            text = "Gegevens wijzigen"
            isAllCaps = false
            setOnClickListener { showFirstSetup() }
        }

        statusText = TextView(this).apply {
            text = "Status: klaar"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 0)
        }

        layout.addView(title)
        layout.addView(intro)
        layout.addView(profileText)
        layout.addView(sosButton)
        layout.addView(testButton)
        layout.addView(editButton)
        layout.addView(statusText)

        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun showFirstSetup() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        val firstName = EditText(this).apply {
            hint = "Voornaam"
            setText(prefs.getString(keyFirstName, ""))
        }

        val lastName = EditText(this).apply {
            hint = "Naam"
            setText(prefs.getString(keyLastName, ""))
        }

        val teamSpinner = Spinner(this)
        teamSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teams)
        val savedTeam = prefs.getString(keyTeam, teams[0])
        teamSpinner.setSelection(teams.indexOf(savedTeam).takeIf { it >= 0 } ?: 0)

        val tcSpinner = Spinner(this)
        val tcLabels = teamCoordinators.map { "${it.first} (${it.second})" }.toTypedArray()
        tcSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tcLabels)

        box.addView(label("Voornaam"))
        box.addView(firstName)
        box.addView(label("Naam"))
        box.addView(lastName)
        box.addView(label("Team"))
        box.addView(teamSpinner)
        box.addView(label("Teamcoördinator"))
        box.addView(tcSpinner)

        AlertDialog.Builder(this)
            .setTitle("Welkom bij CAW SOS")
            .setMessage("Vul éénmalig je gegevens in. Deze gegevens blijven lokaal op de werkgsm.")
            .setView(box)
            .setPositiveButton("Opslaan") { _, _ ->
                val selectedTc = teamCoordinators[tcSpinner.selectedItemPosition]
                if (selectedTc.second == "manual") {
                    saveBasicProfile(firstName.text.toString(), lastName.text.toString(), teamSpinner.selectedItem.toString())
                    showManualTcDialog()
                } else {
                    prefs.edit()
                        .putString(keyFirstName, firstName.text.toString().trim())
                        .putString(keyLastName, lastName.text.toString().trim())
                        .putString(keyTeam, teamSpinner.selectedItem.toString())
                        .putString(keyTcName, selectedTc.first)
                        .putString(keyTcPhone, selectedTc.second)
                        .apply()
                    refreshProfileText()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            setPadding(0, 18, 0, 0)
        }
    }

    private fun saveBasicProfile(firstName: String, lastName: String, team: String) {
        prefs.edit()
            .putString(keyFirstName, firstName.trim())
            .putString(keyLastName, lastName.trim())
            .putString(keyTeam, team)
            .apply()
    }

    private fun showManualTcDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        val name = EditText(this).apply { hint = "Naam Teamcoördinator" }
        val phone = EditText(this).apply {
            hint = "Gsm-nummer bv. +32475123456"
            inputType = InputType.TYPE_CLASS_PHONE
        }

        box.addView(name)
        box.addView(phone)

        AlertDialog.Builder(this)
            .setTitle("Teamcoördinator manueel invullen")
            .setView(box)
            .setPositiveButton("Opslaan") { _, _ ->
                prefs.edit()
                    .putString(keyTcName, name.text.toString().trim().ifEmpty { "Teamcoördinator" })
                    .putString(keyTcPhone, phone.text.toString().trim())
                    .apply()
                refreshProfileText()
            }
            .setNegativeButton("Annuleren", null)
            .show()
    }

    private fun refreshProfileText() {
        val fullName = "${prefs.getString(keyFirstName, "")} ${prefs.getString(keyLastName, "")}".trim()
        val team = prefs.getString(keyTeam, "")
        val tc = prefs.getString(keyTcName, "")
        val tcPhone = prefs.getString(keyTcPhone, "")

        profileText.text = if (fullName.isEmpty() || tcPhone.isNullOrEmpty()) {
            "Profiel: nog niet ingesteld"
        } else {
            "Medewerker: $fullName\nTeam: $team\nTeamcoördinator: $tc\n$tcPhone"
        }
    }

    private fun isProfileComplete(): Boolean {
        return !prefs.getString(keyFirstName, "").isNullOrEmpty()
                && !prefs.getString(keyLastName, "").isNullOrEmpty()
                && !prefs.getString(keyTcPhone, "").isNullOrEmpty()
    }

    private fun startSosCountdown(source: String) {
        if (!isProfileComplete()) {
            Toast.makeText(this, "Vul eerst je gegevens in.", Toast.LENGTH_LONG).show()
            showFirstSetup()
            return
        }

        val countdownText = TextView(this).apply {
            text = "SOS wordt verzonden over 5 seconden..."
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (source == "Test") "Test SOS" else "SOS activeren")
            .setView(countdownText)
            .setNegativeButton("Annuleren", null)
            .create()

        dialog.show()

        object : CountDownTimer(5000, 1000) {
            override fun onTick(ms: Long) {
                countdownText.text = "Verzenden over ${(ms / 1000) + 1}..."
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss()
                    triggerSos(source)
                }
            }
        }.start()
    }

    private fun triggerSos(source: String) {
        val now = System.currentTimeMillis()
        if (now - lastSosTime < cooldownMs) {
            Toast.makeText(this, "Even wachten: er is net een melding verstuurd.", Toast.LENGTH_LONG).show()
            return
        }

        requestNeededPermissions()

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS-machtiging ontbreekt.", Toast.LENGTH_LONG).show()
            return
        }

        val phone = prefs.getString(keyTcPhone, "") ?: ""
        val message = buildSmsMessage(source)

        try {
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            lastSosTime = now
            vibrate()
            statusText.text = if (source == "Test") "Status: test-SMS verstuurd." else "Status: SOS-SMS verstuurd."
            Toast.makeText(this, "SMS verstuurd naar Teamcoördinator.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            statusText.text = "Status: SMS verzenden mislukt."
            Toast.makeText(this, "SMS verzenden mislukt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSmsMessage(source: String): String {
        val fullName = "${prefs.getString(keyFirstName, "")} ${prefs.getString(keyLastName, "")}".trim()
        val team = prefs.getString(keyTeam, "")
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val location = getLocationUrl()

        return if (source == "Test") {
            "TESTBERICHT - CAW SOS\nDit is een test van de CAW SOS-app.\nMedewerker: $fullName\nTeam: $team\nTijd: $date\nLocatie: $location"
        } else {
            "NOODMELDING - CAW Halle-Vilvoorde\nMedewerker: $fullName\nTeam: $team\nTijd: $date\nLocatie: $location\nActivatie: $source"
        }
    }

    private fun getLocationUrl(): String {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Locatie niet beschikbaar: geen machtiging."
        }

        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var loc: Location? = null
            try {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {}
            if (loc == null) {
                try {
                    loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (_: Exception) {}
            }

            if (loc != null) {
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } else {
                "Locatie nog niet beschikbaar. Zet locatie aan en probeer opnieuw."
            }
        } catch (_: Exception) {
            "Locatie kon niet worden opgehaald."
        }
    }

    private fun requestNeededPermissions() {
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1001)
    }

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val acceleration = sqrt(
            event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
        )
        val now = System.currentTimeMillis()

        if (acceleration > shakeThreshold) {
            if (now - lastShakeTime > shakeWindowMs) shakeCount = 0
            shakeCount++
            lastShakeTime = now

            if (shakeCount >= requiredShakes) {
                shakeCount = 0
                startSosCountdown("Shake")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
