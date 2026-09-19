package com.haiklabs.tracelight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiklabs.tracelight.repo.FootprintReport
import com.haiklabs.tracelight.ui.FootprintViewModel
import com.haiklabs.tracelight.ui.Screen
import kotlinx.coroutines.delay

private val Ink = Color(0xFF143B32)
private val Lime = Color(0xFFB9F27C)
private val Canvas = Color(0xFFF7F8F5)
private val Muted = Color(0xFF60746E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TraceLightTheme { TraceLightApp() } }
    }
}

@Composable
private fun TraceLightApp(viewModel: FootprintViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = Canvas) { padding ->
        AnimatedContent(state.screen, modifier = Modifier.padding(padding), label = "screen") { current ->
            when (current) {
                Screen.Home -> HomeScreen(
                    profile = state.profile,
                    update = viewModel::updateProfile,
                    error = state.error,
                    submit = viewModel::startScan
                )

                Screen.Scanning -> ScanningScreen(state.profile, cancel = viewModel::cancelScan)

                Screen.Preview -> PreviewScreen(
                    profile = state.profile,
                    report = state.report,
                    unlock = viewModel::unlockReport,
                    back = viewModel::editDetails
                )

                Screen.Report -> ReportScreen(
                    profile = state.profile,
                    report = state.report,
                    close = viewModel::finishAndDelete
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    profile: SearchProfile,
    update: (SearchProfile) -> Unit,
    error: String?,
    submit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
    ) {
        BrandHeader()
        Spacer(Modifier.height(26.dp))
        Surface(
            color = Ink,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Surface(color = Lime.copy(alpha = .14f), shape = RoundedCornerShape(50)) {
                    Text(
                        "YOUR DIGITAL FOOTPRINT",
                        color = Lime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "See what the internet\nknows about you.",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "A private, consent-based scan of your public presence—organized into one clear report.",
                    color = Color(0xFFC7D3CF),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Start your self-audit", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(
            "Use your own details. We never sell your search data.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Entry(
                "First name",
                profile.firstName,
                { update(profile.copy(firstName = it)) },
                Modifier.weight(1f)
            )
            Entry(
                "Last name",
                profile.lastName,
                { update(profile.copy(lastName = it)) },
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Entry(
            "Birth year",
            profile.birthYear,
            { update(profile.copy(birthYear = it.filter(Char::isDigit).take(4))) },
            Modifier.fillMaxWidth(),
            "Helps distinguish you from others"
        )
        if (error != null) Text(
            error,
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = submit,
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(56.dp)
        ) {
            Text(
                "Scan my public footprint",
                fontWeight = FontWeight.Bold
            ); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lock,
                null,
                tint = Muted,
                modifier = Modifier.size(15.dp)
            ); Text("  Encrypted • Private • Delete anytime", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Entry(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    helper: String? = null
) {
    OutlinedTextField(
        value,
        onChange,
        modifier,
        label = { Text(label) },
        supportingText = helper?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink,
            focusedLabelColor = Ink,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
}

@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(39.dp).background(Ink, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Search, null, tint = Lime) }
        Text(
            "Trace",
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        ); Text("Light", color = Color(0xFF579324), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f)); Surface(
        shape = CircleShape,
        color = Color.White
    ) {
        Icon(
            Icons.Outlined.Shield,
            "Privacy",
            tint = Ink,
            modifier = Modifier.padding(9.dp).size(20.dp)
        )
    }
    }
}

@Composable
private fun ScanningScreen(profile: SearchProfile, cancel: () -> Unit) {
    // Purely cosmetic progress: the real work runs in the ViewModel, which
    // navigates away as soon as the report arrives.
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (step < 3) { delay(1500); step++ } }
    Column(
        Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandHeader(); Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(132.dp).background(Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Lime,
                strokeWidth = 5.dp,
                modifier = Modifier.size(98.dp)
            ); Icon(
            Icons.Outlined.TravelExplore,
            null,
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
        }
        Text(
            "Mapping your footprint",
            color = Ink,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 30.dp)
        )
        Text(
            "Searching public sources for ${profile.firstName} ${profile.lastName}",
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
        )
        listOf(
            "Matching identity",
            "Checking social profiles",
            "Reviewing public mentions",
            "Organizing your report"
        ).forEachIndexed { i, text ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (i < step) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (i < step) Color(0xFF579324) else Muted
                )
                Text(
                    text,
                    color = if (i <= step) Ink else Muted,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Searching can take up to a minute",
            color = Muted,
            fontSize = 12.sp
        )
        TextButton(onClick = cancel) { Text("Cancel", color = Muted) }
    }
}

@Composable
private fun PreviewScreen(
    profile: SearchProfile,
    report: FootprintReport?,
    unlock: () -> Unit,
    back: () -> Unit
) {
    val sourceCount = report?.content?.let { Regex("https?://").findAll(it).count() } ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        BrandHeader(); Text(
        "We found your footprint",
        color = Ink,
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 28.dp)
    )
        Text(
            if (sourceCount > 0) "Report ready • $sourceCount linked sources" else "Report ready",
            color = Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).background(Color(0xFFDCE7E1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            null,
                            tint = Ink,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "${profile.firstName} ${profile.lastName}",
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            fontSize = 18.sp
                        ); Text(
                        "Identity match: High confidence",
                        color = Color(0xFF579324),
                        fontSize = 13.sp
                    )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color(0xFFE5EAE7))
                listOf(
                    Icons.Outlined.AlternateEmail to "3 social profiles",
                    Icons.Outlined.WorkOutline to "2 professional mentions",
                    Icons.Outlined.Article to "4 public references"
                ).forEach { (icon, text) -> Finding(icon, text) }
                Box(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                    Column(Modifier.blur(9.dp)) {
                        Finding(
                            Icons.Outlined.LocationOn,
                            "Possible location history"
                        ); Finding(Icons.Outlined.Link, "Related usernames and links")
                    }
                    Surface(
                        color = Ink.copy(alpha = .92f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                null,
                                tint = Lime,
                                modifier = Modifier.size(18.dp)
                            ); Text(
                            "  Full details locked",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        }
                    }
                }
            }
        }
        Surface(
            color = Color(0xFFEAF6DE),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            Text(
                "✓ No subscriptions. One report, one payment.\n✓ Sensitive data is never displayed.",
                color = Ink,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(15.dp)
            )
        }
        Button(
            onClick = unlock,
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(58.dp)
        ) { Text("Unlock full report  •  $4.99", fontWeight = FontWeight.Bold) }
        TextButton(
            onClick = back,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Edit my details", color = Muted) }
    }
}

