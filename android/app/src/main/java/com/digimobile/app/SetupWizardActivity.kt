package com.digimobile.app

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.digimobile.app.databinding.ActivitySetupWizardBinding
import com.digimobile.node.NodeConfigOptions
import com.digimobile.node.NodeSetupPreset

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupWizardBinding
    private lateinit var configStore: NodeConfigStore

    private val minPeers = 8
    private val maxPeers = 24
    private val minPruneGb = 3
    private val maxPruneGb = 16
    private val minDbCacheMb = 128
    private val maxDbCacheMb = 1024

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configStore = NodeConfigStore(this)

        setupDeviceInfo()
        setupPresetCards()
        setupSeekBars()
        setupActions()
        hydrateFromStoredConfig()
    }

    private fun setupDeviceInfo() {
        val memoryInfo = ActivityManager.MemoryInfo()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memoryInfo)
        val totalRamLabel = Formatter.formatFileSize(this, memoryInfo.totalMem)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val availableBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes
        val freeLabel = Formatter.formatFileSize(this, availableBytes)
        val totalLabel = Formatter.formatFileSize(this, totalBytes)

        binding.textDeviceMemory.text = "Detected RAM: $totalRamLabel"
        binding.textDeviceStorage.text = "Storage: $freeLabel free of $totalLabel"
    }

    private fun setupPresetCards() {
        val presetCards = mapOf(
            binding.cardPresetLight to NodeSetupPreset.LIGHT,
            binding.cardPresetBalanced to NodeSetupPreset.BALANCED,
            binding.cardPresetFullish to NodeSetupPreset.FULLISH,
            binding.cardPresetCustom to NodeSetupPreset.CUSTOM,
        )

        presetCards.forEach { (card, preset) ->
            card.setOnClickListener {
                binding.radioPreset.check(getRadioIdForPreset(preset))
                updateCustomVisibility(preset)
            }
        }

        listOf(
            binding.radioLight to NodeSetupPreset.LIGHT,
            binding.radioBalanced to NodeSetupPreset.BALANCED,
            binding.radioFullish to NodeSetupPreset.FULLISH,
            binding.radioCustom to NodeSetupPreset.CUSTOM,
        ).forEach { (radio, preset) ->
            radio.setOnClickListener {
                binding.radioPreset.check(radio.id)
                updateCustomVisibility(preset)
                if (preset != NodeSetupPreset.CUSTOM) {
                    applyPresetDefaults(preset)
                }
            }
        }

        binding.radioPreset.setOnCheckedChangeListener { _, checkedId ->
            val preset = getPresetForRadioId(checkedId)
            updateCustomVisibility(preset)
            if (preset != NodeSetupPreset.CUSTOM) {
                applyPresetDefaults(preset)
            }
        }
    }

    private fun setupSeekBars() {
        setupSeekBar(binding.seekPeers, minPeers, maxPeers) { value ->
            binding.textPeersValue.text = "$value peers"
        }

        setupSeekBar(binding.seekPrune, minPruneGb, maxPruneGb) { value ->
            binding.textPruneValue.text = "$value GB"
        }

        setupSeekBar(binding.seekDbcache, minDbCacheMb, maxDbCacheMb, step = 64) { value ->
            binding.textDbcacheValue.text = "$value MB"
        }
    }

    private fun setupSeekBar(
        seekBar: SeekBar,
        min: Int,
        max: Int,
        step: Int = 1,
        onUpdate: (Int) -> Unit
    ) {
        seekBar.max = (max - min) / step
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = min + progress * step
                onUpdate(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        seekBar.progress = 0
        onUpdate(min)
    }

    private fun setupActions() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonBegin.setOnClickListener {
            val options = collectOptions()
            configStore.save(options)
            Toast.makeText(this, "Config saved. Starting DigiByte node…", Toast.LENGTH_SHORT).show()
            startNodeFlow()
        }

        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            binding.textWifiWarning.isVisible = isChecked
        }
    }

    private fun hydrateFromStoredConfig() {
        val options = configStore.load()
        binding.radioPreset.check(getRadioIdForPreset(options.preset))
        updateCustomVisibility(options.preset)

        if (options.preset != NodeSetupPreset.CUSTOM) {
            applyPresetDefaults(options.preset)
        } else {
            setSeekValue(binding.seekPeers, options.maxConnections, minPeers)
            setSeekValue(binding.seekPrune, options.pruneTargetMb / 1024, minPruneGb)
            setSeekValue(binding.seekDbcache, options.dbCacheMb, minDbCacheMb, step = 64)
            binding.switchBlocksOnly.isChecked = options.blocksonly
            binding.switchWifiOnly.isChecked = options.wifiOnlyPreference
        }
        binding.checkboxTelemetry.isChecked = options.telemetryConsent
        binding.checkboxUseSnapshot.isChecked = options.useSnapshot
    }

    private fun setSeekValue(seekBar: SeekBar, target: Int, min: Int, step: Int = 1) {
        seekBar.progress = (target - min) / step
    }

    private fun updateCustomVisibility(preset: NodeSetupPreset) {
        val showCustom = preset == NodeSetupPreset.CUSTOM
        binding.layoutCustomControls.isVisible = showCustom
        binding.textCustomNotice.isVisible = showCustom
    }

    private fun applyPresetDefaults(preset: NodeSetupPreset) {
        val defaults = configStore.defaultFor(preset)
        setSeekValue(binding.seekPeers, defaults.maxConnections, minPeers)
        setSeekValue(binding.seekPrune, defaults.pruneTargetMb / 1024, minPruneGb)
        setSeekValue(binding.seekDbcache, defaults.dbCacheMb, minDbCacheMb, step = 64)
        binding.switchBlocksOnly.isChecked = defaults.blocksonly
        binding.switchWifiOnly.isChecked = defaults.wifiOnlyPreference

        binding.textPeersValue.text = "${defaults.maxConnections} peers"
        binding.textPruneValue.text = "${defaults.pruneTargetMb / 1024} GB"
        binding.textDbcacheValue.text = "${defaults.dbCacheMb} MB"
    }

    private fun collectOptions(): NodeConfigOptions {
        val preset = getPresetForRadioId(binding.radioPreset.checkedRadioButtonId)
        val peers = minPeers + binding.seekPeers.progress
        val pruneGb = minPruneGb + binding.seekPrune.progress
        val dbcacheMb = minDbCacheMb + binding.seekDbcache.progress * 64

        return NodeConfigOptions(
            preset = preset,
            maxConnections = peers,
            pruneTargetMb = pruneGb * 1024,
            dbCacheMb = dbcacheMb,
            blocksonly = binding.switchBlocksOnly.isChecked,
            telemetryConsent = binding.checkboxTelemetry.isChecked,
            wifiOnlyPreference = binding.switchWifiOnly.isChecked,
            useSnapshot = binding.checkboxUseSnapshot.isChecked,
        )
    }

    private fun getRadioIdForPreset(preset: NodeSetupPreset): Int {
        return when (preset) {
            NodeSetupPreset.LIGHT -> binding.radioLight.id
            NodeSetupPreset.BALANCED -> binding.radioBalanced.id
            NodeSetupPreset.FULLISH -> binding.radioFullish.id
            NodeSetupPreset.CUSTOM -> binding.radioCustom.id
        }
    }

    private fun getPresetForRadioId(id: Int): NodeSetupPreset {
        return when (id) {
            binding.radioLight.id -> NodeSetupPreset.LIGHT
            binding.radioBalanced.id -> NodeSetupPreset.BALANCED
            binding.radioFullish.id -> NodeSetupPreset.FULLISH
            else -> NodeSetupPreset.CUSTOM
        }
    }

    private fun startNodeFlow() {
        val intent = Intent(this@SetupWizardActivity, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            @Suppress("DEPRECATION")
            startService(intent)
        }
        val setupIntent = Intent(this, NodeSetupActivity::class.java)
        startActivity(setupIntent)
        finish()
    }
}
