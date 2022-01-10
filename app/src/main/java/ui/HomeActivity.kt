package ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.example.telecommand_bluetooth.R
import com.example.telecommand_bluetooth.databinding.ActivityHomeBinding
import data.LocalPreferences
import ui.ScanActivity.Companion.getStartIntent

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --> Indique que l'on utilise le ViewBinding
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonPeriph.setOnClickListener {
            startActivity(ScanActivity.getStartIntent(this))
        }

        binding.buttonInternet.setOnClickListener {
            val piIdentifier = LocalPreferences.getInstance(this).lastConnectedDeviceName();
            if (isAirplaneModeOn(this)){
                Toast.makeText(this, getString(R.string.airplaneModeOn), Toast.LENGTH_SHORT).show()
            }
            else{
                if (piIdentifier != null){
                    startActivity(ToggleActivity.getStartIntent(this,piIdentifier))
                }
                else{
                    Toast.makeText(this, getString(R.string.piIdentifier), Toast.LENGTH_SHORT).show()
                }
            }

        }

    }

    private fun  isAirplaneModeOn(context : Context) : Boolean{
        return Settings.System.getInt(context.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0 )!=0;
    }


}