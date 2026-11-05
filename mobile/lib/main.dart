import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';

import 'core/system_layers.dart';
import 'services/telemetry_service.dart';
import 'pages/dashboard_page.dart';
import 'pages/hardware_health_page.dart';
import 'pages/smart_router_page.dart';
import 'pages/storage_healer_page.dart';
import 'pages/converter_page.dart';
import 'pages/settings_page.dart';
import 'widgets/common_widgets.dart';

final ValueNotifier<ThemeMode> pulseThemeMode = ValueNotifier<ThemeMode>(ThemeMode.dark);

void main() => runApp(const PulseOSLaunch());

class PulseOSLaunch extends StatefulWidget {
  const PulseOSLaunch({super.key});

  @override
  State<PulseOSLaunch> createState() => _PulseOSLaunchState();
}

class _PulseOSLaunchState extends State<PulseOSLaunch> {
  late final VideoPlayerController video;
  bool finished = false;

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    video = VideoPlayerController.asset('assets/PulseOS_animation.mp4')
      ..initialize().then((_) {
        if (!mounted) return;
        video
          ..setLooping(false)
          ..play()
          ..addListener(_videoChanged);
        setState(() {});
      });
  }

  @override
  void dispose() {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    video
      ..removeListener(_videoChanged)
      ..dispose();
    super.dispose();
  }

  void _videoChanged() {
    if (!mounted || !video.value.isInitialized || finished) return;
    if (video.value.position >= video.value.duration) {
      setState(() => finished = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (finished) return const PulseOSApp();
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PulseOS',
      theme: ThemeData(brightness: Brightness.light, fontFamily: 'Arial'),
      home: Scaffold(
        backgroundColor: Colors.black,
        body: video.value.isInitialized
            ? SizedBox.expand(
                child: FittedBox(
                  fit: BoxFit.cover,
                  clipBehavior: Clip.hardEdge,
                  child: SizedBox(
                    width: video.value.size.width,
                    height: video.value.size.height,
                    child: VideoPlayer(video),
                  ),
                ),
              )
            : const SizedBox.expand(),
      ),
    );
  }
}

class PulseOSApp extends StatelessWidget {
  const PulseOSApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: pulseThemeMode,
      builder: (context, currentMode, _) {
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'PulseOS',
          themeMode: currentMode,
          theme: ThemeData(
            brightness: Brightness.light,
            scaffoldBackgroundColor: const Color(0xFFF1F5F9),
            colorScheme: const ColorScheme.light(
              primary: Color(0xFF1D7C78),
              surface: Color(0xFFFFFFFF),
              surfaceContainerHighest: Color(0xFFE2E8F0),
            ),
            useMaterial3: true,
            fontFamily: 'Arial',
            appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFFFFFFFF),
              foregroundColor: Color(0xFF0F172A),
              elevation: 0,
              surfaceTintColor: Colors.transparent,
            ),
            navigationBarTheme: const NavigationBarThemeData(
              backgroundColor: Color(0xFFFFFFFF),
              indicatorColor: Color(0xFFE2E8F0),
            ),
          ),
          darkTheme: ThemeData(
            brightness: Brightness.dark,
            scaffoldBackgroundColor: const Color(0xFF090D11),
            colorScheme: const ColorScheme.dark(
              primary: Color(0xFFDDE4E9),
              surface: Color(0xFF10161C),
              surfaceContainerHighest: Color(0xFF1A2229),
            ),
            useMaterial3: true,
            fontFamily: 'Arial',
            appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFF0B0F13),
              foregroundColor: Color(0xFFF1F5F9),
              elevation: 0,
              surfaceTintColor: Colors.transparent,
            ),
            navigationBarTheme: const NavigationBarThemeData(
              backgroundColor: Color(0xFF0B0F13),
              indicatorColor: Color(0xFF1A2229),
            ),
          ),
          home: const PulseShell(),
        );
      },
    );
  }
}

class PulseShell extends StatefulWidget {
  const PulseShell({super.key});

  @override
  State<PulseShell> createState() => _PulseShellState();
}

