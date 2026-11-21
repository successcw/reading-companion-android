package com.cg.readingcompanion.android

import kotlinx.coroutines.flow.MutableStateFlow

object ServerHub {
    val context = MutableStateFlow(ReadingContext())
    val running = MutableStateFlow(false)
}
