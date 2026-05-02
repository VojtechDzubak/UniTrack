package cz.st72504.unitrack2.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.st72504.unitrack2.ui.theme.UpceRed

@Composable
fun LoggedOutView(onMicrosoftClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Tato sekce je uzamčena", 
            fontSize = 22.sp, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Pro zobrazení statistik se musíš přihlásit.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(
            onClick = onMicrosoftClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
        ) {
            Text("Přihlásit se přes Microsoft", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationForm(onSave: (String, String, Boolean, ByteArray?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    val teams = listOf("DFJP", "FES", "FEI", "FCHT", "FF", "FR", "FZS", "Rektorát")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dokončení profilu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = UpceRed)
        Text("Nastav si údaje pro univerzitní výzvu", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 32.dp))

        val imageBitmap = avatarBytes?.let {
            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
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
                Text("👤", fontSize = 60.sp)
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
            value = name, onValueChange = { name = it },
            label = { Text("Tvé jméno") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(16.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = team, onValueChange = {}, readOnly = true,
                label = { Text("Vyber fakultu") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                teams.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { team = t; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isPublic, onCheckedChange = { isPublic = it }, colors = SwitchDefaults.colors(checkedThumbColor = UpceRed))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Veřejný profil", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Ostatní uvidí tvé jméno v žebříčku", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (name.isNotEmpty() && team.isNotEmpty()) onSave(name, team, isPublic, avatarBytes) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
        ) {
            Text("Uložit a začít běhat", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}
