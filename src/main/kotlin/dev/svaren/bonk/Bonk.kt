package dev.svaren.bonk

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.ItemTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.village.VillagerData
import net.minecraft.village.VillagerProfession
import net.minecraft.registry.Registries
import net.minecraft.nbt.NbtCompound
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.registry.ActionRegistry
import com.github.quiltservertools.ledger.actions.AbstractActionType
import com.github.quiltservertools.ledger.utility.NbtUtils.createNbt
import com.github.quiltservertools.ledger.utility.Sources
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer

class Bonk : ModInitializer {
    private val logger: Logger = LoggerFactory.getLogger("BONK")

    override fun onInitialize() {
        AttackEntityCallback.EVENT.register(fun(player, world, hand, entity, _): ActionResult {
            val handItem = player.getStackInHand(hand)

            if (!(handItem.isIn(ItemTags.SHOVELS) || handItem.item == Items.MACE)
                || entity.type != EntityType.VILLAGER
                || world.server == null
            ) {
                return ActionResult.PASS
            }

            val villager = entity as VillagerEntity

            if (handItem.isIn(ItemTags.SHOVELS)) {
                val oldNbt: NbtCompound = villager.createNbt()
                if (bonkVillager(villager)) {
                    val action = BonkActionType()
                    populateAction(action, player, villager, oldNbt)
                    Ledger.api.logAction(action)
                }
            } else if (handItem.item == Items.MACE) {
                val oldNbt = villager.createNbt()
                blamVillager(villager)
                val action = BlamActionType()
                populateAction(action, player, villager, oldNbt)
                Ledger.api.logAction(action)
            } else {
                return ActionResult.PASS
            }

            // Cancel the hit
            return ActionResult.FAIL
        })

        ServerLifecycleEvents.SERVER_STARTING.register(::serverStarting)

        logger.info("Initialized!")
    }

    private fun serverStarting(server: MinecraftServer) {
        ActionRegistry.registerActionType { BonkActionType() }
        ActionRegistry.registerActionType { BlamActionType() }
    }

    private fun populateAction(action: AbstractActionType, player: PlayerEntity, villager: VillagerEntity, oldNbt: NbtCompound) {
        action.pos = villager.blockPos
        action.world = villager.entityWorld.registryKey.value
        
        action.objectIdentifier = Registries.ENTITY_TYPE.getId(villager.type)
        action.oldObjectIdentifier = Registries.ENTITY_TYPE.getId(villager.type)
        
        action.objectState = villager.createNbt().toString()
        action.oldObjectState = oldNbt.toString()
        
        action.sourceName = Sources.PLAYER
        action.sourceProfile = player.playerConfigEntry
    }

    /**
     * Attempt to bonk a villager. Will fail if the villager has no trades or if they are locked.
     * @return `true` if the bonk was successful, otherwise `false`.
     */
    private fun bonkVillager(
        villager: VillagerEntity
    ): Boolean {
        val canBeBonked: Boolean =
            !villager.villagerData.profession.matchesKey(VillagerProfession.NONE) && villager.experience == 0

        if (!canBeBonked) {
            failBonk(villager)
            return false
        }

        val serverWorld = villager.entityWorld as ServerWorld

        spawnBonkParticles(serverWorld, villager)
        playBonkSounds(serverWorld, villager)

        villager.resetOffers()

        return true
    }

    /** Play effects for a bonk that has failed. */
    private fun failBonk(villager: VillagerEntity) {
        val serverWorld = villager.entityWorld as ServerWorld

        serverWorld.spawnParticles(
            ParticleTypes.ANGRY_VILLAGER, villager.x, villager.y + 1.5, villager.z, 1, 0.0, 0.0, 0.0, 0.01
        )
        serverWorld.playSoundFromEntity(
            null, villager, SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value(), SoundCategory.NEUTRAL, 1f, 0f
        )
    }

    /** A BLAM is like a bonk but will always succeed and makes the villager unconscious for a short time. */
    private fun blamVillager(villager: VillagerEntity) {
        (villager as UnconciousEntity).unconsciousTime = 60

        val serverWorld = villager.entityWorld as ServerWorld

        spawnBlamParticles(serverWorld, villager)
        playBlamSounds(serverWorld, villager)

        villager.resetOffers()
        villager.gossip.clear()
    }

    private fun spawnBonkParticles(serverWorld: ServerWorld, entity: Entity) {
        serverWorld.spawnParticles(
            ParticleTypes.POOF, entity.x, entity.y + 1.5, entity.z, 8, 0.2, 0.2, 0.2, 0.01
        )
    }

    private fun playBonkSounds(
        serverWorld: ServerWorld, entity: Entity
    ) {
        serverWorld.playSoundFromEntity(
            null, entity, SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value(), SoundCategory.NEUTRAL, 1f, 0.7f
        )
    }

    private fun spawnBlamParticles(serverWorld: ServerWorld, entity: Entity) {
        spawnBonkParticles(serverWorld, entity)
        serverWorld.spawnParticles(
            ParticleTypes.ELECTRIC_SPARK, entity.x, entity.y + 1.5, entity.z, 20, 0.0, 0.0, 0.0, 0.8
        )
    }

    private fun playBlamSounds(
        serverWorld: ServerWorld, entity: Entity
    ) {
        playBonkSounds(serverWorld, entity)
        serverWorld.playSoundFromEntity(
            null, entity, SoundEvents.ITEM_MACE_SMASH_AIR, SoundCategory.NEUTRAL, 0.5f, 1.0f
        )
    }
}

/** Reset trade offers and profession progress. */
fun VillagerEntity.resetOffers() {
    offers = null
    experience = 0
    villagerData = VillagerData(villagerData.type, villagerData.profession, 0)
}
