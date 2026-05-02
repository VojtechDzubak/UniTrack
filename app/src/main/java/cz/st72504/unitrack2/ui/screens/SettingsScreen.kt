package cz.st72504.unitrack2.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.st72504.unitrack2.R
import cz.st72504.unitrack2.ui.theme.StravaOrange
import cz.st72504.unitrack2.ui.theme.UpceBlue
import cz.st72504.unitrack2.ui.theme.UpceGreen
import cz.st72504.unitrack2.ui.theme.UpceRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    userTeam: String,
    isPublic: Boolean,
    isStravaLinked: Boolean,
    userAvatarUrl: String,
    onSave: (String, Boolean, ByteArray?) -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onLinkStrava: () -> Unit,
    onSyncClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    BackHandler { onBack() }
    var currentName by remember { mutableStateOf(userName) }
    var currentIsPublic by remember { mutableStateOf(isPublic) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                avatarBytes = stream.readBytes()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Nastavení profilu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val imageBitmap = avatarBytes?.let {
                        try {
                            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                error = painterResource(id = R.drawable.ic_profile_placeholder)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Změnit fotku",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") }
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = currentName,
                        onValueChange = { currentName = it },
                        label = { Text("Jméno") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(checked = currentIsPublic, onCheckedChange = { currentIsPublic = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Veřejný profil", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onSave(currentName, currentIsPublic, avatarBytes) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
                    ) {
                        @Suppress("DEPRECATION")
                        Text("Uložit změny", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Připojení stravy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onLinkStrava,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStravaLinked) UpceGreen else StravaOrange
                        ),
                        enabled = !isStravaLinked
                    ) {
                        @Suppress("DEPRECATION")
                        Text(if (isStravaLinked) "Strava připojena ✅" else "Připojit Strava", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSyncClick,
                        enabled = isStravaLinked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UpceBlue)
                    ) {
                        @Suppress("DEPRECATION")
                        Text("Synchronizovat", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Odhlášení a smazání profilu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Odhlásit se")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = UpceRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        @Suppress("DEPRECATION")
                        Text("Smazat účet", color = Color.White)
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Opravdu smazat účet?") },
                    text = { Text("Tato akce je nevratná. Všechna vaše data budou smazána.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDelete()
                                showDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
                        ) { @Suppress("DEPRECATION") Text("Smazat", color = Color.White) }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteDialog = false }) { Text("Zrušit") }
                    }
                )
            }
        }
    }
}
