import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';

void main() => runApp(const PulseOSApp());

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
  final data = DemoTelemetry();
  Timer? timer;

  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!mounted) return;
      setState(data.tick);
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  void go(int next) => setState(() => index = next);

  @override
  Widget build(BuildContext context) {
    final pages = [
      DashboardPage(data: data, onNavigate: go),
      SmartRouterPage(data: data),
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
            _Logo(),
            SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('PulseOS', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
                Text('DEVICE HEALTH & HEALING', style: TextStyle(fontSize: 7, letterSpacing: 1.15, color: Color(0xFF66727B))),
              ],
            ),
          ],
        ),
        actions: [
          const _LivePill(),
          IconButton(
            tooltip: 'Refresh',
            onPressed: () => setState(data.refresh),
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
          NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: 'Overview'),
          NavigationDestination(icon: Icon(Icons.alt_route_outlined), selectedIcon: Icon(Icons.alt_route), label: 'Router'),
          NavigationDestination(icon: Icon(Icons.cleaning_services_outlined), selectedIcon: Icon(Icons.cleaning_services), label: 'Storage'),
          NavigationDestination(icon: Icon(Icons.swap_horiz_outlined), selectedIcon: Icon(Icons.swap_horiz), label: 'Converter'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}

class DemoTelemetry {
  final r = Random();
  double cpu = 42, ram = 61, temp = 54, battery = 69, clock = 3.4;
  int processes = 184;

  void tick() {
    cpu = _clamp(cpu + (r.nextDouble() - .5) * 10, 18, 96);
    ram = _clamp(ram + (r.nextDouble() - .5) * 5, 28, 93);
    temp = _clamp(temp + (r.nextDouble() - .5) * 2, 42, 88);
    clock = _clamp(clock + (r.nextDouble() - .5) * .08, 1.8, 4.6);
    processes = (processes + r.nextInt(9) - 4).clamp(90, 520);
  }

  void refresh() => tick();

  static double _clamp(double v, double min, double max) => v.clamp(min, max).toDouble();

  int get performance => (100 - max(0, cpu - 55) * .55 - max(0, ram - 65) * .60).clamp(0, 100).round();
  int get hardware => (100 - max(0, temp - 60) * 1.3).clamp(0, 100).round();
  int get storage => 87;
  int get batteryHealth => 92;
  int get score => (performance * .40 + hardware * .25 + storage * .20 + batteryHealth * .15).round();

  List<String> get problems {
    final p = <String>[];
    if (cpu > 80) p.add('High CPU usage');
    if (ram > 85) p.add('High memory pressure');
    if (temp > 78) p.add('High temperature');
    if (storage > 88) p.add('Storage nearly full');
    return p;
  }
}

class DashboardPage extends StatelessWidget {
  final DemoTelemetry data;
  final ValueChanged<int> onNavigate;
  const DashboardPage({super.key, required this.data, required this.onNavigate});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
      children: [
        const Text('OVERVIEW', style: _kicker),
        const SizedBox(height: 4),
        const Text('Device Health & Healing Center', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        _Card(
          onTap: () => _show(context, 'Device Health Score', _detail([
            'Overall score', '${data.score}/100',
            'Performance', '${data.performance}%',
            'Hardware', '${data.hardware}%',
            'Storage', '${data.storage}%',
            'Battery', '${data.batteryHealth}%',
          ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('DEVICE HEALTH SCORE', style: _kicker),
            Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text('${data.score}', style: const TextStyle(fontSize: 54, fontWeight: FontWeight.w900)),
              const Padding(padding: EdgeInsets.only(bottom: 8, left: 3), child: Text('/100', style: TextStyle(color: Color(0xFF71808A), fontSize: 13))),
              const SizedBox(width: 12),
              Padding(padding: const EdgeInsets.only(bottom: 9), child: Text(data.score >= 75 ? 'GOOD' : 'NEEDS ATTENTION', style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w800, letterSpacing: 1))),
            ]),
            const Text('Sustained CPU/RAM pressure, thermal condition, storage pressure and battery health are combined into the device-level score.', style: TextStyle(fontSize: 10, color: Color(0xFF77838D))),
            const SizedBox(height: 12),
            _HealthBar('Performance', data.performance),
            _HealthBar('Hardware', data.hardware),
            _HealthBar('Storage', data.storage),
            _HealthBar('Battery', data.batteryHealth),
          ]),
        ),
        const SizedBox(height: 8),
        const Text('LIVE TELEMETRY', style: _kicker),
        const SizedBox(height: 6),
        GridView.count(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisCount: 2,
          crossAxisSpacing: 7,
          mainAxisSpacing: 7,
          childAspectRatio: 1.55,
          children: [
            _Metric('CPU', '${data.cpu.round()}%', 'Chrome · top process', () => _show(context, 'CPU Details', _detail(['Current load','${data.cpu.round()}%','Top process','Chrome','Source','OSHI / native telemetry','Status',data.cpu > 80 ? 'Attention' : 'Normal']))),
            _Metric('MEMORY', '${data.ram.round()}%', 'Android Studio · top memory', () => _show(context, 'Memory Details', _detail(['Usage','${data.ram.round()}%','Top process','Android Studio','Snapshot','Live','Status',data.ram > 85 ? 'Attention' : 'Normal']))),
            _Metric('TEMPERATURE', '${data.temp.round()}°C', 'Thermal condition', () => _show(context, 'Temperature Details', _detail(['Current','${data.temp.round()}°C','Threshold','78°C','Status',data.temp > 78 ? 'Attention' : 'Normal','Note','Some mobile platforms restrict sensor access']))),
            _Metric('THREADS', '${data.processes}', 'Running processes', () => _show(context, 'Process Details', _detail(['Processes','${data.processes}','CPU leader','Chrome','Memory leader','Android Studio']))),
            _Metric('CLOCK', '${data.clock.toStringAsFixed(1)} GHz', 'Current frequency', () => _show(context, 'Clock Details', _detail(['Frequency','${data.clock.toStringAsFixed(1)} GHz','Mode','Dynamic','Trend','Live']))),
            _Metric('BATTERY', '${data.battery.round()}%', 'Charge level', () => _show(context, 'Battery Details', _detail(['Charge','${data.battery.round()}%','Battery health','92%','Cycle count','184','Status','Available']))),
          ],
        ),
        const SizedBox(height: 8),
        _ChartCard('CPU LOAD', '${data.cpu.round()}%', _series(data.cpu, 20, 95)),
        _ChartCard('MEMORY PRESSURE', '${data.ram.round()}%', _series(data.ram, 25, 92)),
        _ChartCard('CPU TEMPERATURE', '${data.temp.round()}°C', _series(data.temp, 42, 88)),
        _Card(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('ATTENTION', 'Active Problems'),
            const SizedBox(height: 8),
            if (data.problems.isEmpty)
              const _ProblemLine('✓ No active threshold breaches', good: true)
            else
              for (final p in data.problems) _ProblemLine(p),
            const SizedBox(height: 8),
            Row(children: [
              Expanded(child: OutlinedButton(onPressed: data.problems.isEmpty ? null : () => _show(context, 'Review & Fix', const Text('PulseOS will route each problem to its correct module. CPU/RAM → Smart Router. Storage → Storage Healer.')) , child: const Text('REVIEW & FIX'))),
              const SizedBox(width: 8),
              Expanded(child: FilledButton(onPressed: () => _show(context, 'Safe Fix', const Text('Only guarded, reversible actions are allowed. Storage cleanup uses review/quarantine; performance fixes are review-first.')), child: const Text('SAFE ACTION'))),
            ]),
          ]),
        ),
        _Card(
          onTap: () => _show(context, 'Predictive Health', _detail(['CPU pressure','72% · rising','RAM pressure','61% · stable','Thermal risk','34% · stable','Storage risk','18% · stable','Confidence','86%'])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('LOOKING AHEAD', 'Predictive Health'),
            const SizedBox(height: 9),
            _miniValue('CPU pressure', '72% · rising'),
            _miniValue('RAM pressure', '61% · stable'),
            _miniValue('Thermal risk', '34% · stable'),
            _miniValue('Storage risk', '18% · stable'),
            const Text('Trend signals are shown separately from raw telemetry so the prediction card explains what may happen next.', style: TextStyle(fontSize: 9, color: Color(0xFF6D7983))),
          ]),
        ),
        Row(children: [
          Expanded(child: _Card(
            onTap: () => _show(context, 'Battery & Power', _detail(['Charge','${data.battery.round()}%','Health','92%','Cycle count','184','Power source','Battery'])),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const _Header('POWER','Battery & Power'),
              const SizedBox(height: 10),
              Row(children: [const Icon(Icons.battery_5_bar_rounded, size: 34), const SizedBox(width: 8), Text('${data.battery.round()}%', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800))]),
              _info('Health','92%'), _info('Cycles','184'), _info('Source','Battery'),
            ]),
          )),
          const SizedBox(width: 7),
          Expanded(child: _Card(
            onTap: () => _show(context, 'Storage Intelligence', _detail(['Used','42%','Free','58%','Largest area','Downloads · 120 GB','Build artifacts','414 MB'])),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const _Header('DISK','Storage Intelligence'),
              const SizedBox(height: 8),
              const Center(child: SizedBox(width: 86, height: 86, child: CustomPaint(painter: _DonutPainter()))),
              _info('Downloads','120 GB'), _info('Projects','98 GB'), _info('Developer','76 GB'),
            ]),
          )),
        ]),
        _Card(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('RESOURCE ANALYSIS','Top Resource Drainers'),
            const SizedBox(height: 8),
            _info('CPU leader','Chrome · 28%'),
            _info('Memory leader','Android Studio · 2.4 GB'),
            _info('Processes','${data.processes}'),
            const SizedBox(height: 8),
            OutlinedButton(onPressed: () => onNavigate(1), child: const Text('OPEN SMART ROUTER →')),
          ]),
        ),
        Row(children: [
          Expanded(child: _Card(
            onTap: () => _show(context, 'Hardware Information', _detail(['Processor','Mobile CPU','Memory','Device RAM','Thermal state',data.temp > 78 ? 'Attention' : 'Normal','Telemetry','Native platform APIs'])),
            child: const _Header('DEVICE','Hardware Information'),
          )),
          const SizedBox(width: 7),
          Expanded(child: _Card(
            onTap: () => _show(context, 'System Watcher', const Text('Watcher checks file name, size, destination/category, incomplete download status and organization policy. Malware scanning is not claimed.')),
            child: const _Header('AUTOMATION','System Watcher'),
          )),
        ]),
        _Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const _Header('EVENTS','Recent Activity'),
          for (final x in const ['Storage scan completed','New file detected','Build artifact identified','CPU spike detected'])
            ListTile(contentPadding: EdgeInsets.zero, dense: true, leading: const Icon(Icons.circle, size: 6, color: Color(0xFF7F8B94)), title: Text(x, style: const TextStyle(fontSize: 10)), trailing: const Text('recent', style: TextStyle(fontSize: 8, color: Color(0xFF5E6973)))),
        ])),
      ],
    );
  }

  List<double> _series(double center, double low, double high) =>
      List.generate(24, (i) => (center + sin(i * .8) * (high-low) * .06 - cos(i*.2)*2).clamp(low, high).toDouble());
}

