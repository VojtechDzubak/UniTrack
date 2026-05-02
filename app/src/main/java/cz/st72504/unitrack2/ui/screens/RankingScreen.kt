package cz.st72504.unitrack2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.st72504.unitrack2.R
import cz.st72504.unitrack2.model.UserStatistics
import cz.st72504.unitrack2.ui.theme.UpceRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    allUsers: List<UserStatistics>,
    currentUserId: String,
    currentUserTeam: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val baseFilteredUsers = if (selectedTab == 0) {
        allUsers
    } else {
        allUsers.filter { it.team == currentUserTeam }
    }

    val rankedFilteredUsers = remember(baseFilteredUsers) {
        baseFilteredUsers.sortedByDescending { it.total_distance }
            .mapIndexed { index, userStatistics ->
                userStatistics.copy(rank = index + 1)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Síň slávy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
        content = { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Nerozhoduje věk ani pohlaví. Zde jsou ti nejlepší z celé univerzity.",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        if (selectedTab == 0) {
                            Button(onClick = { selectedTab = 0 }) {
                                Text("Celá univerzita")
                            }
                        } else {
                            OutlinedButton(onClick = { selectedTab = 0 }) {
                                Text("Celá univerzita")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (selectedTab == 1) {
                            Button(onClick = { selectedTab = 1 }) {
                                Text("Můj tým ($currentUserTeam)")
                            }
                        } else {
                            OutlinedButton(onClick = { selectedTab = 1 }) {
                                Text("Můj tým ($currentUserTeam)")
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(rankedFilteredUsers) { user ->
                        val isCurrentUser = user.id == currentUserId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrentUser) UpceRed.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${user.rank}.",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.width(30.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                AsyncImage(
                                    model = if (user.avatar.isNotEmpty()) "https://pb.unitrack.fun/api/files/_pb_users_auth_/${user.id}/${user.avatar}" else "",
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                    error = painterResource(id = R.drawable.ic_profile_placeholder)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isCurrentUser) "${user.name} (Ty)" else user.name,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (isCurrentUser) UpceRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user.team,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                 }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(vertical = 2.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("LVL ${user.level}", fontSize = 10.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f km", user.total_distance / 1000),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    )
}
