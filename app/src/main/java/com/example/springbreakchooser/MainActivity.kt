package com.example.springbreakchooser

import android.os.Bundle
import android.util.Log
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.springbreakchooser.databinding.ActivityMainBinding
import android.speech.RecognizerIntent
import ShakeDetector
import android.content.Intent
import android.hardware.Sensor
import android.media.MediaPlayer
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import java.util.*
import kotlin.math.sqrt
import android.net.Uri
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject


private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity(), OnItemSelectedListener, TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var shakeDetector: ShakeDetector

    private var selectedLanguage = ""

    private var languages = arrayOf("Language","English", "French", "Spanish", "Chinese")
    private val languageTags = mapOf(
        "English" to "en-US",
        "French" to "fr-FR",
        "Spanish" to "es-ES",
        "Chinese" to "zh-CN",
    )

    private val languageToLocationMap = mapOf(
        "Spanish" to "geo:19.432608,-99.133209", // Mexico City, Mexico
        "French" to "geo:48.856613,2.352222",   // Paris, France
        "Chinese" to "geo:39.904200,116.407396" // Beijing, China
    )

    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f


    private val famousSites = mapOf(
        "English" to listOf("Boston Uni, Boston, US", "London School of Economic, London, UK"),
        "French" to listOf("Eiffel Tower, Paris, France", "Louvre Museum, Paris, France"),
        "Spanish" to listOf("Santiago Bernabeu, Madrid, Spain", "Camp Nou, Barcellona, Spain"),
        "Chinese" to listOf("Chinatown, Singapore, Singapore", "TsimTsaTsui, HongKong, Hongkong")
    )
    private var  mapUri: Uri = Uri.parse("geo:0,0?q=${Uri.encode("Statue of Liberty, New York, USA")}")

    private val languageToHelloMap = mapOf(
        "English" to "Hello",
        "French" to "Bonjour",
        "Spanish" to "Hola",
        "Chinese" to "你好"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable the EditText so user is forced to input voice
        binding.userInput.isEnabled = false

        // Text to Speech
        textToSpeech = TextToSpeech(this, this)

        // Shake Detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        Objects.requireNonNull(sensorManager)!!
            .registerListener(sensorListener, sensorManager!!
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)

        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        // Spinner setup
        binding.spinner.onItemSelectedListener = this
        val ad: ArrayAdapter<*> = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages
        )
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = ad
    }

    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
            val data: Intent? = result.data
            val res = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            binding.userInput.setText(res?.get(0) ?: "")
        }
    }

    // Text to Speech Initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech is initialized")
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech")
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        Toast.makeText(applicationContext, languages[position], Toast.LENGTH_LONG).show()
        selectedLanguage = languages[position] ?: ""
        Log.d(TAG, "Selected language: $selectedLanguage")
//        updateAddressForSelectedLanguage(selectedLanguage
        val sitesList = famousSites[selectedLanguage] ?: return
        val siteAddress = sitesList.random()

        // Update the mapUri with the new address
        mapUri = Uri.parse("geo:0,0?q=${Uri.encode(siteAddress)}")

        // Check to see if a language has been selected
        if (position > 0) {
            // Launch voice input
            val selectedLanguageTag = languageTags[selectedLanguage] ?: Locale.getDefault().toLanguageTag()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to text")
            }
            speechResultLauncher.launch(intent)
        }

    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > 8 && binding.userInput.text.toString().isNotBlank()) {
                Log.d(TAG, "Shake event detected")
                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                // Launch activity that can handle implicit intent
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                }

                // At the same time, say hello in selected language
//                speakHelloInSelectedLanguage()
                val languageTag = languageTags[selectedLanguage] ?: return
                val locale = Locale.forLanguageTag(languageTag)
                val result = textToSpeech.setLanguage(locale)
                val selectedHello = languageToHelloMap[selectedLanguage]
                Log.d(TAG, "$selectedHello")
                textToSpeech.speak(selectedHello, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    }

    private fun translateText(spokenText: String, languageCode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val apiKey = "API_KEY"
            val url = "https://translation.googleapis.com/language/translate/v2?key=$apiKey"
            val jsonMimeType = "application/json; charset=utf-8"
            val postBody = """
            {
              "q": "$spokenText",
              "target": "$languageCode"
            }
            """.trimIndent()

//            val request = Request.Builder()
//                .url(url)
//                .addHeader("Content-Type", jsonMimeType)
//                .post(postBody.toRequestBody())
//                .build()
//
//            try {
//                val client
//                val response = client.newCall(request).execute()
//                val responseBody = response.body?.string()
//
//                withContext(Dispatchers.Main) {
//                    val translatedText =
//                        parseTranslation(responseBody) // Implement this parsing function
////                    editText.setText(translatedText)
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                // Handle the error appropriately
//            }
        }
    }

    private fun parseTranslation(responseBody: String?): String {
        return try {
            val jsonResponse = JSONObject(responseBody)
            val data = jsonResponse.getJSONObject("data")
            val translations = data.getJSONArray("translations")
            val firstTranslation = translations.getJSONObject(0)
            firstTranslation.getString("translatedText")
        } catch (e: JSONException) {
            e.printStackTrace()
            "Error parsing translation"
        }
    }
    

    private fun playGreeting(language: String) {
        // Assuming you have your audio files named as "hello_<language>.mp3" in raw folder
        val resourceName = "hello_${language.toLowerCase(Locale.ROOT)}"
        val resourceId = resources.getIdentifier(resourceName, "raw", packageName)
        if (resourceId != 0) {
            val mediaPlayer = MediaPlayer.create(this, resourceId)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } else {
            Toast.makeText(this, "Audio greeting not found for $language", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        sensorManager?.registerListener(sensorListener, sensorManager!!.getDefaultSensor(
            Sensor .TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL
        )
        super.onResume()
    }

    override fun onPause() {
        sensorManager!!.unregisterListener(sensorListener)
        super.onPause()
    }
}
