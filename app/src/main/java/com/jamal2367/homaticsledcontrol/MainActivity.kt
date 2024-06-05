package com.jamal2367.homaticsledcontrol

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigationrail.NavigationRailView
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import com.tananaev.adblib.AdbStream
import java.io.File
import java.lang.ref.WeakReference
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private var navigationRail: NavigationRailView? = null
    private var connection: AdbConnection? = null
    private var stream: AdbStream? = null
    private var myAsyncTask: MyAsyncTask? = null
    private val ipAddress = "0.0.0.0"
    private val publicKeyName: String = "public.key"
    private val privateKeyName: String = "private.key"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        if (!isUsbDebuggingEnabled()) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.enable_usb_debugging_first), Toast.LENGTH_LONG).show()
            }

            openDeveloperSettings()
            finish()
            return
        }

        findViewById<TextView>(R.id.homeDescription).apply {
            visibility = View.VISIBLE
        }

        navigationRail = findViewById<View>(R.id.navigation_rail) as NavigationRailView

        navigationRail!!.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.home -> {
                    findViewById<TextView>(R.id.activeLedDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.standbyLedDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.rebootDescription).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedBrightness).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedBrightness).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.rebootButton).visibility = View.GONE

                    findViewById<TextView>(R.id.homeDescription).apply {
                        visibility = View.VISIBLE
                    }
                }
                R.id.activeLed -> {
                    findViewById<TextView>(R.id.homeDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.standbyLedDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.rebootDescription).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedBrightness).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.rebootButton).visibility = View.GONE

                    findViewById<TextView>(R.id.activeLedDescription).apply {
                        visibility = View.VISIBLE
                    }

                    findViewById<MaterialButton>(R.id.activeLedOnOff).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showActiveLedOnOffDialog()
                        }
                    }

                    findViewById<MaterialButton>(R.id.activeLedColor).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showActiveColorDialog()
                        }
                    }

                    findViewById<MaterialButton>(R.id.activeLedBrightness).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showActiveBrightnessDialog()
                        }
                    }
                }

                R.id.standbyLed -> {
                    findViewById<TextView>(R.id.homeDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.activeLedDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.rebootDescription).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedBrightness).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.rebootButton).visibility = View.GONE

                    findViewById<TextView>(R.id.standbyLedDescription).apply {
                        visibility = View.VISIBLE
                        text = getString(R.string.description_standby_led)
                    }

                    findViewById<MaterialButton>(R.id.standbyLedOnOff).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showStandbyLedOnOffDialog()
                        }
                    }

                    findViewById<MaterialButton>(R.id.standbyLedColor).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showStandbyColorDialog()
                        }
                    }

                    findViewById<MaterialButton>(R.id.standbyLedBrightness).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            showStandbyBrightnessDialog()
                        }
                    }
                }

                R.id.reboot -> {
                    findViewById<TextView>(R.id.homeDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.activeLedDescription).visibility = View.GONE
                    findViewById<TextView>(R.id.standbyLedDescription).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.activeLedBrightness).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedOnOff).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedColor).visibility = View.GONE
                    findViewById<MaterialButton>(R.id.standbyLedBrightness).visibility = View.GONE

                    findViewById<TextView>(R.id.rebootDescription).apply {
                        visibility = View.VISIBLE
                    }

                    findViewById<MaterialButton>(R.id.rebootButton).apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            onKey(50)
                        }
                    }
                }
            }
            true
        }
    }

    private fun onKey(case: Int, brightnessValue: Int = 0) {
        connection = null
        stream = null

        myAsyncTask?.cancel()
        myAsyncTask = MyAsyncTask(this)
        myAsyncTask?.execute(ipAddress, case, brightnessValue)
    }

    fun adbCommander(ip: String?, case: Int, brightnessValue: Int) {
        try {
            val socket = Socket(ip, 5555)
            val crypto = readCryptoConfig(filesDir) ?: writeNewCryptoConfig(filesDir)

            try {
                if (stream == null || connection == null) {
                    connection = AdbConnection.create(socket, crypto)
                    connection?.connect()
                }

                when (case) {
                    10 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light 12")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_on), Toast.LENGTH_LONG).show()
                        }
                    }
                    20 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light 0")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_off), Toast.LENGTH_LONG).show()
                        }
                    }
                    30 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress 0")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_cyan_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    31 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress 1")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_red_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    32 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress 2")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_blue_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    33 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress 3")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_green_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    34 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress 4")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_purple_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    40 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light_progress $brightnessValue")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.brightness_of_led_has_been_set_to, brightnessValue) + "%.", Toast.LENGTH_LONG).show()
                        }
                    }
                    50 -> {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.the_device_will_now_restart), Toast.LENGTH_LONG).show()
                        }
                        Thread.sleep(1500)
                        stream = connection?.open("shell:reboot")
                    }
                    70 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light_screen_off 12")
                        stream = connection?.open("shell:settings put global key_user_sel_led_suspend_light 1")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_on), Toast.LENGTH_LONG).show()
                        }
                    }
                    80 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light_screen_off 0")
                        stream = connection?.open("shell:settings put global key_user_sel_led_suspend_light 0")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_off), Toast.LENGTH_LONG).show()
                        }
                    }
                    90 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress_screen_off 0")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_cyan_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    91 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress_screen_off 1")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_red_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    92 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress_screen_off 2")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_blue_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    93 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress_screen_off 3")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_green_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    94 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_color_progress_screen_off 4")

                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.led_is_now_set_to_purple_led), Toast.LENGTH_LONG).show()
                        }
                    }
                    100 -> {
                        stream = connection?.open("shell:settings put global key_user_sel_led_light_progress_screen_off $brightnessValue")

                        runOnUiThread {
                            runOnUiThread {
                                Toast.makeText(this, getString(R.string.brightness_of_led_has_been_set_to, brightnessValue) + "%.", Toast.LENGTH_LONG).show()
                            }

                        }
                    }
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainActivity", "Error executing ADB command for case $case", e)
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MainActivity", "Error establishing socket connection", e)
        }
    }

    private fun openDeveloperSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        startActivity(intent)
    }

    private fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }

    private fun showActiveLedOnOffDialog() {
        val onOff = arrayOf(getString(R.string.on), getString(R.string.off))
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_on_off))
            .setItems(onOff) { dialog: DialogInterface, which: Int ->
                val selectedColor = when (which) {
                    0 -> 10
                    1 -> 20
                    else -> 10 // Default to On
                }
                onKey(selectedColor)
                dialog.dismiss()
            }
        val dialog = builder.create()
        dialog.show()
    }

    private fun showStandbyLedOnOffDialog() {
        val onOff = arrayOf(getString(R.string.on), getString(R.string.off))
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_on_off))
            .setItems(onOff) { dialog: DialogInterface, which: Int ->
                val selectedColor = when (which) {
                    0 -> 70
                    1 -> 80
                    else -> 70 // Default to On
                }
                onKey(selectedColor)
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showActiveBrightnessDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_brightness))

        val brightnessValues = (100 downTo 5).map { "$it%" }.toTypedArray()

        builder.setItems(brightnessValues) { dialog, which ->
            val selectedBrightness = 100 - which
            onKey(40, selectedBrightness)
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun showStandbyBrightnessDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_brightness))

        val brightnessValues = (100 downTo 5).map { "$it%" }.toTypedArray()

        builder.setItems(brightnessValues) { dialog, which ->
            val selectedBrightness = 100 - which
            onKey(100, selectedBrightness)
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun showActiveColorDialog() {
        val colors = arrayOf(getString(R.string.cyan), getString(R.string.red), getString(R.string.blue), getString(R.string.green), getString(R.string.purple))
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_color))
            .setItems(colors) { dialog: DialogInterface, which: Int ->
                val selectedColor = when (which) {
                    0 -> 30
                    1 -> 31
                    2 -> 32
                    3 -> 33
                    4 -> 34
                    else -> 30 // Default to Cyan
                }
                onKey(selectedColor)
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showStandbyColorDialog() {
        val colors = arrayOf(getString(R.string.cyan), getString(R.string.red), getString(R.string.blue), getString(R.string.green), getString(R.string.purple))
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.select_led_color))
            .setItems(colors) { dialog: DialogInterface, which: Int ->
                val selectedColor = when (which) {
                    0 -> 90
                    1 -> 91
                    2 -> 92
                    3 -> 93
                    4 -> 94
                    else -> 90 // Default to Cyan
                }
                onKey(selectedColor)
                dialog.dismiss()
            }
        builder.create().show()
    }

    class MyAsyncTask internal constructor(context: MainActivity) {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)
        private var thread: Thread? = null
        private var ipAddress: String? = null
        private var case: Int = 0
        private var brightnessValue: Int = 0

        fun execute(ipAddress: String, case: Int, brightnessValue: Int) {
            this.ipAddress = ipAddress
            this.case = case
            this.brightnessValue = brightnessValue
            execute()
        }

        private fun execute() {
            thread = Thread {
                try {
                    val activity = activityReference.get()
                    activity?.adbCommander(ipAddress, case, brightnessValue)

                    if (Thread.interrupted()) {
                        return@Thread
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("MyAsyncTask", "Error executing task", e)
                }
            }
            thread?.start()
        }

        fun cancel() {
            thread?.interrupt()
        }
    }

    private fun readCryptoConfig(dataDir: File?): AdbCrypto? {
        val pubKey = File(dataDir, publicKeyName)
        val privKey = File(dataDir, privateKeyName)

        var crypto: AdbCrypto? = null
        if (pubKey.exists() && privKey.exists()) {
            crypto = try {
                AdbCrypto.loadAdbKeyPair(AndroidBase64(), privKey, pubKey)
            } catch (e: Exception) {
                null
            }
        }

        return crypto
    }

    private fun writeNewCryptoConfig(dataDir: File?): AdbCrypto? {
        val pubKey = File(dataDir, publicKeyName)
        val privKey = File(dataDir, privateKeyName)

        var crypto: AdbCrypto?

        try {
            crypto = AdbCrypto.generateAdbKeyPair(AndroidBase64())
            crypto.saveAdbKeyPair(privKey, pubKey)
        } catch (e: Exception) {
            crypto = null
        }

        return crypto
    }

    class AndroidBase64 : AdbBase64 {
        override fun encodeToString(bArr: ByteArray): String {
            return Base64.encodeToString(bArr, Base64.NO_WRAP)
        }
    }
}
