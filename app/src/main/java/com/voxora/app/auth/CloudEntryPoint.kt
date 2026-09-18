package com.voxora.app.auth

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Access to the singleton [CloudRepository] from Compose screens that are not Hilt ViewModels.
 *
 * The repository must be shared between Settings and the API usage screen, so it cannot be
 * constructed per screen — that would give each screen its own in-memory grant and its own
 * selection. This entry point is how a plain composable reaches the one instance.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudEntryPoint {
    fun cloudRepository(): CloudRepository
}