class SmartRouterPage extends StatelessWidget {
  final DemoTelemetry data;
  const SmartRouterPage({super.key, required this.data});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(12), children: [
    const _SectionTitle('Smart Router','Resource analysis and guided performance actions'),
    _Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const _Header('LIVE PROCESS ANALYSIS','Top Resource Drainers'),
      _routerRow('Chrome','28% CPU','480 MB',Icons.web),
      _routerRow('Android Studio','2.4 GB RAM','19% CPU',Icons.code),
      _routerRow('Code Helper','8% CPU','620 MB',Icons.memory),
    ])),
    _Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const _Header('GUIDED ACTIONS','Safe Performance Workflow'),
      const SizedBox(height: 8),
      const Text('PulseOS does not terminate a process blindly. Review the process, understand its impact, then choose an action.', style: TextStyle(fontSize: 10, color: Color(0xFF74808A))),
      const SizedBox(height: 9),
      Wrap(spacing: 7, runSpacing: 7, children: [
        FilledButton(onPressed: () => _show(context,'Process Review',const Text('Review process details, CPU, RAM and recent activity before acting.')), child: const Text('REVIEW PROCESS')),
        OutlinedButton(onPressed: () => _show(context,'Safe Action',const Text('Termination is user-triggered and only requested after review.')), child: const Text('REQUEST ACTION')),
      ]),
    ])),
  ]);
}

