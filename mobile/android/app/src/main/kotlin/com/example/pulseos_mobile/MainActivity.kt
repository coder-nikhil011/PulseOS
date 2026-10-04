package com.example.pulseos_mobile

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.StatFs
import android.provider.Settings
import android.app.usage.StorageStatsManager
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.WindowManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.os.BatteryManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.android.FlutterActivity
import java.util.concurrent.TimeUnit

class MainActivity : FlutterActivity() {
	private val channelName = "pulseos/device"
	private var previousCpuTotal = 0L
	private var previousCpuIdle = 0L
	private val previousProcessTicks = mutableMapOf<Int, Long>()
	private var previousProcessTotal = 0L

	override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
		super.configureFlutterEngine(flutterEngine)
		MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
			.setMethodCallHandler { call, result ->
				when (call.method) {
					"snapshot" -> result.success(snapshot())
					"cpuUsage" -> result.success(cpuUsage() ?: 0.0)
					"usageStats" -> result.success(usageStats())
					"processes" -> result.success(processes())
					"appCaches" -> result.success(appCaches())
					"openPhoneSetting" -> {
						result.success(openPhoneSetting(call.argument<String>("setting") ?: ""))
					}
					"openStorageSettings" -> {
						startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
						result.success(true)
					}
					"openAppStorage" -> {
						val packageName = call.argument<String>("package")
						startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
							data = android.net.Uri.parse("package:$packageName")
						})
						result.success(true)
					}
					"openAllAppStorage" -> {
						startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
						result.success(true)
					}
					else -> result.notImplemented()
				}
			}
	}

	private fun openPhoneSetting(setting: String): Boolean {
		val intent = when (setting) {
			"network" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
			"data_usage" -> Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
			"bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
			"usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
			"permissions" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
				data = android.net.Uri.parse("package:$packageName")
			}
			"notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
				putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
			}
			"apps" -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
			"battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
			"display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
			"sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
			"storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
			"location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
			"security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			"nfc" -> Intent(Settings.ACTION_NFC_SETTINGS)
			"date_time" -> Intent(Settings.ACTION_DATE_SETTINGS)
			"language" -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
			"accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
			else -> return false
		}
		return runCatching {
			startActivity(if (intent.resolveActivity(packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS))
			true
		}.getOrDefault(false)
	}

	private fun snapshot(): Map<String, Any?> {
		val memory = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
		val memoryInfo = ActivityManager.MemoryInfo()
		memory.getMemoryInfo(memoryInfo)
		val storage = StatFs(Environment.getDataDirectory().path)
		val blockSize = storage.blockSizeLong
		val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
		val processInfo = memory.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()
		return mapOf(
			"model" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
			"manufacturer" to Build.MANUFACTURER,
			"brand" to Build.BRAND,
			"androidVersion" to Build.VERSION.RELEASE,
			"securityPatch" to Build.VERSION.SECURITY_PATCH,
			"buildNumber" to Build.DISPLAY,
			"architecture" to Build.SUPPORTED_ABIS.firstOrNull(),
			"androidApi" to Build.VERSION.SDK_INT,
			"totalMemory" to memoryInfo.totalMem,
			"availableMemory" to memoryInfo.availMem,
			"totalStorage" to storage.blockCountLong * blockSize,
			"freeStorage" to storage.availableBlocksLong * blockSize,
			"cpuCores" to Runtime.getRuntime().availableProcessors(),
			"cpuUsage" to cpuUsage(),
			"processMemory" to (processInfo?.totalPss ?: 0) * 1024L,
			"processes" to processes(),
			"batteryStatus" to batteryStatus(battery),
			"batteryTemperature" to ((battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0),
			"batteryVoltage" to (battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1),
			"batteryHealth" to (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
			"temperatures" to emptyList<Map<String, Any>>(),
			"hardware" to hardwareSnapshot(),
		)
	}

	private fun hardwareSnapshot(): Map<String, Any?> {
		val packageFeatures = packageManager
		val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
		val displayMetrics = DisplayMetrics()
		val displaySize = Point()
		display.getRealMetrics(displayMetrics)
		display.getRealSize(displaySize)
		val sensors = (getSystemService(Context.SENSOR_SERVICE) as SensorManager)
			.getSensorList(Sensor.TYPE_ALL)
		val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
		val audioInputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).size }.getOrNull()
		} else null
		val audioOutputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).size }.getOrNull()
		} else null
		val cameraCount = runCatching {
			(getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager)
				.cameraIdList.size
		}.getOrNull()
		val networkManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		val network = networkManager.activeNetwork
		val networkCapabilities = network?.let(networkManager::getNetworkCapabilities)
		val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
		val nfcAdapter = (getSystemService(Context.NFC_SERVICE) as? android.nfc.NfcManager)?.defaultAdapter
		val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

		return mapOf(
			"display" to mapOf(
				"widthPixels" to displaySize.x,
				"heightPixels" to displaySize.y,
				"densityDpi" to displayMetrics.densityDpi,
				"refreshRate" to display.refreshRate,
				"brightness" to runCatching {
					Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
				}.getOrNull(),
				"touchscreen" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
				"multiTouch" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH),
			),
			"camera" to mapOf(
				"count" to cameraCount,
				"flash" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
				"front" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
			),
			"audio" to mapOf(
				"microphone" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
				"inputCount" to audioInputs,
				"outputCount" to audioOutputs,
			),
			"sensors" to sensors.map { sensor ->
				mapOf("name" to sensor.name, "type" to sensor.stringType, "vendor" to sensor.vendor)
			},
			"connectivity" to mapOf(
				"connected" to (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true),
				"validated" to (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true),
				"transport" to when {
					networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
					networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile network"
					networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet / adapter"
					else -> "Unavailable"
				},
			),
			"features" to mapOf(
				"gps" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
				"gpsEnabled" to runCatching { locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) }.getOrNull(),
				"nfc" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_NFC),
				"nfcEnabled" to runCatching { nfcAdapter?.isEnabled }.getOrNull(),
				"bluetooth" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
				"fingerprint" to packageFeatures.hasSystemFeature("android.hardware.fingerprint"),
				"faceBiometric" to packageFeatures.hasSystemFeature("android.hardware.biometrics.face"),
				"usbHost" to packageFeatures.hasSystemFeature(PackageManager.FEATURE_USB_HOST),
				"vibrator" to vibrator.hasVibrator(),
			),
		)
	}

	private fun cpuUsage(): Double? {
		var sample = readCpuSample() ?: return null
		if (previousCpuTotal == 0L) {
			Thread.sleep(100)
			sample = readCpuSample() ?: return null
		}
		val totalDelta = sample.first - previousCpuTotal
		val idleDelta = sample.second - previousCpuIdle
		previousCpuTotal = sample.first
		previousCpuIdle = sample.second
		if (totalDelta <= 0) return 0.0
		return ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0)
			.coerceIn(0.0, 100.0)
	}

	private fun readCpuSample(): Pair<Long, Long>? {
		val fields = runCatching {
			java.io.File("/proc/stat").bufferedReader().use { it.readLine() }
		}.getOrNull()?.trim()?.split(Regex("\\s+")) ?: return null
		if (fields.firstOrNull() != "cpu" || fields.size < 5) return null
		val values = fields.drop(1).mapNotNull { it.toLongOrNull() }
		if (values.size < 4) return null
		val total = values.sum()
		val idle = values[3] + (values.getOrNull(4) ?: 0L)
		return total to idle
	}

	private fun batteryStatus(intent: Intent?): String {
		return when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
			BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
			BatteryManager.BATTERY_STATUS_FULL -> "Full"
			BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
			BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
			else -> "Unknown"
		}
	}

	private fun processes(): List<Map<String, Any?>> {
		val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
		val running = manager.runningAppProcesses ?: return emptyList()
		val memory = manager.getProcessMemoryInfo(running.map { it.pid }.toIntArray())
		val totalCpu = readCpuSample()?.first ?: 0L
		val totalDelta = if (previousProcessTotal == 0L) 0L else totalCpu - previousProcessTotal
		previousProcessTotal = totalCpu
		return running.mapIndexed { index, process ->
			val ticks = readProcessTicks(process.pid)
			val previous = previousProcessTicks.put(process.pid, ticks)
			val cpu = if (previous == null || totalDelta <= 0) null
			else ((ticks - previous).toDouble() / totalDelta * 100.0).coerceIn(0.0, 100.0)
			mapOf(
				"name" to process.processName,
				"memory" to ((memory.getOrNull(index)?.totalPss ?: 0) * 1024L),
				"cpu" to cpu,
				"importance" to process.importance,
			)
		}.sortedByDescending { it["memory"] as Long }
	}

	private fun readProcessTicks(pid: Int): Long {
		val text = runCatching { java.io.File("/proc/$pid/stat").readText() }.getOrNull()
			?: return 0L
		val fields = text.substringAfterLast(") ").split(Regex("\\s+"))
		return (fields.getOrNull(11)?.toLongOrNull() ?: 0L) +
			(fields.getOrNull(12)?.toLongOrNull() ?: 0L)
	}

	private fun appCaches(): List<Map<String, Any>> {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
		val storage = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
		return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
			.mapNotNull { application ->
				val stats = runCatching {
					storage.queryStatsForPackage(
						StorageManager.UUID_DEFAULT,
						application.packageName,
						android.os.Process.myUserHandle(),
					)
				}.getOrNull() ?: return@mapNotNull null
				mapOf(
					"package" to application.packageName,
					"label" to packageManager.getApplicationLabel(application).toString(),
					"cacheBytes" to stats.cacheBytes,
				)
			}
			.sortedByDescending { it["cacheBytes"] as Long }
			.take(50)
	}

	private fun usageStats(): Map<String, Any> {
		val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
		val mode = appOps.checkOpNoThrow(
			AppOpsManager.OPSTR_GET_USAGE_STATS,
			android.os.Process.myUid(),
			packageName,
		)
		if (mode != AppOpsManager.MODE_ALLOWED) {
			return mapOf("allowed" to false, "leaders" to emptyList<Map<String, Any>>())
		}

		val now = System.currentTimeMillis()
		val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
		val stats = manager.queryUsageStats(
			UsageStatsManager.INTERVAL_WEEKLY,
			now - TimeUnit.DAYS.toMillis(7),
			now,
		)
		val eventPackages = mutableSetOf<String>()
		val events = manager.queryEvents(now - TimeUnit.DAYS.toMillis(7), now)
		val event = UsageEvents.Event()
		while (events.hasNextEvent()) {
			events.getNextEvent(event)
			if (event.packageName != packageName && event.eventType != UsageEvents.Event.NONE) {
				eventPackages.add(event.packageName)
			}
		}
		val statPackages = stats.associateBy { it.packageName }
		val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
		val launchablePackages = packageManager.queryIntentActivities(
			launcherIntent,
			PackageManager.MATCH_ALL,
		).map { it.activityInfo.packageName }.toSet()
		val allPackages = launchablePackages + eventPackages + statPackages.keys
		val currentProcesses = processes()
		val leaders = allPackages
			.mapNotNull { appPackage ->
				val stat = statPackages[appPackage]
				val applicationInfo = runCatching {
					packageManager.getApplicationInfo(appPackage, PackageManager.GET_META_DATA)
				}.getOrNull() ?: return@mapNotNull null
				val process = currentProcesses.firstOrNull {
					"${it["name"]}" == appPackage ||
					"${it["name"]}".startsWith("$appPackage:")
				}
				val row = mutableMapOf<String, Any>(
					"label" to packageManager.getApplicationLabel(applicationInfo).toString(),
					"package" to appPackage,
					"minutes" to TimeUnit.MILLISECONDS.toMinutes(stat?.totalTimeInForeground ?: 0L),
					"lastUsed" to (stat?.lastTimeUsed ?: now),
				)
				if (process != null) {
					row["memory"] = process["memory"] ?: 0L
					row["cpu"] = process["cpu"] ?: 0.0
				}
				row
			}
			.sortedByDescending { it["minutes"] as Long }
			.take(100)
		return mapOf("allowed" to true, "leaders" to leaders)
	}
}
