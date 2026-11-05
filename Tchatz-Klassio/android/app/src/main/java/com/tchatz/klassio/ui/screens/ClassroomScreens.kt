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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tchatz.klassio.data.local.CachedDocument
import com.tchatz.klassio.data.local.CachedSubject
import com.tchatz.klassio.ui.StudentUiState
import com.tchatz.klassio.ui.components.EmptyState
import com.tchatz.klassio.ui.components.FileBadge
import com.tchatz.klassio.ui.components.KlassioCard
import com.tchatz.klassio.ui.components.OfflineNotice
import com.tchatz.klassio.ui.components.OutlineCard
import com.tchatz.klassio.ui.theme.KlassioColors
import com.tchatz.klassio.data.repo.KlassioRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomScreen(
    state: StudentUiState,
    onBack: () -> Unit,
    onOpenSubject: (CachedSubject) -> Unit,
    onOpenLibrary: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val session = state.active ?: return
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Espace de classe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                        }
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
            item {
                Column {
                    Text(session.institutionName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOf(session.level + " " + session.studyYear, session.fieldOfStudy, session.academicYear)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(999.dp), color = KlassioColors.BlueBg) {
                        Text(
                            "Code de classe : ${session.joinCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = KlassioColors.Navy,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (state.offline) {
                item { OfflineNotice("Hors connexion : seuls les documents déjà téléchargés sont accessibles.") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Matières", state.subjects.size.toString(), Modifier.weight(1f))
                    StatCard("Documents", state.documents.size.toString(), Modifier.weight(1f))
                    StatCard("Téléchargés", state.downloads.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                TextButton(onClick = { onOpenLibrary(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Voir toute la bibliothèque")
                }
            }
            item { Text("Matières", style = MaterialTheme.typography.titleMedium) }
            if (state.loading && state.subjects.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!state.loading && state.subjects.isEmpty()) {
                item {
                    OutlineCard {
                        EmptyState(
                            icon = Icons.Filled.Description,
                            title = "Aucune matière pour le moment",
                            body = "Le responsable de classe n'a pas encore créé de matière.",
                        )
                    }
                }
            }
            items(state.subjects, key = { it.id }) { subject ->
                SubjectCard(subject = subject, onClick = { onOpenSubject(subject) })
            }
            item {
                OutlineCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("Informations", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Les nouveaux documents sont accessibles dès qu'une connexion est disponible. " +
                                "Les fichiers téléchargés restent lisibles hors connexion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlassioColors.GrayMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = KlassioColors.White,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = KlassioColors.Blue)
            Text(label, style = MaterialTheme.typography.bodySmall, color = KlassioColors.GrayMedium)
        }
    }
}

@Composable
private fun SubjectCard(subject: CachedSubject, onClick: () -> Unit) {
    KlassioCard(onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(KlassioColors.BlueBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = KlassioColors.Blue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                if (!subject.description.isNullOrBlank()) {
                    Text(
                        subject.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                        maxLines = 1,
                    )
                }
                Text(
                    "${subject.documentCount} document(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = KlassioColors.Blue,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: StudentUiState,
    initialSubject: String?,
    onBack: () -> Unit,
    onOpenDocument: (CachedDocument) -> Unit,
    onDownload: (CachedDocument) -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (String?) -> Unit,
) {
    val session = state.active ?: return
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Bibliothèque") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { onSearch(state.search) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rechercher")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Rechercher un document") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.subjectFilter == null,
                        onClick = { onFilter(null) },
                        label = { Text("Toutes (${session.className})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KlassioColors.BlueBg),
                    )
                }
                items(state.subjects, key = { it.id }) { subject ->
                    FilterChip(
                        selected = state.subjectFilter == subject.id,
                        onClick = { onFilter(subject.id) },
                        label = { Text(subject.name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KlassioColors.BlueBg),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.offline) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    OfflineNotice("Hors connexion : bibliothèque locale.")
                }
            }
            if (state.documents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.FolderOpen,
                    title = "Aucun document",
                    body = if (state.search.isNotBlank()) {
                        "Aucun résultat pour « ${state.search} »."
                    } else {
                        "Les documents publiés par vos enseignants apparaîtront ici."
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            downloaded = state.downloads.any { it.documentId == document.id },
                            progress = state.downloading[document.id],
                            onClick = { onOpenDocument(document) },
                            onDownload = { onDownload(document) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentRow(
    document: CachedDocument,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    KlassioCard(onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileBadge(type = document.fileType)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        document.subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.Blue,
                    )
                    Text(
                        listOf(
                            document.teacherDisplayName,
                            formatDate(document.createdAt),
                            KlassioRepository.formatSize(document.fileSize),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = KlassioColors.GrayMedium,
                    )
                }
                IconButton(onClick = onDownload) {
                    if (downloaded) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Téléchargé", tint = KlassioColors.Success)
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = "Télécharger", tint = KlassioColors.Blue)
                    }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = KlassioColors.Blue,
                )
                Text(
                    "Téléchargement… ${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = KlassioColors.GrayMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: CachedDocument,
    downloaded: Boolean,
    progress: Float?,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
) {
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Document") },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileBadge(type = document.fileType)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(document.title, style = MaterialTheme.typography.titleLarge)
                    Text(document.subjectName, style = MaterialTheme.typography.bodySmall, color = KlassioColors.Blue)
                }
            }
            Spacer(Modifier.height(16.dp))
            KlassioCard {
                Column(Modifier.padding(16.dp)) {
                    DetailLine("Enseignant", document.teacherDisplayName)
                    DetailLine("Publié le", formatDate(document.createdAt))
                    DetailLine("Fichier", document.fileName)
                    DetailLine("Format", document.fileType.uppercase())
                    DetailLine("Taille", KlassioRepository.formatSize(document.fileSize))
                    DetailLine("État", if (downloaded) "Disponible hors connexion" else "En ligne uniquement")
                }
            }
            if (!document.description.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                KlassioCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(document.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = KlassioColors.Blue,
                )
                Text(
                    "Téléchargement… ${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = KlassioColors.GrayMedium,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KlassioColors.Blue,
                    modifier = Modifier.weight(1f).height(50.dp).clickable(onClick = onOpen),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = KlassioColors.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (downloaded) "Ouvrir" else "Consulter",
                            color = KlassioColors.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KlassioColors.White,
                    modifier = Modifier.weight(1f).height(50.dp).clickable(onClick = onDownload),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = KlassioColors.Blue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Télécharger", color = KlassioColors.Blue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Les documents sont stockés dans un espace privé. La consultation se fait par un lien sécurisé temporaire.",
                style = MaterialTheme.typography.bodySmall,
                color = KlassioColors.GrayMedium,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = KlassioColors.GrayMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: StudentUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Scaffold(
        containerColor = KlassioColors.GrayLight,
        topBar = {
            TopAppBar(
                title = { Text("Téléchargements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KlassioColors.White),
            )
        },
    ) { padding ->
        if (state.downloads.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Filled.CloudDownload,
                    title = "Aucun téléchargement",
                    body = "Les documents téléchargés restent consultables sans connexion Internet.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.downloads, key = { it.documentId }) { file ->
                    KlassioCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileBadge(type = file.fileName.substringAfterLast('.', ""))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(
                                    "${file.subjectName} · ${KlassioRepository.formatSize(file.fileSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KlassioColors.GrayMedium,
                                )
                                Row {
                                    TextButton(onClick = { onOpen(file.documentId) }) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ouvrir")
                                    }
                                    TextButton(onClick = { onDelete(file.documentId) }) {
                                        Text("Supprimer", color = KlassioColors.Danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDate(iso: String): String = runCatching {
    val cleaned = iso.substringBefore('.').replace("Z", "").replace("T", " ")
    val parts = cleaned.split(" ")
    val date = parts[0].split("-")
    if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else iso
}.getOrDefault(iso)
