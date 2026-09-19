package com.haiklabs.tracelight

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haiklabs.tracelight.repo.*
import com.haiklabs.tracelight.ui.FootprintViewModel
import com.haiklabs.tracelight.ui.Screen

private val Blue = Color(0xFF2159DE)
private val Background = Color(0xFFF8F9FB)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF6F788A)
private val Line = Color(0xFFE7E9EE)
private val Green = Color(0xFF00B957)
private val SoftBlue = Color(0xFFEDF2FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PublicLensTheme { PublicLensApp() } }
    }
}

@Composable
private fun PublicLensApp(viewModel: FootprintViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = Background, bottomBar = { BottomNav() }) { insets ->
        AnimatedContent(state.screen, modifier = Modifier.padding(insets), label = "public-lens-screen") { screen ->
            when (screen) {
                Screen.Search -> SearchScreen(state.profile, state.error, viewModel::updateProfile, viewModel::startCandidateSearch)
                Screen.CandidateProgress -> ProgressScreen(false)
                Screen.Candidates -> CandidatesScreen(state.profile, state.searchResponse, viewModel::selectCandidate, viewModel::editSearch)
                Screen.NeedsMoreInfo -> NeedsMoreInfoScreen(state.searchResponse, viewModel::retryWithMoreInfo)
                Screen.Preview -> PreviewScreen(state.selectedCandidate, viewModel::openPayment, viewModel::editSearch)
                Screen.Payment -> PaymentScreen(state.selectedCandidate, state.error, viewModel::backToPreview, viewModel::confirmPurchaseAndGenerate)
                Screen.ReportProgress -> ProgressScreen(true)
                Screen.Report -> ReportScreen(state.report, viewModel::backToPreview, viewModel::deleteReport)
            }
        }
    }
}

@Composable
private fun SearchScreen(profile: SearchProfile, error: String?, update: (SearchProfile) -> Unit, search: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Page {
        BrandHeader()
        HeroTitle("Find the right person", "Enter the details you know. A few strong clues help us distinguish people with similar names.")
        FieldLabel("FULL NAME", true)
        LensField(profile.fullName, { update(profile.copy(fullName = it)) }, "Anna Williams")
        FieldLabel("COUNTRY OR REGION", true)
        LensField(profile.countryOrRegion, { update(profile.copy(countryOrRegion = it)) }, "United Kingdom")
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add at least one strong clue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Badge("Required", Blue, Color.White, Modifier.padding(start = 10.dp))
        }
        Text("This greatly improves the chance of finding the correct person.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        ClueField("Public social profile URL", "LinkedIn, Instagram, Facebook, GitHub or another public profile", profile.publicProfileUrl, { update(profile.copy(publicProfileUrl = it)) }, true)
        ClueField("Known username", "e.g. @handle, username across platforms", profile.username, { update(profile.copy(username = it)) }, true)
        ClueField("Company or profession", "Current employer or field of work", profile.companyOrProfession, { update(profile.copy(companyOrProfession = it)) })
        ClueField("Current city", "General area, not exact address", profile.currentCity, { update(profile.copy(currentCity = it)) })
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 12.dp)) {
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            Text(if (expanded) "Hide additional details" else "Add additional details", fontSize = 16.sp)
        }
        if (expanded) {
            LensField(profile.ageRange, { update(profile.copy(ageRange = it)) }, "Approximate age range")
            LensField(profile.previousCity, { update(profile.copy(previousCity = it)) }, "Previous city")
            LensField(profile.school, { update(profile.copy(school = it)) }, "School or university")
            LensField(profile.previousCompany, { update(profile.copy(previousCompany = it)) }, "Previous company")
            LensField(profile.personalWebsite, { update(profile.copy(personalWebsite = it)) }, "Personal website")
            LensField(profile.knownProject, { update(profile.copy(knownProject = it)) }, "Known project, publication or achievement")
            LensField(profile.additionalContext, { update(profile.copy(additionalContext = it)) }, "Additional context")
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        PrimaryButton("Find possible matches", search)
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Shield, null, tint = Muted, modifier = Modifier.size(18.dp))
            Text("  We search public sources and never contact the person.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProgressScreen(fullReport: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandHeader(); Spacer(Modifier.weight(.65f))
        Box(Modifier.size(126.dp).background(SoftBlue, CircleShape), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Blue, strokeWidth = 4.dp, modifier = Modifier.size(82.dp))
            Icon(if (fullReport) Icons.Outlined.FactCheck else Icons.Outlined.PersonSearch, null, tint = Blue, modifier = Modifier.size(42.dp))
        }
        Text(if (fullReport) "Creating the complete report" else "Finding possible matches", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
        Text(if (fullReport) "We're collecting and cross-checking available public information." else "We're comparing your clues with public information.", color = Muted, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Column(Modifier.padding(top = 35.dp)) {
            val steps = if (fullReport) listOf("Confirming identity", "Finding relevant sources", "Matching social accounts", "Cross-checking facts", "Organizing the report") else listOf("Searching public sources", "Comparing identity clues", "Organizing possible matches")
            steps.forEachIndexed { index, text -> ProgressStep(text, index < if (fullReport) 2 else 1, index == if (fullReport) 2 else 1) }
        }
        Spacer(Modifier.weight(1f)); Text(if (fullReport) "You can leave and come back later" else "This typically takes 5–15 seconds", color = Color(0xFF9AA4B7), modifier = Modifier.padding(bottom = 28.dp))
    }
}

