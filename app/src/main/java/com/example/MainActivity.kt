package com.example

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val dao: ConfigDao) : ViewModel() {
    val config = dao.getConfig().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    fun saveConfig(email: String, fullName: String, pass: String, user: String, mode: String) {
        viewModelScope.launch {
            dao.insertConfig(UserConfig(email = email, fullName = fullName, password = pass, username = user, mode = mode))
        }
    }
}

class MainViewModelFactory(private val dao: ConfigDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val viewModel: MainViewModel by viewModels { MainViewModelFactory(database.configDao()) }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                AutomatorApp(viewModel)
            }
        }
    }
}

@Composable
fun AutomatorApp(viewModel: MainViewModel? = null) {
    val backgroundBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF2A54), // Hot Pink
                Color(0xFF9027FB), // Bright Purple
                Color(0xFF2464FE)  // Bright Blue
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 2500f)
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            MainScreenContent(viewModel)
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (enabledService in enabledServices) {
        val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
        if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == service.name) {
            return true
        }
    }
    return false
}

@Composable
fun MainScreenContent(viewModel: MainViewModel?) {
    val config by viewModel?.config?.collectAsStateWithLifecycle(initialValue = null) ?: remember { mutableStateOf(null) }
    
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("Normal Signup") }
    
    LaunchedEffect(config) {
        config?.let {
            email = it.email
            fullName = it.fullName
            password = it.password
            username = it.username
            selectedMode = it.mode
        }
    }
    
    val modes = listOf("Normal Signup", "Accounts Center")
    var statusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        HeaderSection()
        
        Spacer(modifier = Modifier.height(48.dp))
        
        GlassTextField(
            value = email,
            onValueChange = { email = it },
            hint = "Email Address",
            icon = Icons.Default.Email
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassTextField(
            value = fullName,
            onValueChange = { fullName = it },
            hint = "Full Name",
            icon = Icons.Default.Person
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassTextField(
            value = password,
            onValueChange = { password = it },
            hint = "Password",
            icon = Icons.Default.Lock,
            isPassword = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        GlassTextField(
            value = username,
            onValueChange = { username = it },
            hint = "Username",
            icon = Icons.Default.AccountCircle
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        GlassToggle(
            selectedOption = selectedMode,
            options = modes,
            onOptionSelected = { selectedMode = it }
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        var isStarted by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable(enabled = !isStarted) {
                    viewModel?.saveConfig(email, fullName, password, username, selectedMode)
                    if (!isAccessibilityServiceEnabled(context, AutomationService::class.java)) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        isStarted = true
                        statusText = "Initializing $selectedMode..."
                        scope.launch {
                            delay(2000)
                            statusText = "Launching Instagram..."
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                            } else {
                                statusText = "Instagram app not found."
                                isStarted = false
                            }
                        }
                    }
                }
        ) {
            GlassContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .then(if (isStarted) Modifier.background(Color.Black.copy(alpha = 0.2f)) else Modifier),
                shape = RoundedCornerShape(32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Start Automation",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        
        FooterSection()
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Automator",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Smart Account Creator by @Themanuwus",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        GlassContainer(
            modifier = Modifier.size(60.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Logo",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun FooterSection() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text("Secure", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(12.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Text("Smart", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(12.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        Text("Reliable", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    GlassContainer(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(hint, color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun GlassToggle(selectedOption: String, options: List<String>, onOptionSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    GlassContainer(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = selectedOption == option
                val bgModifier = if (isSelected) Modifier.background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp)) else Modifier.background(Color.Transparent)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .then(bgModifier)
                        .clickable { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GlassContainer(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY) 
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