class StorageHealerPage extends StatelessWidget {
  const StorageHealerPage({super.key});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(12), children: [
    const _SectionTitle('Storage Healer','Analyze → Review → Quarantine → Verify'),
    _Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const _Header('STORAGE INTELLIGENCE','What is using your space?'),
      const SizedBox(height: 10),
      const Center(child: SizedBox(width: 145,height:145,child:CustomPaint(painter:_DonutPainter()))),
      const SizedBox(height: 12),
      _info('Downloads','120 GB'), _info('Projects','98 GB'), _info('Developer','76 GB'), _info('Documents','42 GB'),
      const SizedBox(height: 9),
      FilledButton(onPressed: () => _show(context,'Scan Complete',_detail(['Largest area','Downloads','Build artifacts','414 MB','Safe cleanup candidates','Review required'])), child: const Text('SCAN NOW')),
    ])),
    _Card(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const _Header('SAFE CLEANUP','Review candidates'),
      const SizedBox(height: 8),
      _candidate('build/', '414 MB', 'Build artifact', true),
      _candidate('cache/', '180 MB', 'Temporary cache', true),
      _candidate('project.zip', '1.2 GB', 'User file · review', false),
      const SizedBox(height: 7),
      Row(children:[
        Expanded(child: OutlinedButton(onPressed: () => _show(context,'Quarantine',const Text('Selected safe candidates would be moved to reversible quarantine.')), child: const Text('QUARANTINE'))),
        const SizedBox(width:7),
        Expanded(child: FilledButton(onPressed: () => _show(context,'Verify',const Text('After cleanup, PulseOS rechecks free space and recalculates storage health.')), child: const Text('VERIFY'))),
      ]),
    ])),
  ]);
}

