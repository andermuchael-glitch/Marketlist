package com.andermuchael.marketlist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.createSupabaseClient

object MarketlistSupabase {
    const val SUPABASE_URL = "https://rijjtwpahpxzjpbkovff.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_5Ykbh2xHarsAHFzFQlmeVw_DibhKkDE"
    const val SITE_URL = "https://muchael-glitch.github.io/Marketlist/"

    fun createClient(publishableKey: String): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = publishableKey
        ) {
            install(Postgrest)
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
        }
}