@Composable
private fun CandidatesScreen(profile: SearchProfile, result: CandidateSearchResponse?, select: (PersonCandidate) -> Unit, edit: () -> Unit) {
    Page {
        BrandHeader(); HeroTitle("Which person are you looking for?", "Review these public details carefully before continuing.")
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = Muted); Text("  ${profile.fullName} · ${profile.countryOrRegion}", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); TextButton(onClick = edit) { Text("Edit") }
            }
        }
        Spacer(Modifier.height(14.dp))
        result?.candidates.orEmpty().forEach { candidate -> CandidateCard(candidate) { select(candidate) } }
        OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) { Text("None of these — edit my search", color = Muted) }
    }
}

@Composable
private fun NeedsMoreInfoScreen(result: CandidateSearchResponse?, edit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandHeader(); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ManageSearch, null, tint = Blue, modifier = Modifier.size(68.dp))
        Text(if (result?.status == SearchStatus.NO_RELIABLE_MATCH) "No reliable match yet" else "We need one more clue", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 24.dp))
        Text(result?.message ?: "Add another detail to narrow the search.", color = Muted, lineHeight = 24.sp, modifier = Modifier.padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        result?.requestedClues.orEmpty().forEach { Badge(it.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), SoftBlue, Blue, Modifier.padding(top = 9.dp)) }
        Spacer(Modifier.height(28.dp)); PrimaryButton("Add more information", edit); Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PreviewScreen(candidate: PersonCandidate?, unlock: () -> Unit, edit: () -> Unit) {
    if (candidate == null) return
    Page {
        BrandHeader(); PersonHeader(candidate)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(candidate.publicProfiles.size.coerceAtLeast(1).toString(), "profiles", Modifier.weight(1f)); StatCard(candidate.matchingClues.size.coerceAtLeast(1).toString(), "matching clues", Modifier.weight(1f)); StatCard("✓", "public data", Modifier.weight(1f))
        }
        SectionLabel("FREE PREVIEW")
        Text("Public facts we found", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
        FactCard("CURRENT ROLE", listOf(candidate.role, candidate.company).filter(String::isNotBlank).joinToString(" at ").ifBlank { "Public role not confirmed" }, candidate.publicProfiles.firstOrNull()?.url.orEmpty())
        if (candidate.location.isNotBlank()) FactCard("GENERAL LOCATION", candidate.location, "")
        Text("Strongest social profiles", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        candidate.publicProfiles.take(3).forEach { ProfilePreview(it) }
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                SectionLabel("COMPLETE REPORT INCLUDES", top = 0)
                listOf("Complete public summary with citations", "All discovered public social accounts", "Professional timeline and footprint", "Public business contact information", "Articles and notable mentions", "Conflicting or uncertain information", "Complete cited source list").forEach { CheckLine(it) }
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Column(Modifier.padding(18.dp)) { PrimaryButton("Get complete report  ·  $2.99", unlock); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("One-time purchase · No subscription", color = Muted, fontSize = 12.sp); TextButton(onClick = edit) { Text("Choose another") } } }
        }
    }
}

