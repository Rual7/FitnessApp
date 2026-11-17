package com.fitnessapp.feature.exercises

import androidx.annotation.StringRes
import com.fitnessapp.R

/** An exercise that can be picked on the home screen. More are added here as they are implemented. */
data class Exercise(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val hintRes: Int,
)

object Exercises {
    const val BICEP_CURL = "bicep_curl"

    val all: List<Exercise> = listOf(
        Exercise(BICEP_CURL, R.string.exercise_bicep_curl, R.string.exercise_bicep_curl_hint),
    )

    fun byId(id: String): Exercise? = all.firstOrNull { it.id == id }
}
