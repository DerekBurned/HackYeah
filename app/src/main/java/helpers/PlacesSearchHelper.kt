package com.example.travelnow.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

class PlacesSearchHelper(context: Context) {

    private val placesClient: PlacesClient
    private var sessionToken: AutocompleteSessionToken? = null
    private val predictions = mutableListOf<AutocompletePrediction>()

    init {
        initializePlaces(context)
        placesClient = Places.createClient(context)
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    private fun initializePlaces(context: Context) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")

            if (!Places.isInitialized() && apiKey != null) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Places: ${e.message}")
        }
    }

    fun searchPlaces(
        query: String,
        onSuccess: (List<String>, List<AutocompletePrediction>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                predictions.clear()
                predictions.addAll(response.autocompletePredictions)

                val suggestionsList = response.autocompletePredictions.map {
                    it.getFullText(null).toString()
                }

                onSuccess(suggestionsList, predictions)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Place prediction error: ${exception.message}")
                onFailure(exception)
            }
    }

    fun getPredictionAt(position: Int): AutocompletePrediction? {
        return predictions.getOrNull(position)
    }

    fun clearPredictions() {
        predictions.clear()
    }

    fun resetSession() {
        sessionToken = AutocompleteSessionToken.newInstance()
        predictions.clear()
    }

    companion object {
        private const val TAG = "PlacesSearchHelper"
    }
}