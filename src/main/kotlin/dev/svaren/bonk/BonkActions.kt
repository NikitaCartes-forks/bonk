package dev.svaren.bonk

import com.github.quiltservertools.ledger.actions.AbstractActionType

class BonkActionType : AbstractActionType() {
    override val identifier = "villager-bonk"
    override fun getTranslationType() = "entity"
}

class BlamActionType : AbstractActionType() {
    override val identifier = "villager-blam"
    override fun getTranslationType() = "entity"
}
