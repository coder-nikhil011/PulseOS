import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';

import 'services/telemetry_service.dart';
import 'pages/dashboard_page.dart';
import 'pages/hardware_health_page.dart';
import 'pages/smart_router_page.dart';
import 'pages/storage_healer_page.dart';
import 'pages/converter_page.dart';
import 'pages/settings_page.dart';
import 'widgets/common_widgets.dart';

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
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PulseOS',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF090D11),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFDDE4E9),
          surface: Color(0xFF10161C),
        ),
        useMaterial3: true,
        fontFamily: 'Arial',
      ),
      home: const PulseShell(),
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

  @override
  void initState() {
    super.initState();
    unawaited(_refreshTelemetry());
    timer = Timer.periodic(const Duration(seconds: 5), (_) {
      unawaited(_refreshTelemetry());
    });
  }

  Future<void> _refreshTelemetry() async {
    await telemetry.refresh();
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    timer?.cancel();
    telemetry.dispose();
    super.dispose();
  }

  void go(int next) => setState(() => index = next);

  @override
  Widget build(BuildContext context) {
    final pages = [
      DashboardPage(data: telemetry.snapshot, onNavigate: go),
      HardwareHealthPage(telemetry: telemetry),
      SmartRouterPage(data: telemetry.snapshot),
      const StorageHealerPage(),
      const ConverterPage(),
      const SettingsPage(),
    ];
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF0B0F13),
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
            tooltip: 'Refresh telemetry',
            onPressed: telemetry.refreshing ? null : _refreshTelemetry,
            icon: const Icon(Icons.refresh_rounded, size: 20),
          ),
        ],
      ),
      body: SafeArea(child: pages[index]),
      bottomNavigationBar: NavigationBar(
        backgroundColor: const Color(0xFF0B0F13),
        indicatorColor: const Color(0xFF1A2229),
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
