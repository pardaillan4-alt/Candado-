package com.candado.app.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.candado.app.core.Guard
import com.candado.app.core.OwnerPolicies
import com.candado.app.core.TrustedClock
import com.candado.app.data.Store

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { CandadoTheme { MainScreen() } }
    }

    override fun onResume() {
        super.onResume()
        TrustedClock.syncAsync()
        Guard.cleanupExpired()
        OwnerPolicies.apply(this)
        Store.rev.intValue = Store.rev.intValue + 1
    }
}

private val TABS = listOf("Inicio", "Candados", "Apps", "Filtro", "Permisos")

@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = creating) { creating = false }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().background(Teal).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔒", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                if (creating) "Nuevo candado" else "Candado",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            val mode = Store.themeMode
            TextButton(onClick = {
                Store.themeMode = when (mode) { "auto" -> "dia"; "dia" -> "noche"; else -> "auto" }
            }) {
                Text(
                    when (mode) { "dia" -> "☀️ Día"; "noche" -> "🌙 Noche"; else -> "🌓 Auto" },
                    color = Color.White
                )
            }
        }
        if (creating) {
            CreateLockScreen(onDone = { creating = false; tab = 1 })
        } else {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> HomeScreen(onGo = { tab = it }, onNewLock = { creating = true })
                1 -> LocksScreen(onNewLock = { creating = true })
                2 -> AppsScreen()
                3 -> FilterScreen()
                else -> SetupScreen()
            }
        }
    }
}
