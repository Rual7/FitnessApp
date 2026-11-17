package com.fitnessapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitnessapp.feature.exercises.Exercises
import com.fitnessapp.feature.exercises.HomeScreen
import com.fitnessapp.feature.splash.SplashScreen
import com.fitnessapp.feature.workout.WorkoutScreen

private object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val EXERCISE_ID = "exerciseId"
    const val WORKOUT = "workout/{$EXERCISE_ID}"
    fun workout(exerciseId: String) = "workout/$exerciseId"
}

/** Splash -> exercise selection -> workout. */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(onExerciseSelected = { navController.navigate(Routes.workout(it.id)) })
        }
        composable(
            route = Routes.WORKOUT,
            arguments = listOf(navArgument(Routes.EXERCISE_ID) { type = NavType.StringType }),
        ) { entry ->
            val exercise = entry.arguments?.getString(Routes.EXERCISE_ID)?.let(Exercises::byId)
            if (exercise != null) {
                WorkoutScreen(exercise = exercise, onBack = { navController.popBackStack() })
            }
        }
    }
}