class _PulseShellState extends State<PulseShell> {
  int index = 0;
  final telemetry = TelemetryController();
  Timer? timer;
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadTheme();
    _startNativeMonitoring();
    unawaited(_refreshTelemetry());
    timer = Timer.periodic(const Duration(seconds: 5), (_) {
      unawaited(_refreshTelemetry());
    });
  }

  Future<void> _loadTheme() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final mode = prefs.getString('pulseos_theme_mode') ?? 'dark';
      if (mode == 'light') {
        pulseThemeMode.value = ThemeMode.light;
      } else if (mode == 'system') {
        pulseThemeMode.value = ThemeMode.system;
      } else {
        pulseThemeMode.value = ThemeMode.dark;
      }
    } catch (_) {}
  }

  Future<void> _refreshTelemetry() async {
    await telemetry.refresh();
    if (mounted) setState(() {});
  }

  Future<void> _startNativeMonitoring() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final alertsEnabled = prefs.getBool('pulseos_alerts') ?? true;
      const platform = MethodChannel('pulseos/device');
      if (alertsEnabled) {
        await platform.invokeMethod('startMonitoring');
      } else {
        await platform.invokeMethod('stopMonitoring');
      }
    } catch (_) {
      // Native background monitoring is optional unless the Android bridge is available.
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    _searchController.dispose();
    telemetry.dispose();
    super.dispose();
  }

  void go(int next) => setState(() => index = next);

  void _handleSmartSearch(String input) {
    final query = input.trim();
    if (query.isEmpty) return;
    final q = query.toLowerCase();

    if (q.contains('wifi') || q.contains('network') || q.contains('internet') || q.contains('check internet')) {
      setState(() => index = 2);
      return;
    }
    if (q.contains('battery') || q.contains('charging') || q.contains('battery saver')) {
      setState(() => index = 1);
      return;
    }
    if (q.contains('slow') || q.contains('performance') || q.contains('cpu') || q.contains('ram')) {
      setState(() => index = 1);
      return;
    }
    if (q.contains('storage') || q.contains('space') || q.contains('large') || q.contains('free storage')) {
      setState(() => index = 3);
      return;
    }
    if (q.contains('brightness') || q.contains('bluetooth') || q.contains('sound') || q.contains('permissions')) {
      setState(() => index = 5);
      return;
    }
    if (q.contains('convert') || q.contains('pdf') || q.contains('image')) {
      setState(() => index = 4);
      return;
    }

    setState(() => index = 0);
    _openQuickSearch();
  }

  void _openQuickSearch() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: isDark ? const Color(0xFF10161C) : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Quick search', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            const Text('Jump to a PulseOS section or device action.', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
            const SizedBox(height: 16),
            _SearchActionTile(
              label: 'Overview',
              detail: 'Health score and active alerts',
              icon: Icons.dashboard_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 0);
              },
            ),
            _SearchActionTile(
              label: 'Hardware',
              detail: 'CPU, memory, battery and sensors',
              icon: Icons.memory_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 1);
              },
            ),
            _SearchActionTile(
              label: 'Router',
              detail: 'Connectivity and network diagnostics',
              icon: Icons.alt_route_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 2);
              },
            ),
            _SearchActionTile(
              label: 'Storage',
              detail: 'App-owned cleanups and storage review',
              icon: Icons.cleaning_services_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 3);
              },
            ),
            _SearchActionTile(
              label: 'Converter',
              detail: 'Image, archive and document tools',
              icon: Icons.swap_horiz_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 4);
              },
            ),
            _SearchActionTile(
              label: 'Settings',
              detail: 'Android system shortcuts and preferences',
              icon: Icons.settings_outlined,
              onTap: () {
                Navigator.pop(context);
                setState(() => index = 5);
              },
            ),
          ],
        ),
      ),
    );
  }

  void _openNotifications() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? const Color(0xFF10161C) : Colors.white,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (_) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Notifications', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            if (telemetry.snapshot.healthAlerts.isEmpty)
              const Text('No active observations right now.', style: TextStyle(fontSize: 12, color: Color(0xFF9BA7AF)))
            else
              ...telemetry.snapshot.healthAlerts.map(
                (alert) => Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  margin: const EdgeInsets.only(bottom: 8),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF161E26) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: isDark ? const Color(0xFF273641) : const Color(0xFFCBD5E1)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.notifications_active_rounded, size: 18, color: Color(0xFF8EC5FF)),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          alert,
                          style: const TextStyle(fontSize: 12, height: 1.5),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.tonal(
                onPressed: () => Navigator.pop(context),
                child: const Text('CLOSE'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final pages = [
      DashboardPage(data: telemetry.snapshot, onNavigate: go),
      HardwareHealthPage(telemetry: telemetry),
      SmartRouterPage(data: telemetry.snapshot),
      const StorageHealerPage(),
      const ConverterPage(),
      const SettingsPage(),
    ];
    final layerSummary = PulseLayerRegistry.summary();
    return Scaffold(
      appBar: AppBar(
        backgroundColor: isDark ? const Color(0xFF0B0F13) : Colors.white,
        surfaceTintColor: Colors.transparent,
        titleSpacing: 16,
        title: const Row(
          children: [
            PulseLogo(),
            SizedBox(width: 10),
            Flexible(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('PulseOS',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
                  Text('DEVICE HEALTH & HEALING',
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 8,
                          letterSpacing: 1.15,
                          color: Color(0xFF9BA7AF))),
                ],
              ),
            ),
          ],
        ),
        actions: [
          PulseLivePill(available: telemetry.snapshot.hasLiveData),
          IconButton(
            tooltip: 'Quick search',
            onPressed: _openQuickSearch,
            icon: const Icon(Icons.search_rounded, size: 20),
          ),
          Badge(
            backgroundColor: const Color(0xFF8EC5FF),
            textColor: Colors.black,
            label: Text('${telemetry.snapshot.healthAlerts.length}'),
            isLabelVisible: telemetry.snapshot.healthAlerts.isNotEmpty,
            child: IconButton(
              tooltip: 'Notifications',
              onPressed: _openNotifications,
              icon: const Icon(Icons.notifications_rounded, size: 20),
            ),
          ),
          IconButton(
            tooltip: 'Refresh telemetry',
            onPressed: telemetry.refreshing ? null : _refreshTelemetry,
            icon: const Icon(Icons.refresh_rounded, size: 20),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
              child: TextField(
                controller: _searchController,
                onSubmitted: _handleSmartSearch,
                decoration: InputDecoration(
                  hintText: 'What do you want to do?',
                  prefixIcon: const Icon(Icons.search_rounded, size: 18),
                  filled: true,
                  fillColor: isDark ? const Color(0xFF10161C) : Colors.white,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: isDark ? const Color(0xFF24313C) : const Color(0xFFCBD5E1)),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: isDark ? const Color(0xFF24313C) : const Color(0xFFCBD5E1)),
                  ),
                  focusedBorder: const OutlineInputBorder(
                    borderRadius: BorderRadius.all(Radius.circular(12)),
                    borderSide: BorderSide(color: Color(0xFF8EC5FF)),
                  ),
                ),
              ),
            ),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
              decoration: BoxDecoration(
                border: Border(bottom: BorderSide(color: isDark ? const Color(0xFF1B242C) : const Color(0xFFE2E8F0))),
                color: isDark ? const Color(0xFF0C1116) : const Color(0xFFF8FAFC),
              ),
              child: Wrap(
                spacing: 8,
                runSpacing: 6,
                children: [
                  _LayerStatusChip(
                    label: 'UI',
                    value: '${layerSummary[PulseLayer.ui] ?? 0}',
                    color: const Color(0xFF8EC5FF),
                  ),
                  _LayerStatusChip(
                    label: 'NATIVE',
                    value: '${layerSummary[PulseLayer.native] ?? 0}',
                    color: const Color(0xFF7FE0B2),
                  ),
                  _LayerStatusChip(
                    label: 'INTELLIGENCE',
                    value: '${layerSummary[PulseLayer.intelligence] ?? 0}',
                    color: const Color(0xFFF7D58C),
                  ),
                ],
              ),
            ),
            Expanded(child: pages[index]),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        backgroundColor: isDark ? const Color(0xFF0B0F13) : Colors.white,
        indicatorColor: isDark ? const Color(0xFF1A2229) : const Color(0xFFE2E8F0),
        selectedIndex: index,
        onDestinationSelected: go,
        destinations: const [
          NavigationDestination(
              icon: Icon(Icons.dashboard_outlined),
              selectedIcon: Icon(Icons.dashboard),
              label: 'Overview'),
          NavigationDestination(
              icon: Icon(Icons.memory_outlined),
              selectedIcon: Icon(Icons.memory),
              label: 'Hardware'),
          NavigationDestination(
              icon: Icon(Icons.alt_route_outlined),
              selectedIcon: Icon(Icons.alt_route),
              label: 'Router'),
          NavigationDestination(
              icon: Icon(Icons.cleaning_services_outlined),
              selectedIcon: Icon(Icons.cleaning_services),
              label: 'Storage'),
          NavigationDestination(
              icon: Icon(Icons.swap_horiz_outlined),
              selectedIcon: Icon(Icons.swap_horiz),
              label: 'Convert'),
          NavigationDestination(
              icon: Icon(Icons.settings_outlined),
              selectedIcon: Icon(Icons.settings),
              label: 'Settings'),
        ],
      ),
    );
  }
}

class _LayerStatusChip extends StatelessWidget {
  const _LayerStatusChip({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF161E26) : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: isDark ? const Color(0xFF2A3943) : const Color(0xFFCBD5E1)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 6),
          Text(label, style: TextStyle(fontSize: 9, letterSpacing: 0.9, color: isDark ? const Color(0xFFBFCBD2) : const Color(0xFF475569))),
          const SizedBox(width: 6),
          Text(value, style: TextStyle(fontSize: 9, fontWeight: FontWeight.w800, color: color)),
        ],
      ),
    );
  }
}

class _SearchActionTile extends StatelessWidget {
  const _SearchActionTile({
    required this.label,
    required this.detail,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final String detail;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF171F26) : const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: isDark ? const Color(0xFF273641) : const Color(0xFFE2E8F0)),
        ),
        child: Row(
          children: [
            Icon(icon, size: 18, color: const Color(0xFF9BC7FF)),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text(detail, style: TextStyle(fontSize: 11, color: isDark ? const Color(0xFF9BA7AF) : const Color(0xFF64748B))),
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded, size: 18),
          ],
        ),
      ),
    );
  }
}