class ConverterPage extends StatefulWidget {
  const ConverterPage({super.key});
  @override State<ConverterPage> createState()=>_ConverterPageState();
}
class _ConverterPageState extends State<ConverterPage> {
  String selected='Document';
  String format='PDF';
  bool running=false;
  final types=['Document','Image','Archive','Media','Ebook'];
  @override
  Widget build(BuildContext context)=>ListView(padding:const EdgeInsets.all(12),children:[
    const _SectionTitle('Offline Converter','Local file conversion workflows'),
    _Card(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      const _Header('CONVERT','Choose a workflow'),
      const SizedBox(height:10),
      DropdownButtonFormField<String>(initialValue:selected,items:types.map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),onChanged:(v)=>setState(()=>selected=v!),decoration:const InputDecoration(labelText:'Input type')),
      const SizedBox(height:8),
      DropdownButtonFormField<String>(initialValue:format,items:const ['PDF','DOCX','PNG','JPG','ZIP'].map((x)=>DropdownMenuItem(value:x,child:Text(x))).toList(),onChanged:(v)=>setState(()=>format=v!),decoration:const InputDecoration(labelText:'Output format')),
      const SizedBox(height:12),
      Row(children:[
        Expanded(child:OutlinedButton(onPressed:()=>_show(context,'File Picker',const Text('Choose a local file. The prototype keeps the workflow offline-first.')),child:const Text('CHOOSE FILE'))),
        const SizedBox(width:8),
        Expanded(child:FilledButton(onPressed:running?null:()=>setState(()=>running=true),child:Text(running?'CONVERTING…':'CONVERT'))),
      ]),
      if(running) ...[const SizedBox(height:10),const LinearProgressIndicator(),const SizedBox(height:6),const Text('Conversion workflow started · local processing prototype',style:TextStyle(fontSize:9,color:Color(0xFF6F7B85)))],
    ])),
    const _Card(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      _Header('LOCAL-FIRST','Privacy'),
      SizedBox(height:6),
      Text('Files are intended to stay on-device. Cloud upload is not required by the product concept.',style:TextStyle(fontSize:10,color:Color(0xFF78848E))),
    ])),
  ]);
}

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});
  @override
  Widget build(BuildContext context)=>ListView(padding:const EdgeInsets.all(12),children:[
    const _SectionTitle('Settings','PulseOS mobile client preferences'),
    _Card(child:Column(children:[
      SwitchListTile(contentPadding:EdgeInsets.zero,title:const Text('Local-first mode',style:TextStyle(fontSize:12)),subtitle:const Text('Prefer on-device processing',style:TextStyle(fontSize:9,color:Color(0xFF6F7B85))),value:true,onChanged:(_)=>{}),
      SwitchListTile(contentPadding:EdgeInsets.zero,title:const Text('Health alerts',style:TextStyle(fontSize:12)),subtitle:const Text('Alert on sustained risk',style:TextStyle(fontSize:9,color:Color(0xFF6F7B85))),value:true,onChanged:(_)=>{}),
      SwitchListTile(contentPadding:EdgeInsets.zero,title:const Text('Watcher',style:TextStyle(fontSize:12)),subtitle:const Text('File organization notifications',style:TextStyle(fontSize:9,color:Color(0xFF6F7B85))),value:true,onChanged:(_)=>{}),
    ])),
    const _Card(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Text('ABOUT',style:_kicker),
      SizedBox(height:5),
      Text('PulseOS Mobile v0.2 Prototype',style:TextStyle(fontWeight:FontWeight.w700)),
      SizedBox(height:5),
      Text('MONITOR → UNDERSTAND → PREDICT → HEAL',style:TextStyle(fontSize:9,color:Color(0xFF7A8690),letterSpacing:1)),
    ])),
  ]);
}

