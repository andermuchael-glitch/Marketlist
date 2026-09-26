package com.andermuchael.marketlist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient

object MarketlistSupabase {
    const val CONFIG_URL = "https://muchael-glitch.github.io/Marketlist/supabase-config.js"
    const val SITE_URL = "https://muchael-glitch.github.io/Marketlist/"

    fun createClient(publishableKey: String): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = "https://rijjtwpahpxzjpbkovff.supabase.co",
            supabaseKey = publishableKey
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
        }
}
