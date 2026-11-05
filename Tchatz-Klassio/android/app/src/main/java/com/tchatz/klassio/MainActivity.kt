package com.tchatz.klassio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tchatz.klassio.data.local.DownloadedFile
import com.tchatz.klassio.data.repo.KlassioRepository
import com.tchatz.klassio.ui.OwnerViewModel
import com.tchatz.klassio.ui.StudentViewModel
import com.tchatz.klassio.ui.screens.ClassroomScreen
import com.tchatz.klassio.ui.screens.DocumentDetailScreen
import com.tchatz.klassio.ui.screens.DownloadsScreen
import com.tchatz.klassio.ui.screens.HomeScreen
import com.tchatz.klassio.ui.screens.JoinScreen
import com.tchatz.klassio.ui.screens.LibraryScreen
import com.tchatz.klassio.ui.screens.OwnerAuthScreen
import com.tchatz.klassio.ui.screens.OwnerDashboardScreen
import com.tchatz.klassio.ui.screens.OwnerSpaceScreen
import com.tchatz.klassio.ui.screens.SettingsScreen
import com.tchatz.klassio.ui.screens.shareText
import com.tchatz.klassio.ui.theme.KlassioTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var studentVm: StudentViewModel
    private lateinit var ownerVm: OwnerViewModel

    private var pendingDocumentId: String? = null
    private var pendingClassroomId: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = (application as KlassioApp).container
        studentVm = ViewModelProvider(
            this,
            viewModelFactory { StudentViewModel(container.repository) },
        )[StudentViewModel::class.java]
        ownerVm = ViewModelProvider(
            this,
            viewModelFactory { OwnerViewModel(container.repository, container.api) },
        )[OwnerViewModel::class.java]

        intent?.getStringExtra(EXTRA_CLASSROOM_ID)?.let { classroomId ->
            pendingClassroomId = classroomId
            studentVm.rememberPendingClassroom(classroomId)
        }
        intent?.getStringExtra(EXTRA_DOCUMENT_ID)?.let { pendingDocumentId = it }

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            KlassioTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    KlassioRoot(container.repository, studentVm, ownerVm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CLASSROOM_ID)?.let { classroomId ->
            pendingClassroomId = classroomId
            studentVm.rememberPendingClassroom(classroomId)
        }
        pendingDocumentId = intent.getStringExtra(EXTRA_DOCUMENT_ID)
    }

    @Composable
    private fun KlassioRoot(
        repository: KlassioRepository,
        studentVm: StudentViewModel,
        ownerVm: OwnerViewModel,
    ) {
        val navController = rememberNavController()
        val student by studentVm.state.collectAsStateWithLifecycle()
        val owner by ownerVm.state.collectAsStateWithLifecycle()
        val context = this
        val scope = rememberCoroutineScope()
        val toast: (String) -> Unit = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        val pending = student.pendingClassroomId
        androidx.compose.runtime.LaunchedEffect(pending) {
            if (pending != null) {
                repository.sessions().firstOrNull { it.classroomId == pending }?.let { session ->
                    studentVm.open(session)
                    navController.navigate(Routes.CLASSROOM)
                }
                studentVm.clearPendingClassroom()
            }
        }

        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    student = student,
                    onJoinClick = { navController.navigate(Routes.JOIN) },
                    onOpenSession = { session ->
                        studentVm.open(session)
                        navController.navigate(Routes.CLASSROOM)
                    },
                    onOwner = { navController.navigate(if (ownerVm.isSignedIn) Routes.OWNER_DASHBOARD else Routes.OWNER_AUTH) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onRemove = { id -> studentVm.leave(id) },
                )
            }

            composable(Routes.JOIN) {
                JoinScreen(
                    joining = student.joining,
                    error = student.error,
                    onBack = { navController.popBackStack() },
                    onJoin = { code ->
                        studentVm.join(code) {
                            toast("Classe rejointe.")
                            navController.navigate(Routes.CLASSROOM) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    },
                )
            }

            composable(Routes.CLASSROOM) {
                ClassroomScreen(
                    state = student,
                    onBack = {
                        studentVm.clearError()
                        navController.popBackStack()
                    },
                    onOpenSubject = { subject ->
                        studentVm.setSubjectFilter(subject.id)
                        navController.navigate(Routes.LIBRARY)
                    },
                    onOpenLibrary = { filter ->
                        studentVm.setSubjectFilter(filter)
                        navController.navigate(Routes.LIBRARY)
                    },
                    onRefresh = { studentVm.refresh() },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = student,
                    initialSubject = student.subjectFilter,
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { document -> navController.navigate(Routes.document(document.id)) },
                    onDownload = { document ->
                        studentVm.download(document) { message -> toast(message) }
                    },
                    onSearch = { query -> studentVm.setSearch(query) },
                    onFilter = { id -> studentVm.setSubjectFilter(id) },
                )
            }

            composable(
                Routes.DOCUMENT,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val document = student.documents.firstOrNull { it.id == id }
                if (document == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Document introuvable dans le cache local.")
                    }
                } else {
                    DocumentDetailScreen(
                        document = document,
                        downloaded = student.downloads.any { it.documentId == id },
                        progress = student.downloading[id],
                        onBack = { navController.popBackStack() },
                        onDownload = { studentVm.download(document) { message -> toast(message) } },
                        onOpen = {
                            scope.launch {
                                val local = studentVm.downloadedFor(document.id)
                                if (local != null) {
                                    openLocalFile(File(local.localPath), local.mimeType, toast)
                                } else {
                                    val url = studentVm.signedUrlFor(document.id)
                                    if (url != null) {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }.onFailure {
                                            toast("Aucune application ne peut ouvrir ce format.")
                                        }
                                    } else {
                                        toast("Téléchargez le document pour le consulter hors connexion.")
                                    }
                                }
                            }
                        },
                    )
                }
            }

            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    state = student,
                    onBack = { navController.popBackStack() },
                    onOpen = { id ->
                        val file: DownloadedFile? = student.downloads.firstOrNull { it.documentId == id }
                        if (file != null) openLocalFile(File(file.localPath), file.mimeType, toast)
                        else toast("Fichier introuvable.")
                    },
                    onDelete = { id -> studentVm.deleteDownload(id) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    notificationsEnabled = student.notificationsEnabled,
                    onToggleNotifications = { enabled -> studentVm.setNotifications(enabled) },
                    onBack = { navController.popBackStack() },
                    onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenWeb = { path ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.WEB_APP_URL + path)))
                        }.onFailure { toast("Navigateur indisponible.") }
                    },
                )
            }

            composable(Routes.OWNER_AUTH) {
                OwnerAuthScreen(
                    state = owner,
                    onBack = { navController.popBackStack() },
                    onSignIn = { email, password ->
                        ownerVm.signIn(email, password)
                        navController.navigate(Routes.OWNER_DASHBOARD)
                    },
                    onSignUp = { email, password, name ->
                        ownerVm.signUp(email, password, name)
                        navController.navigate(Routes.OWNER_DASHBOARD)
                    },
                    onReset = { email -> ownerVm.sendReset(email) },
                )
            }

            composable(Routes.OWNER_DASHBOARD) {
                androidx.compose.runtime.LaunchedEffect(Unit) { ownerVm.loadClassrooms() }
                OwnerDashboardScreen(
                    state = owner,
                    onBack = { navController.popBackStack() },
                    onSignOut = {
                        ownerVm.signOut()
                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                    onOpenClassroom = { id ->
                        ownerVm.openSpace(id)
                        navController.navigate(Routes.OWNER_SPACE)
                    },
                    onCreateClassroom = { institution, className, field, level, year, academic ->
                        ownerVm.createClassroom(institution, className, field, level, year, academic)
                    },
                )
            }

            composable(Routes.OWNER_SPACE) {
                OwnerSpaceScreen(
                    state = owner,
                    onBack = { navController.popBackStack() },
                    onCreateSubject = { name, description -> ownerVm.createSubject(name, description) },
                    onRotate = { id -> ownerVm.rotateLink(id) },
                    onRevoke = { id -> ownerVm.revokeLink(id) },
                    onRegenerateCode = { ownerVm.regenerateJoinCode() },
                    onDeleteSubject = { id -> ownerVm.deleteSubject(id) },
                    onArchive = { archived -> ownerVm.archiveClassroom(archived) },
                    onShare = { link -> shareText(context, "Documents de classe — Tchatz Klassio\n$link") },
                )
            }
        }

        // Ouverture directe du document depuis une notification
        val pendingDocument = pendingDocumentId
        androidx.compose.runtime.LaunchedEffect(pendingDocument, student.documents) {
            if (pendingDocument != null && student.documents.any { it.id == pendingDocument }) {
                navController.navigate(Routes.document(pendingDocument))
                pendingDocumentId = null
            }
        }
    }

    private fun openLocalFile(file: File, mimeType: String, toast: (String) -> Unit) {
        if (!file.exists()) {
            toast("Le fichier n'existe plus sur l'appareil.")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast("Aucune application installée pour ouvrir ce format.") }
    }

    companion object {
        const val EXTRA_CLASSROOM_ID = "classroom_id"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }
}

object Routes {
    const val HOME = "home"
    const val JOIN = "join"
    const val CLASSROOM = "classroom"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val OWNER_AUTH = "owner_auth"
    const val OWNER_DASHBOARD = "owner_dashboard"
    const val OWNER_SPACE = "owner_space"
    const val DOCUMENT = "document/{id}"

    fun document(id: String) = "document/$id"
}

private inline fun <reified VM : androidx.lifecycle.ViewModel> viewModelFactory(
    crossinline create: () -> VM,
) = object : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
}