@Composable
private fun Finding(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(Canvas, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Ink, modifier = Modifier.size(19.dp)) }; Text(
        text,
        color = Ink,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 12.dp)
    )
    }
}

@Composable
private fun ReportScreen(profile: SearchProfile, report: FootprintReport?, close: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        BrandHeader(); Text(
        "Your public report",
        color = Ink,
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 26.dp)
    ); Text("Generated from public sources • today", color = Muted, fontSize = 13.sp)
        ReportCard(
            "Identity",
            Icons.Outlined.Badge,
            listOf(
                report?.fullName ?: "${profile.firstName} ${profile.lastName}",
                "Born ${profile.birthYear}"
            )
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TravelExplore, null, tint = Ink)
                    Text(
                        "What we found",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Text(
                    report?.content ?: "No report is available. Run a scan to generate one.",
                    color = Ink,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        Surface(
            color = Ink,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Take control",
                    color = Lime,
                    fontWeight = FontWeight.Bold
                ); Text(
                "Review profile visibility, remove stale pages, and request corrections directly from each source.",
                color = Color.White,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 8.dp)
            ); TextButton(onClick = close) { Text("Finish & delete report", color = Lime) }
            }
        }
        Text(
            "TraceLight does not verify identity, access private accounts, or reveal addresses, phone numbers, or other sensitive data.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ReportCard(title: String, icon: ImageVector, items: List<String>) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    null,
                    tint = Ink
                ); Text(
                title,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
            }; items.forEach {
            Text(
                "•  $it",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        }
    }
}

@Composable
private fun TraceLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            background = Canvas,
            surface = Color.White
        ), typography = Typography(), content = content
    )
}
