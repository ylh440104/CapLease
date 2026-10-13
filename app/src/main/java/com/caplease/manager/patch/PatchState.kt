package com.caplease.manager.patch

sealed interface PatchStage {
    data object Idle : PatchStage
    data object Inspecting : PatchStage
    data class Ready(val inspection: BootInspection) : PatchStage
    data object Patching : PatchStage
    data class Done(val outcome: PatchOutcome) : PatchStage
    data class Failed(val message: String) : PatchStage
}

data class PatchUiState(
    val stage: PatchStage = PatchStage.Idle,
    val sourceName: String? = null,
    val sourcePath: String? = null,
    val superKey: String = "",
    val lastOutput: String? = null
) {
    val busy: Boolean get() = stage is PatchStage.Inspecting || stage is PatchStage.Patching

    val canPatch: Boolean
        get() = stage is PatchStage.Ready && superKey.length >= BootPatcher.MIN_SUPERKEY_LENGTH
}