Widget _routerRow(String name,String cpu,String ram,IconData icon)=>Container(margin:const EdgeInsets.only(bottom:8),padding:const EdgeInsets.all(10),decoration:BoxDecoration(color:const Color(0xFF0C1217),borderRadius:BorderRadius.circular(8),border:Border.all(color:const Color(0xFF20272E))),child:Row(children:[Icon(icon,size:18,color:const Color(0xFFAFB9C0)),const SizedBox(width:9),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(name,style:const TextStyle(fontSize:11,fontWeight:FontWeight.w700)),Text('$cpu · $ram',style:const TextStyle(fontSize:8,color:Color(0xFF71808A)))])),OutlinedButton(onPressed:()=>{},child:const Text('DETAILS'))]));

Widget _candidate(String name,String size,String kind,bool safe)=>ListTile(contentPadding:EdgeInsets.zero,dense:true,leading:Icon(safe?Icons.check_circle_outline:Icons.info_outline,size:18,color:safe?const Color(0xFF9EAEA3):const Color(0xFF9D8A72)),title:Text(name,style:const TextStyle(fontSize:10,fontWeight:FontWeight.w700)),subtitle:Text('$kind · $size',style:const TextStyle(fontSize:8,color:Color(0xFF697680))),trailing:Checkbox(value:safe,onChanged:(_)=>{}));

Widget _detail(List<String> pairs)=>GridView.builder(shrinkWrap:true,physics:const NeverScrollableScrollPhysics(),gridDelegate:const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount:2,childAspectRatio:2.8,crossAxisSpacing:7,mainAxisSpacing:7),itemCount:pairs.length,itemBuilder:(c,i)=>Container(padding:const EdgeInsets.all(9),decoration:BoxDecoration(color:const Color(0xFF0C1217),border:Border.all(color:const Color(0xFF20272E)),borderRadius:BorderRadius.circular(8)),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(pairs[i],style:const TextStyle(fontSize:7,color:Color(0xFF68747E))),if(i+1<pairs.length) Text(pairs[i+1],style:const TextStyle(fontSize:10,fontWeight:FontWeight.w700))])));

