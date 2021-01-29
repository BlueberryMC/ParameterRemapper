package xyz.acrylicstyle.parameterRemapper.util

data class RemapInfo(
    val method: String,
    val signature: String,
    val paramIndex: Int,
    val oldName: String?,
    val newName: String,
    val dummy: Boolean,
)
