package com.andermuchael.marketlist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarketlistCloudState(
    @SerialName("user_id") val userId: String,
    val items: String = "[]",
    val budget: Double = 0.0,
    val history: String = "[]",
    @SerialName("updated_at") val updatedAt: String? = null
)

object MarketlistCloud {
    suspend fun load(client: SupabaseClient): MarketlistCloudState? {
        val session = client.auth.currentSessionOrNull() ?: return null
        val userId = session.user?.id ?: return null
        return client.from("marketlist_data")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<MarketlistCloudState>()
            .firstOrNull()
    }

    suspend fun save(
        client: SupabaseClient,
        itemsJson: String,
        budget: Double,
        historyJson: String
    ) {
        val session = client.auth.currentSessionOrNull() ?: return
        val userId = session.user?.id ?: return
        client.from("marketlist_data").upsert(
            MarketlistCloudState(
                userId = userId,
                items = itemsJson,
                budget = budget.coerceAtLeast(0.0),
                history = historyJson,
                updatedAt = null
            )
        ) {
            onConflict = "user_id"
        }
    }
}
