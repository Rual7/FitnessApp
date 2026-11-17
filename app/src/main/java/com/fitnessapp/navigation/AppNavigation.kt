package com.fitnessapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitnessapp.feature.exercises.Exercises
import com.fitnessapp.feature.exercises.HomeScreen
import com.fitnessapp.feature.splash.SplashScreen
import com.fitnessapp.feature.summary.SummaryScreen
import com.fitnessapp.feature.workout.WorkoutScreen
import com.fitnessapp.feature.workout.WorkoutViewModel

private object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val EXERCISE_ID = "exerciseId"
    const val WORKOUT = "workout/{$EXERCISE_ID}"
    const val SUMMARY = "summary"
    fun workout(exerciseId: String) = "workout/$exerciseId"
}

/** Splash -> exercise selection -> workout -> summary. */
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
                WorkoutScreen(
                    exercise = exercise,
                    onBack = { navController.popBackStack() },
                    onFinish = { navController.navigate(Routes.SUMMARY) },
                )
            }
        }
        composable(Routes.SUMMARY) { entry ->
            // The workout stays on the back stack under the summary, so its view model (the finished
            // set) is shared with this screen and is cleared when the workout is left for good.
            val workoutEntry = remember(entry) { navController.getBackStackEntry(Routes.WORKOUT) }
            val workout: WorkoutViewModel = viewModel(workoutEntry)
            val state by workout.state.collectAsStateWithLifecycle()
            state.summary?.let { summary ->
                SummaryScreen(
                    summary = summary,
                    onNewSet = {
                        workout.reset()
                        navController.popBackStack()
                    },
                    onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
        }
    }
}
