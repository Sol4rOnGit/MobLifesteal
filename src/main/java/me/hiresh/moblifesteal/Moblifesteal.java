package me.hiresh.moblifesteal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class Moblifesteal implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(((livingEntity, damageSource, amount) -> {
            if(livingEntity instanceof ServerPlayerEntity player) {
                StealHearts(player, damageSource, amount);
            }
            return true;
        }));

        ServerLivingEntityEvents.AFTER_DEATH.register((livingEntity, damageSource) -> {
            if(damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                GivePlayerHeart(player);
            }
        });
    }

    public void StealHearts(ServerPlayerEntity player, DamageSource damageSource, float damageAmount) {
        if (player == null) return;
        var playerMaxHealthAttribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (playerMaxHealthAttribute == null) return;

        //Take health from player / "ban" the player
        float currentMax = player.getMaxHealth();
        if (currentMax < damageAmount) {
            //Set to spectator, reset health and send a message to notify them that they lost.
            player.changeGameMode(GameMode.SPECTATOR);
            playerMaxHealthAttribute.setBaseValue(20.0f);
            player.setHealth(20.0f);
            player.sendMessage(Text.literal("§cYou have lost all of your hearts! Game over."));
        } else {
            //Remove the hearts from max health
            playerMaxHealthAttribute.setBaseValue(currentMax - damageAmount);

            //In the case this decreases the max health below the player's health, set the health to max
            if(player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }

        //Give the lost hearts to the attacking mob
        if (damageSource.getAttacker() instanceof LivingEntity attacker) {
            var mobMaxHealthAttribute = attacker.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (mobMaxHealthAttribute == null) return;
            double currentMobMax = mobMaxHealthAttribute.getBaseValue();

            //Set max mob health
            mobMaxHealthAttribute.setBaseValue(currentMobMax + damageAmount);

            //Add the additional hearts as well
            attacker.heal(damageAmount);
        }
    }

    public void GivePlayerHeart(ServerPlayerEntity player) {
        if (player == null) return;
        var maxHealthAttribute = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealthAttribute == null) return;

        //Increase max health by a heart
        maxHealthAttribute.setBaseValue(maxHealthAttribute.getBaseValue() + 2.0f);
        if (maxHealthAttribute.getBaseValue() > 20.0f){
            maxHealthAttribute.setBaseValue(20.0f);
        }

        //Increase current health by a heart
        player.heal(2.0f);

    }
}
