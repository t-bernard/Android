package ui

import ViewHolder.DeviceViewHolder
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.recyclical.datasource.emptyDataSourceTyped
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.example.telecommand_bluetooth.R
import com.example.telecommand_bluetooth.databinding.ActivityScanBinding
import data.BluetoothLEManager
import data.LocalPreferences
import model.Device
import model.LedStatus

private lateinit var binding: ActivityScanBinding

// Gestion du Bluetooth
private var bluetoothAdapter: BluetoothAdapter? = null      // L'Adapter permettant de se connecter
private var currentBluetoothGatt: BluetoothGatt? = null     // La connexion actuellement établie
private var bluetoothLeScanner: BluetoothLeScanner? = null  //// « Interface système nous permettant de scanner »

/**
 * Gestion du SCAN, recherche des device BLE à proximité
 */

// Parametrage du scan BLE
private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

// On ne retourne que les « Devices » proposant le bon UUID
private var scanFilters: List<ScanFilter> = arrayListOf(
        ScanFilter.Builder().setServiceUuid(ParcelUuid(BluetoothLEManager.DEVICE_UUID)).build()
)

// Variable de fonctionnement
private var mScanning = false
private val handler = Handler()

// Adapter
private val bleDevicesFoundList = emptyDataSourceTyped<Device>()

private val ledStatus: LedStatus = LedStatus();


