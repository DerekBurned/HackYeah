package Activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.travelnow.R
import com.example.travelnow.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "About to start MapsActivity")
        val intent = Intent(this, MapsActivity::class.java)
        startActivity(intent)
        Log.d("MainActivity", "startActivity called")
        finish()
    }
}