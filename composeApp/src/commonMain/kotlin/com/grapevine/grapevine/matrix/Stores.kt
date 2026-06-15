package com.grapevine.grapevine.matrix

import org.koin.core.module.Module

expect suspend fun createRepositoriesModule(): Module
expect fun createMediaStoreModule(): Module
