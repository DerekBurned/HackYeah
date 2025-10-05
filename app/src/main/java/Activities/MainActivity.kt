package Activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.travelnow.databinding.ActivityMainBinding
import com.example.travelnow.helpers.PlacesSearchHelper

class MainActivity : AppCompatActivity() {

    private val CITY_NAME_KEY: String = "city"

    private lateinit var placesHelper: PlacesSearchHelper
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnCheckSafety.isEnabled =false
        placesHelper = PlacesSearchHelper(this)

        setupListener()
        Log.d("MainActivity", "onCreate completed")
    }

    private fun setupListener() {
        with(binding) {
            btnCheckSafety.setOnClickListener {
                val cityName = etCityName.text.toString().trim()

                Log.d("MainActivity", "========== Button Clicked ==========")
                Log.d("MainActivity", "City name entered: '$cityName'")
                Log.d("MainActivity", "City name length: ${cityName.length}")
                Log.d("MainActivity", "City name isEmpty: ${cityName.isEmpty()}")

                if (cityName.isEmpty()) {
                    etCityName.error = "You need to enter the city name first"
                    Log.d("MainActivity", "✗ City name is empty")
                } else {
                    Log.d("MainActivity", "✓ Creating intent with city: '$cityName'")

                    // Create a NEW intent
                    val intent = Intent(this@MainActivity, MapsActivity::class.java)
                    intent.putExtra(CITY_NAME_KEY, cityName)

                    Log.d("MainActivity", "Intent created. Extras: ${intent.extras}")
                    Log.d("MainActivity", "Starting MapsActivity...")

                    startActivity(intent)
                    finish()
                }
            }
        }

        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line
        )

        with(binding.etCityName) {
            setAdapter(adapter)
            threshold = 1

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    handleSearchTextChanged(s, adapter)
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            setOnItemClickListener { _, _, position, _ ->
                handlePlaceSelected(position)
                clearFocus()
            }
        }
    }

    private fun handleSearchTextChanged(text: CharSequence?, adapter: ArrayAdapter<String>) {
        if (!text.isNullOrEmpty()) {
            searchPlaces(text.toString(), adapter)
            binding.btnCheckSafety.isEnabled = true
        } else {
            binding.btnCheckSafety.isEnabled = false
        }
    }

    private fun searchPlaces(query: String, adapter: ArrayAdapter<String>) {
        placesHelper.searchPlaces(
            query,
            onSuccess = { suggestions, _ ->
                adapter.clear()
                adapter.addAll(suggestions)
                adapter.notifyDataSetChanged()
            },
            onFailure = { exception ->
                Log.e("MainActivity", "Search failed: ${exception.message}")
            }
        )
    }

    private fun handlePlaceSelected(position: Int) {
        val prediction = placesHelper.getPredictionAt(position) ?: return
        val locationName = prediction.getFullText(null).toString()
        binding.etCityName.setText(locationName)
        Log.d("MainActivity", "Place selected: '$locationName'")
    }
}