void _show(BuildContext c,String title,Widget body)=>showModalBottomSheet(context:c,backgroundColor:const Color(0xFF10161C),showDragHandle:true,isScrollControlled:true,builder:(_)=>Padding(padding:const EdgeInsets.fromLTRB(16,2,16,24),child:SingleChildScrollView(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(title,style:const TextStyle(fontSize:18,fontWeight:FontWeight.w800)),const SizedBox(height:12),body,const SizedBox(height:8)]))));

class _Card extends StatelessWidget{
  final Widget child; final VoidCallback? onTap;
  const _Card({required this.child,this.onTap});
  @override Widget build(BuildContext c)=>Container(margin:const EdgeInsets.only(bottom:8),decoration:BoxDecoration(color:const Color(0xFF10161C),border:Border.all(color:const Color(0xFF242C33)),borderRadius:BorderRadius.circular(11)),child:Material(color:Colors.transparent,child:InkWell(borderRadius:BorderRadius.circular(11),onTap:onTap,child:Padding(padding:const EdgeInsets.all(13),child:child))));
}
class _Metric extends StatelessWidget{
  final String title,value,sub; final VoidCallback tap;
  const _Metric(this.title,this.value,this.sub,this.tap);
  @override Widget build(BuildContext c)=>_Card(onTap:tap,child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(title,style:_kicker),const SizedBox(height:5),Text(value,style:const TextStyle(fontSize:22,fontWeight:FontWeight.w900)),Text(sub,maxLines:1,overflow:TextOverflow.ellipsis,style:const TextStyle(fontSize:8,color:Color(0xFF697680)))]));
}
class _Header extends StatelessWidget{final String k,t;const _Header(this.k,this.t);@override Widget build(BuildContext c)=>Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(k,style:_kicker),const SizedBox(height:4),Text(t,style:const TextStyle(fontSize:13,fontWeight:FontWeight.w700))]);}
class _SectionTitle extends StatelessWidget{final String t,s;const _SectionTitle(this.t,this.s);@override Widget build(BuildContext c)=>Padding(padding:const EdgeInsets.only(bottom:10),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(t,style:const TextStyle(fontSize:22,fontWeight:FontWeight.w800)),Text(s,style:const TextStyle(fontSize:9,color:Color(0xFF697680)))]));}
class _LivePill extends StatelessWidget{const _LivePill();@override Widget build(BuildContext c)=>Container(margin:const EdgeInsets.only(right:4),padding:const EdgeInsets.symmetric(horizontal:8,vertical:5),decoration:BoxDecoration(border:Border.all(color:const Color(0xFF283129)),borderRadius:BorderRadius.circular(18)),child:const Text('● LIVE',style:TextStyle(fontSize:8,color:Color(0xFFA8B9AC),fontWeight:FontWeight.w700)));}
class _Logo extends StatelessWidget{const _Logo();@override Widget build(BuildContext c)=>Container(width:32,height:32,decoration:BoxDecoration(color:const Color(0xFF151C22),border:Border.all(color:const Color(0xFF39434A)),borderRadius:BorderRadius.circular(9)),alignment:Alignment.center,child:const Text('P',style:TextStyle(fontWeight:FontWeight.w900)));}
class _HealthBar extends StatelessWidget{final String t;final int v;const _HealthBar(this.t,this.v);@override Widget build(BuildContext c)=>Padding(padding:const EdgeInsets.only(bottom:8),child:Column(children:[Row(children:[Expanded(child:Text(t,style:const TextStyle(fontSize:9,color:Color(0xFF808B94)))),Text('$v%',style:const TextStyle(fontSize:9,fontWeight:FontWeight.w700))]),const SizedBox(height:4),ClipRRect(borderRadius:BorderRadius.circular(9),child:LinearProgressIndicator(value:v/100,minHeight:4,backgroundColor:const Color(0xFF1C2329),valueColor:const AlwaysStoppedAnimation(Color(0xFFB9C3C9))))]));}
class _ProblemLine extends StatelessWidget{final String text;final bool good;const _ProblemLine(this.text,{this.good=false});@override Widget build(BuildContext c)=>Container(margin:const EdgeInsets.only(bottom:6),padding:const EdgeInsets.all(9),decoration:BoxDecoration(color:const Color(0xFF0C1217),border:Border.all(color:const Color(0xFF20272E)),borderRadius:BorderRadius.circular(8)),child:Row(children:[Container(width:4,height:23,decoration:BoxDecoration(color:good?const Color(0xFF859C8D):const Color(0xFF9A7F68),borderRadius:BorderRadius.circular(3))),const SizedBox(width:8),Expanded(child:Text(text,style:const TextStyle(fontSize:9,fontWeight:FontWeight.w600))) ]));}
class _ChartCard extends StatelessWidget{final String title,current;final List<double> points;const _ChartCard(this.title,this.current,this.points);@override Widget build(BuildContext c)=>_Card(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Row(children:[Text(title,style:_kicker),const Spacer(),const Text('LIVE',style:TextStyle(fontSize:7,color:Color(0xFF77838C)))]),const SizedBox(height:6),SizedBox(height:90,child:CustomPaint(painter:_LinePainter(points))),const SizedBox(height:5),Row(children:[Text(current,style:const TextStyle(fontSize:16,fontWeight:FontWeight.w800)),const SizedBox(width:5),const Text('recent trend',style:TextStyle(fontSize:8,color:Color(0xFF64707A)))])]));}
Widget _miniValue(String a,String b)=>Container(margin:const EdgeInsets.only(bottom:6),padding:const EdgeInsets.all(8),decoration:BoxDecoration(color:const Color(0xFF0C1217),border:Border.all(color:const Color(0xFF20272E)),borderRadius:BorderRadius.circular(8)),child:Row(children:[Expanded(child:Text(a,style:const TextStyle(fontSize:8,color:Color(0xFF68747E)))),Text(b,style:const TextStyle(fontSize:9,fontWeight:FontWeight.w700))]));
Widget _info(String a,String b)=>Padding(padding:const EdgeInsets.symmetric(vertical:3),child:Row(children:[Expanded(child:Text(a,style:const TextStyle(fontSize:8,color:Color(0xFF697680)))),Text(b,style:const TextStyle(fontSize:8,fontWeight:FontWeight.w700,color:Color(0xFFC4CCD0)))]));
const _kicker=TextStyle(fontSize:7,letterSpacing:1.25,fontWeight:FontWeight.w800,color:Color(0xFF68747E));