@Composable
private fun PaymentScreen(candidate: PersonCandidate?, error: String?, back: () -> Unit, buy: () -> Unit) {
    if (candidate == null) return
    Page {
        BrandHeader(); TextButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null); Text(" Back") }
        Text("Unlock the complete report", fontFamily = FontFamily.Serif, fontSize = 31.sp, color = Ink, modifier = Modifier.padding(vertical = 18.dp))
        CandidateCard(candidate, null)
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Column(Modifier.padding(22.dp)) { SectionLabel("WHAT'S INCLUDED", 0); listOf("Complete public summary", "All discovered public social accounts", "Professional footprint and timeline", "Public business contact information", "Articles and notable mentions", "Conflicting or uncertain information", "Complete cited source list", "Saved access to this report").forEach { CheckLine(it) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MiniPromise("No subscription", Modifier.weight(1f)); MiniPromise("No auto-renewal", Modifier.weight(1f)) }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MiniPromise("One report only", Modifier.weight(1f)); MiniPromise("Secure checkout", Modifier.weight(1f)) }
        Text("If report generation is interrupted, you can continue using the same purchase.", color = Color(0xFF9AA4B7), modifier = Modifier.padding(22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        PrimaryButton("Buy with Google Play · $2.99", buy)
        Text("Test checkout: connect Play Billing before production", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
}

@Composable
private fun ReportScreen(report: DeepSearchReport?, back: () -> Unit, delete: () -> Unit) {
    if (report == null) return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Page {
        BrandHeader(); TextButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null); Text(" Back") }
        PersonHeader(report.person)
        Row(verticalAlignment = Alignment.CenterVertically) { MatchBadge(report.person.matchLevel); Text(report.generatedDate, color = Color(0xFF98A2B3), modifier = Modifier.padding(start = 12.dp)); Spacer(Modifier.weight(1f)); TextButton(onClick = delete) { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFFF5252)); Text("Delete", color = Color(0xFFFF5252)) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard(report.sources.size.toString(), "sources", Modifier.weight(1f)); StatCard(report.socialProfiles.size.toString(), "profiles", Modifier.weight(1f)); StatCard(report.verifiedFactCount.toString(), "facts", Modifier.weight(1f)) }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = Background, divider = {}) {
            listOf("Summary", "Identity", "Social", "Professional", "Articles").forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        when (tab) {
            0 -> SummarySection(report)
            1 -> IdentitySection(report)
            2 -> SocialSection(report)
            3 -> ProfessionalSection(report)
            else -> ArticlesSection(report)
        }
        if (tab == 0) { IdentitySection(report); SocialSection(report); ProfessionalSection(report); ArticlesSection(report) }
        SourceSection(report.sources)
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp).height(58.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(16.dp)) { Text("Report incorrect information", color = Color(0xFFFF5252)) }
    }
}

