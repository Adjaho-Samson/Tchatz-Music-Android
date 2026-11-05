package com.tchatz.klassio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tchatz.klassio.data.SessionState
import com.tchatz.klassio.ui.StudentUiState
import com.tchatz.klassio.ui.components.EmptyState
import com.tchatz.klassio.ui.components.ErrorNotice
import com.tchatz.klassio.ui.components.KlassioCard
import com.tchatz.klassio.ui.components.OutlineCard
import com.tchatz.klassio.ui.theme.KlassioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    student: StudentUiState,
    onJoinClick: () -> Unit,
    onOpenSession: (SessionState) -> Unit,
    onOwner: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Tchatz Klassio", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Filled.Download, contentDescription = "Téléchargements")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(onJoinClick = onJoinClick) }
            item {
                OutlinedButton(
                    onClick = onOwner,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Espace responsable")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            item {
                Text(
                    "Mes classes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (student.sessions.isEmpty()) {
                item {
                    OutlineCard {
                        EmptyState(
                            icon = Icons.Filled.School,
                            title = "Aucune classe rejointe",
                            body = "Saisissez le code de classe fourni par le responsable. Aucun compte n'est nécessaire.",
                        )
                    }
                }
            }
            items(student.sessions, key = { it.classroomId }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onOpenSession(session) },
                    onRemove = { onRemove(session.classroomId) },
                )
            }
            item {
                Text(
                    "Un responsable crée l'espace. Un étudiant le rejoint avec un code. " +
                        "Un enseignant publie avec un lien. Tout le monde retrouve les documents au même endroit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KlassioColors.GrayMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(onJoinClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = KlassioColors.Navy),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(KlassioColors.Navy, KlassioColors.Blue)),
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = KlassioColors.White.copy(alpha = 0.14f),
                ) {
                    Text(
                        "Une solution de Tchatz",
                        color = KlassioColors.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Tous les documents de ta classe,\norganisés au même endroit.",
                    color = KlassioColors.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cours, TD, exercices : retrouvez-les par matière, sans groupes WhatsApp.",
                    color = KlassioColors.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onJoinClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KlassioColors.White,
                        contentColor = KlassioColors.Blue,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rejoindre une classe", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionState, onClick: () -> Unit, onRemove: () -> Unit) {
    KlassioCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(KlassioColors.BlueBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = KlassioColors.Blue)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.institutionName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(session.level, session.studyYear, session.fieldOfStudy)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${session.className} · ${session.academicYear}",
                    style = MaterialTheme.typography.labelMedium,
                    color = KlassioColors.Navy,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Code ${session.joinCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Retirer",
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.Danger,
                        modifier = Modifier.clickable(onClick = onRemove),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    joining: Boolean,
    error: String?,
    onBack: () -> Unit,
    onJoin: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Rejoindre une classe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Code de classe", style = MaterialTheme.typography.titleMedium)
            Text(
                "Le responsable de classe vous communique un code à 8 caractères.",
                style = MaterialTheme.typography.bodySmall,
                color = KlassioColors.GrayMedium,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.uppercase().filter { it.isLetterOrDigit() }.take(8) },
                label = { Text("Ex. NQKHWWWL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                ErrorNotice(error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onJoin(code) },
                enabled = code.length >= 6 && !joining,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (joining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = KlassioColors.White,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (joining) "Connexion…" else "Accéder à la classe")
            }
            Spacer(Modifier.height(24.dp))
            OutlineCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Aucun compte requis", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tchatz Klassio ne demande ni email, ni numéro de téléphone, ni mot de passe à l'étudiant. " +
                            "La classe reste enregistrée sur cet appareil. En cas de réinstallation, ressaisissez simplement le code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDownloads: () -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            KlassioCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = KlassioColors.Blue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Recevoir une alerte lorsqu'un nouveau document est publié.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                }
            }
            Spacer(Modifier.height(12.dp))
            KlassioCard(onClick = onDownloads) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = KlassioColors.Blue)
                    Spacer(Modifier.width(12.dp))
                    Text("Gestion des téléchargements", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            KlassioCard(onClick = { onOpenWeb("/legal") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = KlassioColors.Blue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Confidentialité et conditions", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Consulter la politique de confidentialité et les conditions d'utilisation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            KlassioCard(onClick = { onOpenWeb("/") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = KlassioColors.Blue)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Version Web", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Retrouvez vos classes depuis un navigateur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Tchatz Klassio · Version 1.0.0\nUne solution de Tchatz — Samson Adjaho",
                style = MaterialTheme.typography.bodySmall,
                color = KlassioColors.GrayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