class _LinePainter extends CustomPainter{final List<double> p;_LinePainter(this.p);@override void paint(Canvas c,Size s){final g=Paint()..color=const Color(0xFF1D252C);for(final f in [.2,.5,.8]){c.drawLine(Offset(0,s.height*f),Offset(s.width,s.height*f),g);}final line=Paint()..color=const Color(0xFFBEC7CE)..strokeWidth=2..style=PaintingStyle.stroke..strokeJoin=StrokeJoin.round..strokeCap=StrokeCap.round;final path=Path();for(int i=0;i<p.length;i++){final x=i/(p.length-1)*s.width;final y=s.height-((p[i]-20)/80).clamp(0,1)*(s.height-10)-5;i==0?path.moveTo(x,y):path.lineTo(x,y);}c.drawPath(path,line);}@override bool shouldRepaint(covariant _LinePainter old)=>old.p!=p;}
class _DonutPainter extends CustomPainter{const _DonutPainter();@override void paint(Canvas c,Size s){final center=Offset(s.width/2,s.height/2),r=s.width/2-7;final bg=Paint()..color=const Color(0xFF4D5962)..style=PaintingStyle.stroke..strokeWidth=16;final fg=Paint()..color=const Color(0xFFC2CBD1)..style=PaintingStyle.stroke..strokeWidth=16;c.drawCircle(center,r,bg);c.drawArc(Rect.fromCircle(center:center,radius:r),-pi/2,pi*2*.42,false,fg);final tp=TextPainter(text:const TextSpan(text:'42%',style:TextStyle(color:Color(0xFFE0E5E8),fontSize:17,fontWeight:FontWeight.w800)),textDirection:TextDirection.ltr)..layout();tp.paint(c,center-Offset(tp.width/2,tp.height/2));}@override bool shouldRepaint(covariant CustomPainter old)=>false;}