@Composable private fun SummarySection(report: DeepSearchReport) { SectionLabel("PUBLIC SUMMARY"); CardSurface { Text(report.summary, color = Ink, lineHeight = 25.sp) } }
@Composable private fun IdentitySection(report: DeepSearchReport) { SectionLabel("IDENTITY EVIDENCE"); CardSurface { report.identityEvidence.forEach { EvidenceRow(it) } } }
@Composable private fun SocialSection(report: DeepSearchReport) { SectionLabel("PUBLIC SOCIAL ACCOUNTS"); report.socialProfiles.forEach { social -> CardSurface(Modifier.padding(bottom = 12.dp)) { Row { PlatformMark(social.platform, social.url); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(social.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(social.username, color = Color(0xFF9AA4B7)); Text(social.bio, color = Color(0xFF475467), modifier = Modifier.padding(top = 8.dp)); if (social.audience.isNotBlank()) Text(social.audience, color = Color(0xFF9AA4B7), fontSize = 13.sp); OpenLink(social.url) } ; MatchBadge(social.matchLevel) } } }; val notFound = report.platformSweep.filter { it.status == SweepStatus.NOT_FOUND }.map { it.platform }; if (notFound.isNotEmpty()) Text("Checked with no public profile found: ${notFound.joinToString()}", color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 6.dp)); val unknown = report.platformSweep.filter { it.status == SweepStatus.UNKNOWN }.map { it.platform }; if (unknown.isNotEmpty()) Text("Could not be checked: ${unknown.joinToString()}", color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 14.dp)) }
@Composable private fun ProfessionalSection(report: DeepSearchReport) { SectionLabel("PROFESSIONAL FOOTPRINT"); report.professionalTimeline.forEach { item -> Row { Box(Modifier.padding(top = 12.dp).size(14.dp).background(Blue, CircleShape)); CardSurface(Modifier.padding(start = 14.dp, bottom = 12.dp).weight(1f)) { Text("${item.start} — ${item.end.ifBlank { "present" }}", color = Color(0xFF98A2B3)); Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(listOf(item.organization, item.location).filter(String::isNotBlank).joinToString(" · "), color = Muted); Text("Sources: ${item.sourceIds.joinToString()}", color = Color(0xFF98A2B3), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } } }; LinkedInNote(report); if (report.businessContacts.isNotEmpty()) { SectionLabel("PUBLIC BUSINESS CONTACT"); CardSurface { report.businessContacts.forEach { OpenLink(it.url, "${it.label}: ${it.value}") } } } }
@Composable private fun ArticlesSection(report: DeepSearchReport) { SectionLabel("ARTICLES & NOTABLE MENTIONS"); report.articlesAndMentions.forEach { article -> CardSurface(Modifier.padding(bottom = 12.dp)) { Text(article.title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(listOf(article.publisher, article.publishedDate).filter(String::isNotBlank).joinToString(" · "), color = Color(0xFF98A2B3), modifier = Modifier.padding(vertical = 8.dp)); Text(article.summary, color = Color(0xFF475467), lineHeight = 22.sp); OpenLink(article.url) } }; if (report.uncertainties.isNotEmpty()) { SectionLabel("UNCERTAIN INFORMATION"); Surface(color = Color(0xFFFFF8E7), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE09A)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { report.uncertainties.forEach { Text("⚠  $it", color = Color(0xFFB54708), lineHeight = 22.sp, modifier = Modifier.padding(vertical = 6.dp)) } } } } }
@Composable private fun SourceSection(sources: List<ReportSource>) { SectionLabel("COMPLETE SOURCE LIST"); sources.forEach { source -> CardSurface(Modifier.padding(bottom = 12.dp)) { Row { Column(Modifier.weight(1f)) { Text(source.title, fontWeight = FontWeight.Bold, fontSize = 17.sp); OpenLink(source.url, source.publisher); Text("Retrieved ${source.retrievedDate}", color = Color(0xFF98A2B3)); Text("Supports: ${source.supports.joinToString()}", color = Muted, modifier = Modifier.padding(top = 7.dp)) }; Text(if (source.corroboration == Corroboration.MULTIPLE_SOURCES) "Multiple sources" else "Single source", color = if (source.corroboration == Corroboration.MULTIPLE_SOURCES) Blue else Muted, fontSize = 12.sp) } } } }

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), content = content) }
@Composable private fun BrandHeader() { Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).background(Blue, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, tint = Color.White) }; Text("Public Lens", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = Ink) } }
@Composable private fun HeroTitle(title: String, subtitle: String) { Text(title, fontFamily = FontFamily.Serif, fontSize = 31.sp, color = Ink, modifier = Modifier.padding(top = 20.dp)); Text(subtitle, color = Muted, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) }
@Composable private fun FieldLabel(text: String, required: Boolean = false) { Text(text + if (required) " *" else "", color = if (required) Muted else Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp)) }
@Composable private fun LensField(value: String, change: (String) -> Unit, placeholder: String) { OutlinedTextField(value, change, placeholder = { Text(placeholder, color = Color(0xFFA0A8B8)) }, singleLine = true, shape = RoundedCornerShape(15.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, focusedBorderColor = Blue, unfocusedBorderColor = Line), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 58.dp)) }
@Composable private fun ClueField(title: String, hint: String, value: String, change: (String) -> Unit, strongest: Boolean = false) { Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (value.isNotBlank()) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, tint = if (value.isNotBlank()) Blue else Color(0xFFCBD1DB)); Text(title, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp)); if (strongest) Badge("Strongest", Color(0xFFEAFBF7), Color(0xFF009B83), Modifier.padding(start = 8.dp)) }; Text(hint, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 36.dp, top = 4.dp)); LensField(value, change, "Add clue") } } }
@Composable private fun PrimaryButton(text: String, click: () -> Unit) { Button(onClick = click, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(58.dp)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) } }
@Composable private fun CandidateCard(candidate: PersonCandidate, click: (() -> Unit)?) { Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).then(if (click != null) Modifier.clickable(onClick = click) else Modifier)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) { Avatar(candidate.name); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(candidate.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(listOf(candidate.role, candidate.company).filter(String::isNotBlank).joinToString(" at "), color = Muted); Text(candidate.location, color = Color(0xFF98A2B3)); Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { candidate.publicProfiles.take(4).forEach { PlatformMark(it.platform, it.url) } }; if (candidate.matchingClues.isNotEmpty()) Text("Matched: ${candidate.matchingClues.joinToString()}", color = Color(0xFF98A2B3), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }; MatchBadge(candidate.publicProfiles.minByOrNull { it.matchLevel.ordinal }?.matchLevel ?: MatchLevel.UNCONFIRMED) } } }
@Composable private fun PersonHeader(candidate: PersonCandidate) { Row(Modifier.fillMaxWidth().background(SoftBlue).padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(candidate.name, 76); Column(Modifier.padding(start = 18.dp)) { Text(candidate.name, fontWeight = FontWeight.Bold, fontSize = 25.sp); Text(listOf(candidate.role, candidate.company).filter(String::isNotBlank).joinToString(" at "), color = Muted, fontSize = 17.sp); Text(candidate.location, color = Color(0xFF98A2B3)); Spacer(Modifier.height(8.dp)); MatchBadge(MatchLevel.STRONG) } } }
@Composable private fun PersonHeader(person: ReportPerson) { Row(Modifier.fillMaxWidth().background(SoftBlue).padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(person.name, 76); Column(Modifier.padding(start = 18.dp)) { Text(person.name, fontWeight = FontWeight.Bold, fontSize = 25.sp); Text(listOf(person.role, person.company).filter(String::isNotBlank).joinToString(" at "), color = Muted, fontSize = 17.sp); Text(person.generalLocation, color = Color(0xFF98A2B3)) } } }
@Composable private fun Avatar(name: String, size: Int = 58) { Box(Modifier.size(size.dp).background(Color(0xFF3064C4), CircleShape), contentAlignment = Alignment.Center) { Text(name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), color = Color.White, fontSize = (size / 2.6).sp, fontWeight = FontWeight.Medium) } }
@Composable private fun MatchBadge(level: MatchLevel) { val (label, colors) = when (level) { MatchLevel.OFFICIAL -> "Official" to (SoftBlue to Blue); MatchLevel.STRONG -> "Strong match" to (Color(0xFFECFDF3) to Color(0xFF008A3C)); MatchLevel.PARTIAL -> "Partial match" to (Color(0xFFFFF8E7) to Color(0xFFC65A00)); MatchLevel.UNCONFIRMED -> "Needs more info" to (Color(0xFFF1F2F4) to Muted) }; Badge(label, colors.first, colors.second) }
@Composable private fun Badge(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) { Surface(color = background, shape = RoundedCornerShape(50), modifier = modifier) { Text(text, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) } }
@Composable private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) { Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = modifier.height(95.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) { Text(value, color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 12.sp) } } }
@Composable private fun FactCard(label: String, fact: String, url: String) { CardSurface(Modifier.padding(bottom = 10.dp)) { Text(label, color = Muted, fontSize = 12.sp, letterSpacing = 1.sp); Text(fact, color = Ink, fontSize = 17.sp, modifier = Modifier.padding(top = 5.dp)); if (url.isNotBlank()) OpenLink(url) } }
@Composable private fun ProfilePreview(profile: PublicProfileMatch) { CardSurface(Modifier.padding(bottom = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { PlatformMark(profile.platform, profile.url); Text(profile.platform, modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Medium); MatchBadge(profile.matchLevel) }; OpenLink(profile.url) } }
/** Site icon loaded from the profile's domain, with the two-letter mark as placeholder and fallback. */
@Composable private fun PlatformMark(platform: String, url: String = "") {
    val domain = platformDomain(platform, url)
    val shape = RoundedCornerShape(7.dp)
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(if (platform.contains("instagram", true)) Color(0xFFF04F86) else Blue), contentAlignment = Alignment.Center) {
            Text(platform.take(2), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
    Box(Modifier.size(34.dp).clip(shape).background(Color.White).border(1.dp, Line, shape), contentAlignment = Alignment.Center) {
        if (domain == null) fallback() else SubcomposeAsyncImage(
            model = "https://www.google.com/s2/favicons?domain=$domain&sz=128",
            contentDescription = platform,
            modifier = Modifier.size(22.dp),
            loading = { fallback() },
            error = { fallback() }
        )
    }
}

private val platformDomains = mapOf(
    "linkedin" to "linkedin.com", "x" to "x.com", "twitter" to "x.com", "instagram" to "instagram.com",
    "facebook" to "facebook.com", "github" to "github.com", "youtube" to "youtube.com", "tiktok" to "tiktok.com",
    "threads" to "threads.net", "bluesky" to "bsky.app", "wikipedia" to "wikipedia.org", "medium" to "medium.com",
    "substack" to "substack.com", "reddit" to "reddit.com", "pinterest" to "pinterest.com", "snapchat" to "snapchat.com"
)

/** Domain to fetch a favicon for: the profile URL's host when present, otherwise a well-known network. */
private fun platformDomain(platform: String, url: String): String? {
    val host = url.trim().takeIf { it.contains("://") }?.substringAfter("://")?.substringBefore('/')?.removePrefix("www.")?.lowercase()
    if (!host.isNullOrBlank() && host.contains('.')) return host
    val key = platform.trim().lowercase()
    return platformDomains.entries.firstOrNull { (name, _) -> key == name || key.startsWith(name) }?.value
}
@Composable private fun CheckLine(text: String) { Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF00A58E), modifier = Modifier.size(18.dp)); Text(text, color = Color(0xFF344054), modifier = Modifier.padding(start = 10.dp)) } }
@Composable private fun MiniPromise(text: String, modifier: Modifier) { Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = modifier.height(62.dp)) { Box(contentAlignment = Alignment.Center) { Text(text, color = Color(0xFF475467), fontSize = 13.sp) } } }
@Composable private fun ProgressStep(text: String, done: Boolean, active: Boolean) { Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(36.dp).background(if (done) Green else if (active) Blue else Color(0xFFF0F1F3), CircleShape), contentAlignment = Alignment.Center) { if (done) Icon(Icons.Outlined.Check, null, tint = Color.White) else Text(if (active) "•••" else "•", color = if (active) Color.White else Color(0xFFCDD2DA), fontWeight = FontWeight.Bold) }; Text(text, color = if (done || active) Ink else Color(0xFFA0A8B8), fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp)) } }
@Composable private fun EvidenceRow(item: EvidenceItem) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Box(Modifier.padding(top = 7.dp).size(9.dp).background(if (item.matchLevel == MatchLevel.PARTIAL) Color(0xFFFFB000) else Green, CircleShape)); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(item.label, fontWeight = FontWeight.Medium); Text(item.detail, color = Muted, fontSize = 13.sp) }; MatchBadge(item.matchLevel) } }
@Composable private fun CardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().then(modifier)) { Column(Modifier.padding(18.dp), content = content) } }
@Composable private fun SectionLabel(text: String, top: Int = 28) { Text(text, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 15.sp, modifier = Modifier.padding(top = top.dp, bottom = 12.dp)) }
/** LinkedIn shows full work history only to signed-in members, so say so instead of looking incomplete. */
@Composable private fun LinkedInNote(report: DeepSearchReport) {
    val linkedIn = report.socialProfiles.firstOrNull { it.url.contains("linkedin.com", ignoreCase = true) }
        ?: report.platformSweep.firstOrNull { it.status == SweepStatus.FOUND && it.url.contains("linkedin.com", ignoreCase = true) }?.let { SocialProfile(platform = it.platform, url = it.url) }
        ?: return
    Surface(color = SoftBlue, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (report.professionalTimeline.isEmpty()) "Work history for this person lives on LinkedIn, where it is visible only to signed-in members."
                else "This is the publicly indexed part. The full work history with dates is on LinkedIn and visible only to signed-in members.",
                color = Color(0xFF344054), fontSize = 13.sp, lineHeight = 19.sp
            )
            OpenLink(linkedIn.url, "Open LinkedIn profile ↗")
        }
    }
}

@Composable private fun OpenLink(url: String, label: String = "Open profile ↗") { if (url.isBlank()) return; val context = LocalContext.current; Text(label, color = Blue, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) }
@Composable private fun BottomNav() { NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) { listOf(Icons.Outlined.Search to "Search", Icons.Outlined.Article to "Reports", Icons.Outlined.Settings to "Settings").forEachIndexed { index, item -> NavigationBarItem(selected = index == 0, onClick = {}, icon = { Icon(item.first, null) }, label = { Text(item.second) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = Color.Transparent, unselectedIconColor = Color(0xFF98A2B3), unselectedTextColor = Color(0xFF98A2B3))) } } }
@Composable private fun PublicLensTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Background, surface = Color.White), typography = Typography(), content = content) }
