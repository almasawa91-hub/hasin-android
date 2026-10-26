package com.hasin.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasin.mobile.core.ThreatScoreEngine
import com.hasin.mobile.data.AppScannerRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel

class MainViewModel(private val repository: AppScannerRepository) : ViewModel() {
    var apps by mutableStateOf<List<ThreatScoreEngine.AppThreatResult>>(emptyList())
        private set

    init {
        scanApps()
    }

    fun scanApps() {
        apps = repository.getInstalledApps()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel = viewModel<MainViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return MainViewModel(AppScannerRepository(applicationContext)) as T
                            }
                        }
                    )
                    HasinDashboard(viewModel)
                }
            }
        }
    }
}

@Composable
fun HasinDashboard(viewModel: MainViewModel) {
    val apps = viewModel.apps

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "حصين",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "تم فحص ${apps.size} تطبيق",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps) { app ->
                AppRiskCard(app)
            }
        }
    }
}

@Composable
fun AppRiskCard(app: ThreatScoreEngine.AppThreatResult) {
    val riskColor = when {
        app.score >= 60 -> Color(0xFFFF5252) // أحمر
        app.score >= 30 -> Color(0xFFFFD740) // أصفر
        else -> Color(0xFF69F0AE)             // أخضر
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = app.appName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(riskColor, shape = MaterialTheme.shapes.medium)
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "${app.score}", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            
            if (app.riskFactors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "عوامل الخطر:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    app.riskFactors.take(3).forEach { factor ->
                        Surface(
                            color = Color(0xFFFF5252).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(text = factor, fontSize = 10.sp, color = Color(0xFFFF5252), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (app.riskFactors.size > 3) {
                        Text(text = "+${app.riskFactors.size - 3}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
