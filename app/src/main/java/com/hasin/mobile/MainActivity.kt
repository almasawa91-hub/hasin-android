package com.hasin.mobile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hasin.mobile.core.network.HasinVpnService
import com.hasin.mobile.data.AppScannerRepository
import com.hasin.mobile.data.HasinDatabase
import com.hasin.mobile.data.entity.NetworkConnectionEntity
import com.hasin.mobile.data.entity.ScannedAppEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

class HasinViewModel(private val repository: AppScannerRepository) : ViewModel() {
    val apps: StateFlow<List<ScannedAppEntity>> = repository.getAllApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalCount: StateFlow<Int> = repository.getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val averageScore: StateFlow<Double> = repository.getAverageScore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val highRiskCount: StateFlow<Int> = repository.getHighRiskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val recentConnections: StateFlow<List<NetworkConnectionEntity>> = repository.getRecentConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var isScanning by mutableStateOf(false)
        private set

    private val _isVpnRunning = MutableStateFlow(false)
    val isVpnRunning: StateFlow<Boolean> = _isVpnRunning

    var vpnIntent by mutableStateOf<Intent?>(null)
        private set

    fun scan() {
        viewModelScope.launch {
            isScanning = true
            repository.scanAllApps()
            isScanning = false
        }
    }

    fun requestVpnStart(context: Context) {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnIntent = intent
        } else {
            confirmAndStartVpn(context)
        }
    }

    fun confirmAndStartVpn(context: Context) {
        HasinVpnService.startService(context)
        _isVpnRunning.value = true
        vpnIntent = null
    }

    fun stopVpn(context: Context) {
        HasinVpnService.stopService(context)
        _isVpnRunning.value = false
    }

    fun clearVpnIntent() {
        vpnIntent = null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = HasinDatabase.getInstance(this)
        val repository = AppScannerRepository(applicationContext, db.scannedAppDao())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val viewModel: HasinViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return HasinViewModel(repository) as T
                            }
                        }
                    )

                    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            viewModel.confirmAndStartVpn(applicationContext)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (viewModel.totalCount.value == 0) viewModel.scan()
                    }

                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(viewModel, navController)
                        }
                        composable("network") {
                            NetworkMonitorScreen(
                                viewModel = viewModel,
                                navController = navController,
                                onVpnIntentReady = { intent -> vpnLauncher.launch(intent) }
                            )
                        }
                        composable("details/{packageName}") { backStack ->
                            val pkg = backStack.arguments?.getString("packageName") ?: return@composable
                            AppDetailsScreen(viewModel, pkg, navController)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: HasinViewModel, navController: NavController) {
    val total by viewModel.totalCount.collectAsState()
    val avg by viewModel.averageScore.collectAsState()
    val high by viewModel.highRiskCount.collectAsState()
    val scanning by viewModel.isScanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حصين", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.scan() }, enabled = !scanning) {
                        if (scanning) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "إعادة فحص")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("التطبيقات", "$total", Icons.Default.CheckCircle, Color(0xFF42A5F5), Modifier.weight(1f))
                StatCard("متوسط الخطر", "${avg.toInt()}", Icons.Default.Warning, scoreColor(avg.toInt()), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate("network") },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF69F0AE).copy(alpha = 0.15f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مراقبة الشبكة المباشرة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("تحليل حزم البيانات محلياً للكشف عن الاتصالات المشبوهة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(180f))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("أكثر التطبيقات خطورة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            
            val apps by viewModel.apps.collectAsState()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.take(10)) { app ->
                    AppRow(app) { navController.navigate("details/${app.packageName}") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(
    viewModel: HasinViewModel, 
    navController: NavController,
    onVpnIntentReady: (Intent) -> Unit
) {
    val connections by viewModel.recentConnections.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()
    val vpnIntent = viewModel.vpnIntent

    LaunchedEffect(vpnIntent) {
        if (vpnIntent != null) {
            onVpnIntentReady(vpnIntent)
            viewModel.clearVpnIntent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مراقبة الشبكة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (isVpnRunning) viewModel.stopVpn(navController.context) 
                        else viewModel.requestVpnStart(navController.context)
                    }) {
                        if (isVpnRunning) {
                            Icon(Icons.Default.Stop, contentDescription = "إيقاف", tint = Color(0xFFFF5252))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "بدء", tint = Color(0xFF69F0AE))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isVpnRunning) Color(0xFF69F0AE).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isVpnRunning) Icons.Default.Shield else Icons.Default.ShieldOutlined,
                        contentDescription = null,
                        tint = if (isVpnRunning) Color(0xFF69F0AE) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (isVpnRunning) "المراقبة نشطة" else "المراقبة متوقفة", fontWeight = FontWeight.Bold)
                        Text("تحليل حزم الشبكة محلياً للكشف عن الاتصالات المشبوهة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("آخر الاتصالات المشبوهة المسجلة", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            
            if (connections.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("لا توجد اتصالات مشبوهة مسجلة حتى الآن\n(جرب فتح تطبيقات أخرى أثناء تشغيل المراقبة)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(connections) { conn ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${conn.destIp}:${conn.destPort}", fontWeight = FontWeight.Medium)
                                    Text("${conn.protocol} • ${conn.reason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = formatTime(conn.timestamp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppRow(app: ScannedAppEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(scoreColor(app.threatScore)),
                contentAlignment = Alignment.Center
            ) {
                Text("${app.threatScore}", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                    when (app.scoreTrend) {
                        1 -> Icon(Icons.Default.TrendingUp, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        -1 -> Icon(Icons.Default.TrendingDown, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(16.dp))
                    }
                }
                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(180f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(viewModel: HasinViewModel, packageName: String, navController: NavController) {
    val apps by viewModel.apps.collectAsState()
    val app = apps.firstOrNull { it.packageName == packageName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.appName ?: "تفاصيل", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        if (app == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("لم يتم العثور على التطبيق")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = scoreColor(app.threatScore).copy(alpha = 0.15f))) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("درجة التهديد", fontSize = 14.sp)
                        Text("${app.threatScore}/100", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = scoreColor(app.threatScore))
                        if (app.previousScore != null) {
                            Text("كان: ${app.previousScore}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow("الاسم", app.appName)
                        InfoRow("الحزمة", app.packageName)
                        InfoRow("الإصدار", app.versionName)
                        InfoRow("نظام", if (app.isSystemApp) "نعم" else "لا")
                        InfoRow("إجمالي الأذونات", "${app.totalPermissions}")
                    }
                }
            }
            val high = app.highRiskPermissions.fromJson()
            if (high.isNotEmpty()) {
                item { Text("أذونات عالية الخطورة (${high.size})", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252)) }
                items(high) { perm -> PermissionChip(perm, Color(0xFFFF5252)) }
            }
            val medium = app.mediumRiskPermissions.fromJson()
            if (medium.isNotEmpty()) {
                item { Text("أذونات متوسطة الخطورة (${medium.size})", fontWeight = FontWeight.Bold, color = Color(0xFFFFD740)) }
                items(medium) { perm -> PermissionChip(perm, Color(0xFFFFD740)) }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PermissionChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

fun scoreColor(score: Int): Color = when {
    score >= 60 -> Color(0xFFFF5252)
    score >= 30 -> Color(0xFFFFD740)
    else -> Color(0xFF69F0AE)
}

fun String.fromJson(): List<String> {
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "الآن"
        diff < 3600000 -> "${diff / 60000} دقيقة"
        else -> "${diff / 3600000} ساعة"
    }
}
