package com.robert.qalarm

data class Alarm(
    val id: Int,
    val label: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<String>,
    val isActive: Boolean = true,
    val ringtonePath: String? = null,
    val qrCode: String? = null,
    val ringtonePaths: Set<String> = emptySet()
)