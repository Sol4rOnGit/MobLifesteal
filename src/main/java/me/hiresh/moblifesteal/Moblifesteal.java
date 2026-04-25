package me.hiresh.moblifesteal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class Moblifesteal implements ModInitializer {

    @Override
    public void onInitialize() {
        //If a player is damaged by the entity, grant the entity the hearts and take from the player
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(((livingEntity, damageSource, amount) -> {
            if(livingEntity instanceof ServerPlayerEntity player) {
                float finalDamage = DamageUtil.getDamageLeft(livingEntity, amount, damageSource, player.getArmor(), (float)player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));

                if (player.isBlocking() && !damageSource.isIn(DamageTypeTags.BYPASSES_SHIELD)){
                    //Check if player is shielding against the damage
                    Vec3d srcPos = damageSource.getPosition();
                    if (srcPos != null){
                        Vec3d viewVec = player.getRotationVector();
                        Vec3d srcVec = srcPos.subtract(player.getEntityPos()).normalize();

                        if (srcVec.dotProduct(viewVec) < 0.0f){
                            return true;
                        }
                    }

                    return true; //Ignore if shielding
                }

                if (!(damageSource.getAttacker() instanceof LivingEntity)) {
                    return true; //Handle vanilla if not a mob causing the damage
                }

                StealHearts(player, damageSource, finalDamage);

                //Send damage flags, sound and then do not apply damage as already applied on StealHearts()
                player.getEntityWorld().sendEntityStatus(player, (byte)2);
                player.getEntityWorld().playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.ENTITY_PLAYER_HURT,
                        player.getSoundCategory(),
                        1.0f,
                        1.0f
                );
                return false;
            }
            return true; //Vanilla handling given any other scenario
        }));

        //If a player kills a hostile entity grant the player a heart
        ServerLivingEntityEvents.AFTER_DEATH.register((livingEntity, damageSource) -> {
            if(damageSource.getAttacker() instanceof ServerPlayerEntity player) {
                if (!(livingEntity instanceof Monster monster)) return; //Only if hostile entity
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
        float currentHealth = player.getHealth();
        if (currentMax < damageAmount) {
            //Set to spectator, reset health and send a message to notify them that they lost.
            player.changeGameMode(GameMode.SPECTATOR);
            playerMaxHealthAttribute.setBaseValue(20.0f);
            player.setHealth(20.0f);
            player.sendMessage(Text.literal("§cYou have lost all of your hearts! Game over."));
        } else {
            //Remove the hearts from max health
            playerMaxHealthAttribute.setBaseValue(currentMax - damageAmount);

            player.setHealth(currentHealth - damageAmount);
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