class ScanActivity : AppCompatActivity() {
    companion object {
        const val PERMISSION_REQUEST_LOCATION = 9999
        const val REQUEST_ENABLE_BLE = 9997

        fun getStartIntent(context: Context): Intent {
            return Intent(context, ScanActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --> Indique que l'on utilise le ViewBinding
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvDevices.setup {
            withDataSource(bleDevicesFoundList)
            withItem<Device, DeviceViewHolder>(R.layout.item_list) {
                onBind(::DeviceViewHolder) { _, item ->
                    name.text = item.name.takeIf { !it.isNullOrEmpty() } ?: run { item.mac }
                    subName.text = item.mac
                }
                onClick {
                    Toast.makeText(
                        this@ScanActivity,
                        getString(R.string.trying_connection_to, item.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    BluetoothLEManager.currentDevice = item.device
                    connectToCurrentDevice()
                }
            }
        }

        setUiMode(false)


        /**
         * Actions utilisateur
         */

        binding.buttonDeconnexion.setOnClickListener {
            disconnectFromCurrentDevice()
        }

        binding.buttonToggle.setOnClickListener {
            toggleLed()
        }

        binding.buttonScan.setOnClickListener {
            if (hasPermission()) {
                setupBLE()
            } else {
                askForPermission()
            }
        }
    }


    /**
     * Gère l'action après la demande de permission.
     * 2 cas possibles :
     * - Réussite 🎉.
     * - Échec (refus utilisateur).
     */
    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_LOCATION && grantResults.size == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && locationServiceEnabled()) {
                // Permission OK => Lancer SCAN
                setupBLE()
            } else if (!locationServiceEnabled()) {
                // Inviter à activer la localisation
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                // Permission KO => Message d'erreur
                MaterialDialog(this).show {
                    title(R.string.yourTitle)
                    message(R.string.yourMessage)
                }
            }
        }
    }

    /**
     * Permet de vérifier si l'application possede la permission « Localisation ». OBLIGATOIRE pour scanner en BLE
     */
    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Demande de la permission (ou des permissions) à l'utilisateur.
     */
    private fun askForPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_LOCATION
        )
    }

    private fun locationServiceEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(
                this.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    /**
     * Récupération de l'adapter Bluetooth & vérification si celui-ci est actif
     */
    private fun setupBLE() {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.let { bluetoothManager ->
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter != null && !bluetoothManager.adapter.isEnabled) {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    REQUEST_ENABLE_BLE
                )
            } else {
                scanLeDevice()
            }
        }
    }

    // Le scan va durer 10 secondes seulement, sauf si vous passez une autre valeur comme paramètre.
    private fun scanLeDevice(scanPeriod: Long = 10000) {
        if (!mScanning) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

            // On vide la liste qui contient les devices actuellement trouvés
            bleDevicesFoundList.clear()

            mScanning = true

            // On lance une tache qui durera « scanPeriod » à savoir donc de base
            // 10 secondes
            handler.postDelayed({
                mScanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Toast.makeText(this, getString(R.string.scan_ended), Toast.LENGTH_SHORT).show()
            }, scanPeriod)

            // On lance le scan
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        }
    }

    // Callback appelé à chaque périphérique trouvé.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            // C'est ici que nous allons créer notre « device » et l'ajouter dans le RecyclerView (Datasource)

            val device = Device(result.device.name, result.device.address, result.device)
            if (!bleDevicesFoundList.contains(device)) {
                bleDevicesFoundList.add(device)
            }
        }
    }

    /**
     * On demande la connexion du device
     */
    private fun connectToCurrentDevice() {
        BluetoothLEManager.currentDevice?.let { device ->
            Toast.makeText(this, "Connexion en cours … $device", Toast.LENGTH_SHORT).show()

            currentBluetoothGatt = device.connectGatt(
                this,
                false,
                BluetoothLEManager.GattCallback(
                    onConnect = {
                        // On indique à l'utilisateur que nous sommes correctement connecté
                        runOnUiThread {
                            // Nous sommes connecté au device, on active les notifications pour être notifié si la LED change d'état.

                            enableListenBleNotify()

                            // On change la vue « pour être en mode connecté »
                            setUiMode(true)

                            // On sauvegarde dans les « LocalPréférence » de l'application le nom du dernier périphérique
                            // sur lequel nous nous sommes connecté
                            LocalPreferences.getInstance(this).lastConnectedDeviceName(device.name);

                        }
                    },
                    onNotify = {
                        runOnUiThread {
                            handleToggleLedNotificationUpdate(it)
                        }
                    },
                    onDisconnect = { runOnUiThread { disconnectFromCurrentDevice() } })
            )
        }
    }


    /**
     * On demande la déconnexion du device
     */
    private fun disconnectFromCurrentDevice() {
        currentBluetoothGatt?.disconnect()
        BluetoothLEManager.currentDevice = null
        setUiMode(false)
    }


    /**
     * On change l'affichage de la vue en fonction de l'état connecté/déconnecté
     */
    private fun setUiMode(isConnected: Boolean) {
        if (isConnected) {
            // Connecté à un périphérique
            bleDevicesFoundList.clear()
            binding.rvDevices.visibility = View.GONE
            binding.buttonScan.visibility = View.GONE
            binding.textPeriph.visibility = View.VISIBLE
            binding.textPeriph.text = getString(R.string.connected_to, BluetoothLEManager.currentDevice?.name)
            binding.buttonDeconnexion.visibility = View.VISIBLE
            binding.ledStatus.visibility = View.VISIBLE
            binding.buttonToggle.visibility = View.VISIBLE
        } else {
            // Non connecté, reset de la vue.
            binding.rvDevices.visibility = View.VISIBLE
            binding.buttonScan.visibility = View.VISIBLE
            binding.textPeriph.visibility = View.GONE
            binding.ledStatus.visibility = View.GONE
            binding.buttonDeconnexion.visibility = View.GONE
            binding.buttonToggle.visibility = View.GONE
        }
    }


    /**
     * Récupération de « service » BLE (via UUID) qui nous permettra d'envoyer / recevoir des commandes
     */
    private fun getMainDeviceService(): BluetoothGattService? {
        return currentBluetoothGatt?.let { bleGatt ->
            val service = bleGatt.getService(BluetoothLEManager.DEVICE_UUID)
            service?.let {
                return it
            } ?: run {
                Toast.makeText(this, getString(R.string.uuid_not_found), Toast.LENGTH_SHORT).show()
                return null;
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show()
            return null
        }
    }

    /**
     * On change l'état de la LED (via l'UUID de toggle)
     */
    private fun toggleLed() {
        getMainDeviceService()?.let { service ->
            val toggleLed =
                service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("1")
            currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }


    private fun enableListenBleNotify() {
        getMainDeviceService()?.let { service ->
            Toast.makeText(this, getString(R.string.enable_ble_notifications), Toast.LENGTH_SHORT).show()
            // Indique que le GATT Client va écouter les notifications sur le charactérisque
            val notification = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE)

            currentBluetoothGatt?.setCharacteristicNotification(notification, true)
        }
    }

    private fun handleToggleLedNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.getStringValue(0).equals("on", ignoreCase = true)) {
            binding.ledStatus.setImageResource(R.drawable.lamp_on)
        } else {
            binding.ledStatus.setImageResource(R.drawable.lamp_off)
        }
    }

}