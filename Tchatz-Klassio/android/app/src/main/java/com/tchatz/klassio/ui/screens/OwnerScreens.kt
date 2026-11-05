package com.tchatz.klassio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tchatz.klassio.data.Classroom
import com.tchatz.klassio.ui.OwnerUiState
import com.tchatz.klassio.ui.components.ErrorNotice
import com.tchatz.klassio.ui.components.InfoRow
import com.tchatz.klassio.ui.components.KlassioCard
import com.tchatz.klassio.ui.components.OutlineCard
import com.tchatz.klassio.ui.components.SectionTitle
import com.tchatz.klassio.ui.components.StatusPill
import com.tchatz.klassio.ui.theme.KlassioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerAuthScreen(
    state: OwnerUiState,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onReset: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Espace responsable") },
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
            Text("Gérez vos classes, matières et liens de publication.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = tab, containerColor = KlassioColors.White) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Connexion") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Créer un compte") })
            }
            Spacer(Modifier.height(18.dp))
            if (tab == 1) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nom complet") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                ErrorNotice(state.error)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (tab == 0) onSignIn(email, password) else onSignUp(email, password, fullName)
                },
                enabled = email.contains("@") && password.length >= 6 && !state.signingIn,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.signingIn) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = KlassioColors.White)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (tab == 0) "Se connecter" else "Créer mon compte")
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { onReset(email) }, enabled = email.contains("@")) {
                Text("Mot de passe oublié ?")
            }
            Spacer(Modifier.height(20.dp))
            OutlineCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = KlassioColors.Blue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Seuls les responsables possèdent un compte. Les étudiants accèdent par code et les enseignants publient par lien.",
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
fun OwnerDashboardScreen(
    state: OwnerUiState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenClassroom: (String) -> Unit,
    onCreateClassroom: (
        institution: String,
        className: String,
        field: String,
        level: String,
        studyYear: String,
        academicYear: String,
    ) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Tableau de bord") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Se déconnecter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
        floatingActionButton = {
            Button(onClick = { showCreate = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nouvelle classe")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Bonjour", style = MaterialTheme.typography.bodySmall, color = KlassioColors.GrayMedium)
                    Text(state.email ?: "Responsable", style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (state.error != null) item { ErrorNotice(state.error) }
            if (state.info != null) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = KlassioColors.SuccessBg) {
                        Text(
                            state.info,
                            color = KlassioColors.Success,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            item {
                SectionTitle("Mes espaces de classe") {
                    TextButton(onClick = { /* actualisé automatiquement */ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (state.classrooms.isEmpty()) {
                item {
                    OutlineCard {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.School, contentDescription = null, tint = KlassioColors.Blue)
                            Spacer(Modifier.height(8.dp))
                            Text("Aucune classe", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Créez votre premier espace de classe pour générer un code étudiant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KlassioColors.GrayMedium,
                            )
                        }
                    }
                }
            }
            items(state.classrooms, key = { it.id }) { classroom ->
                ClassroomAdminCard(classroom = classroom, onClick = { onOpenClassroom(classroom.id) })
            }
        }
    }

    if (showCreate) {
        CreateClassroomDialog(
            creating = state.creating,
            onDismiss = { showCreate = false },
            onCreate = { institution, className, field, level, year, academic ->
                onCreateClassroom(institution, className, field, level, year, academic)
                showCreate = false
            },
        )
    }
}

@Composable
private fun ClassroomAdminCard(classroom: Classroom, onClick: () -> Unit) {
    KlassioCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(classroom.institutionName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${classroom.className} · ${classroom.level} ${classroom.studyYear} · ${classroom.academicYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                }
                Surface(shape = RoundedCornerShape(999.dp), color = KlassioColors.BlueBg) {
                    Text(
                        classroom.joinCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = KlassioColors.Navy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(classroom.fieldOfStudy, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CreateClassroomDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit,
) {
    var institution by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("Licence") }
    var studyYear by remember { mutableStateOf("2") }
    var academicYear by remember { mutableStateOf("2026-2027") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer un espace de classe") },
        text = {
            Column {
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("Établissement") },
                    placeholder = { Text("ESM Bénin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Nom de la classe") },
                    placeholder = { Text("SIL 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("Filière") },
                    placeholder = { Text("Systèmes Informatiques et Logiciels") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it },
                        label = { Text("Niveau") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = studyYear,
                        onValueChange = { studyYear = it },
                        label = { Text("Année") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = academicYear,
                    onValueChange = { academicYear = it },
                    label = { Text("Année académique") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(institution, className, field, level, studyYear, academicYear) },
                enabled = institution.length >= 2 && className.length >= 2 && field.length >= 2 &&
                    Regex("^[0-9]{4}-[0-9]{4}$").matches(academicYear) && !creating,
            ) { Text(if (creating) "Création…" else "Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSpaceScreen(
    state: OwnerUiState,
    onBack: () -> Unit,
    onCreateSubject: (String, String) -> Unit,
    onRotate: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onRegenerateCode: () -> Unit,
    onDeleteSubject: (String) -> Unit,
    onArchive: (Boolean) -> Unit,
    onShare: (String) -> Unit,
) {
    val space = state.space ?: return
    var showCreate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text(space.classroom.className) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
        floatingActionButton = {
            Button(onClick = { showCreate = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Matière")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KlassioCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(space.classroom.institutionName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${space.classroom.level} ${space.classroom.studyYear} · ${space.classroom.fieldOfStudy} · ${space.classroom.academicYear}",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Code étudiant", style = MaterialTheme.typography.bodySmall, color = KlassioColors.GrayMedium)
                                Text(
                                    space.classroom.joinCode,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = KlassioColors.Navy,
                                )
                            }
                            IconButton(onClick = { copyToClipboard(context, space.classroom.joinCode) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copier le code")
                            }
                            TextButton(onClick = onRegenerateCode) {
                                Text("Renouveler")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("${space.stats.subjects} matières", success = true)
                            StatusPill("${space.stats.documents} documents", success = true)
                            StatusPill("${space.stats.devices} appareils", success = true)
                        }
                    }
                }
            }
            if (state.error != null) item { ErrorNotice(state.error) }
            if (state.lastCreatedLink != null) {
                item {
                    KlassioCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("Lien de publication créé", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ce lien n'est affiché qu'une seule fois. Copiez-le maintenant pour l'envoyer à l'enseignant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KlassioColors.Warning,
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = KlassioColors.GrayLight) {
                                Text(
                                    state.lastCreatedLink,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copyToClipboard(context, state.lastCreatedLink) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copier")
                                }
                                Button(onClick = { onShare(state.lastCreatedLink) }) {
                                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Partager")
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Matières") }
            if (space.subjects.isEmpty()) {
                item {
                    OutlineCard {
                        Column(Modifier.padding(20.dp)) {
                            Text("Aucune matière", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Créez une matière : un lien de publication privé sera généré automatiquement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KlassioColors.GrayMedium,
                            )
                        }
                    }
                }
            }
            items(space.subjects, key = { it.id }) { subject ->
                KlassioCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${subject.documentCount} document(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KlassioColors.GrayMedium,
                                )
                            }
                            if (subject.link != null) {
                                StatusPill("Lien actif", success = true)
                            } else {
                                StatusPill("Aucun lien", success = false)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onRotate(subject.id) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nouveau lien")
                            }
                            OutlinedButton(onClick = { onRevoke(subject.id) }) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Révoquer")
                            }
                            IconButton(onClick = { onDeleteSubject(subject.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = KlassioColors.Danger)
                            }
                        }
                    }
                }
            }
            item {
                OutlineCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Gestion de l'espace", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "La fermeture bloque l'accès étudiant et la publication, sans supprimer les documents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { onArchive(!space.classroom.isArchived) }) {
                            Text(if (space.classroom.isArchived) "Réactiver la classe" else "Fermer la classe")
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSubjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, description ->
                onCreateSubject(name, description)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CreateSubjectDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle matière") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la matière") },
                    placeholder = { Text("Programmation orientée objet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (facultative)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description) },
                enabled = name.trim().length >= 2,
            ) { Text("Créer la matière") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Tchatz Klassio", text))
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Partager le lien"))
}
