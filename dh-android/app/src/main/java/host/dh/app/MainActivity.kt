package host.dh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import host.dh.app.ui.AppTheme
import host.dh.app.ui.Bg
import host.dh.app.ui.EditorScreen
import host.dh.app.ui.LibraryScreen
import host.dh.app.ui.RecordScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize().background(Bg), color = Bg) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "record") {
                        composable("record") {
                            RecordScreen(onOpenLibrary = { nav.navigate("library") })
                        }
                        composable("library") {
                            LibraryScreen(
                                onBack = { nav.popBackStack() },
                                onEdit = { file -> nav.navigate("editor/$file") },
                            )
                        }
                        composable(
                            "editor/{file}",
                            arguments = listOf(navArgument("file") { type = NavType.StringType }),
                        ) { entry ->
                            EditorScreen(
                                fileName = entry.arguments?.getString("file") ?: "",
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
