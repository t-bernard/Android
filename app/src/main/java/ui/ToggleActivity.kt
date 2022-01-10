package ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.example.telecommand_bluetooth.R
import com.example.telecommand_bluetooth.databinding.ActivityHomeBinding
import com.example.telecommand_bluetooth.databinding.ActivityToggleBinding
import data.ApiService
import data.BluetoothLEManager
import data.LocalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.LedStatus

class ToggleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityToggleBinding

    private val ledStatus: LedStatus = LedStatus();

    companion object{
        private const val PI_IDENTIFIER = "PI_IDENTIFIER"

        fun getStartIntent(context : Context, piIdentifier : String): Intent {
            return Intent(context, ToggleActivity::class.java).apply {
                putExtra(PI_IDENTIFIER,piIdentifier)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToggleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ledStatus.setIdentifier(getIdentifier());

        binding.logoRefresh.setOnClickListener {
            getStatus();
        }

        binding.buttonState.setOnClickListener {
            ledStatus.reverseStatus();
            toggleLed();
            setVisualState();
        }
    }

    // Récupération de l'état depuis le serveur
    private fun getStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val readStatus = ApiService.instance.readStatus(ledStatus.identifier)
                ledStatus.setStatus(readStatus.status)
                setVisualState()
            }
        }
    }

    // Récupérer l'identifiant de la Pi
    private fun getIdentifier() : String{
        return intent.getStringExtra("PI_IDENTIFIER")!!
    }

    // Changer l'état de la led
    private fun toggleLed(){
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ApiService.instance.writeStatus(ledStatus);
            }
        }
    }

    // Mise à jour de l'affichage
    private fun setVisualState(){
        if (ledStatus.status) {
            binding.ledStatus.setImageResource(R.drawable.lamp_on)
        } else {
            binding.ledStatus.setImageResource(R.drawable.lamp_off)
        }
    }
}