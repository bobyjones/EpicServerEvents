package net.bobyjones.epicserverevents.Events;


import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.explosion.Explosion;

import java.util.ArrayList;


public class ZombieEvent {
    public static ZombieEventPhase eventPhase;
    public static ServerBossBar bossBar;
    public static BlockPos blockPos;
    public static ServerWorld world;
    public static int t = 0;
    public static ArrayList<LivingEntity> spawnedZombies = new ArrayList<>();
    public static int phaseNumber = 0;
    public static int totalInPhase = 0;
    public static LivingEntity bossZombie;
    public static int numberOfWaves = 0;
    public static boolean bossJumping = false;
    public static void run(ServerPlayerEntity Player) {
        eventPhase = ZombieEventPhase.PHASE_START;
        blockPos = Player.getBlockPos();
        world = Player.getServer().getOverworld();
        bossBar = new ServerBossBar(Text.literal("The Zombie Army approaches"), BossBar.Color.RED, BossBar.Style.PROGRESS);
        bossBar.setPercent(0);
        ServerTickEvents.END_SERVER_TICK.register(ZombieEvent::tick);
    }

    //the main function of the event, it runs every tick (20 ticks = 1 second)
    public static void tick(MinecraftServer listener) {
        //stop running when event is over
        if (eventPhase == ZombieEventPhase.DONE) {
            return;
        }
        updateZombies();

        //add players that are in overworld to boss bar, this should be ranged but im lazy
        for (ServerPlayerEntity player : world.getServer().getOverworld().getPlayers()) {
            bossBar.addPlayer(player);
        }

        //does different things depending on the phase of the event
        switch (eventPhase) {
            case PHASE_START -> {
                bossBar.setStyle(BossBar.Style.PROGRESS);
                bossBar.setName(phaseNumber < numberOfWaves ? Text.literal("A wave of zombies is coming") : Text.literal("The Mother Zombie approaches"));
                bossBar.setPercent(t/200f);
                t++;
                if (t >= 200) {
                    t = 0;
                    if (phaseNumber < numberOfWaves) {
                        phaseNumber++;

                        for (int i = 0; i < 5+phaseNumber; i++) {
                            spawnZombieGroup(ZombieGroupType.BASIC, 5, getSpawnSpot());
                        }
                        for (int i = 0; i < phaseNumber; i++) {
                            spawnZombieGroup(ZombieGroupType.STRONG, 3, getSpawnSpot());
                        }
                        for (int i = 0; i < phaseNumber; i++) {
                            spawnZombieGroup(ZombieGroupType.TANK, 3, getSpawnSpot());
                        }
                        for (int i = 0; i < phaseNumber; i++) {
                            spawnZombieGroup(ZombieGroupType.CRACK, 3, getSpawnSpot());
                        }
                        spawnZombieGroup(ZombieGroupType.JOCKEY, phaseNumber, getSpawnSpot());

                        totalInPhase = spawnedZombies.size();
                        eventPhase = ZombieEventPhase.PHASE_ONGOING;
                    }else {
                        t=200;
                        if (bossZombie == null) {
                            bossZombie = EntityType.GIANT.create(world);
                            bossZombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500);
                            bossZombie.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(3);
                            bossZombie.setHealth(500);
                            bossZombie.setPos(blockPos.getX(), blockPos.getY()+100, blockPos.getZ());
                            bossZombie.setCustomName(Text.literal("Mother Zombie"));
                            bossZombie.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 9999999, 4));
                            world.spawnEntity(bossZombie);
                            bossZombie.setVelocity(1,1,1);
                            bossZombie.setOnGround(false);
                            bossZombie.collidedSoftly = true;
                        }else {
                            bossZombie.fallDistance = 0;
                            if (bossZombie.isOnGround()) {
                                world.createExplosion(bossZombie, bossZombie.getX(), bossZombie.getY(), bossZombie.getZ(), 5f, Explosion.DestructionType.NONE);
                                t=0;
                                bossZombie.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1, 1);
                                eventPhase = ZombieEventPhase.BOSS_PHASE;
                            }
                        }

                    }
                }
            }
            case PHASE_ONGOING -> {
                bossBar.setStyle(BossBar.Style.NOTCHED_10);
                bossBar.setName(Text.literal("Zombie Attack"));
                bossBar.setPercent(spawnedZombies.size() / (float) totalInPhase);
                //bossBar.setName(Text.literal("Zombies remaining: "+ spawnedZombies.size()));
                if (spawnedZombies.size() == 0) {
                    eventPhase = ZombieEventPhase.PHASE_START;
                }
            }
            case BOSS_PHASE -> {
                bossBar.setStyle(BossBar.Style.NOTCHED_20);
                bossBar.setDarkenSky(true);
                bossBar.setThickenFog(true);
                bossBar.setColor(BossBar.Color.PINK);
                bossBar.setName(Text.literal("The Mother Zombie"));
                bossBar.setPercent(bossZombie.getHealth()/bossZombie.getMaxHealth());

                if (bossZombie.isDead()) {
                    eventPhase = ZombieEventPhase.END_PHASE;
                }

                t++;
                //action every 5 seconds
                if (!bossJumping) {
                    if (t >= 100) {
                        t=0;
                        float abilityChance = world.random.nextFloat();
                        if (abilityChance <= 0.50) {
                            float zombieType = world.random.nextFloat();
                            if (zombieType <= 0.2f) {
                                spawnZombieGroup(ZombieGroupType.STRONG, world.random.nextInt(3) + 3, bossZombie.getPos().add(0,5,0));
                            }else if (zombieType <= 0.4f) {
                                spawnZombieGroup(ZombieGroupType.TANK, world.random.nextInt(3) + 3, bossZombie.getPos().add(0, 5, 0));
                            }else if (zombieType <= 0.6f) {
                                spawnZombieGroup(ZombieGroupType.CRACK, world.random.nextInt(2) + 2, bossZombie.getPos().add(0, 5, 0));
                            } else if (zombieType <= 0.7f) {
                                spawnZombieGroup(ZombieGroupType.JOCKEY, world.random.nextInt(2) + 2, bossZombie.getPos().add(0, 5, 0));
                            } else {
                                spawnZombieGroup(ZombieGroupType.BASIC, world.random.nextInt(3) + 5, bossZombie.getPos().add(0,5,0));
                            }
                        }else if (abilityChance <= 0.85) {
                            for (Entity entity : world.getOtherEntities(bossZombie, bossZombie.getBoundingBox().expand(8), EntityPredicates.VALID_LIVING_ENTITY)) {
                                entity.setVelocity(0, 2, 0);
                            }
                        }else {
                            bossJumping = true;
                            bossZombie.setInvulnerable(true);
                            world.createExplosion(null, bossZombie.getX(), bossZombie.getY(), bossZombie.getZ(), 5, Explosion.DestructionType.NONE);
                            bossZombie.setVelocity(world.random.nextFloat()*3, 5,world.random.nextFloat()*3);
                        }
                    }
                }else {
                    if (bossZombie.isOnGround()) {
                        world.createExplosion(null, bossZombie.getX(), bossZombie.getY(), bossZombie.getZ(), 5, Explosion.DestructionType.NONE);
                        bossZombie.setInvulnerable(false);
                        bossJumping = false;
                    }
                }
            }

            case END_PHASE -> {
                //drop rewards
                for (int i = 0; i < 100; i++) {
                    ExperienceOrbEntity orb = EntityType.EXPERIENCE_ORB.create(world);
                    orb.setPos(bossZombie.getX()+(world.random.nextInt(10)-5), bossZombie.getY()+5, bossZombie.getZ()+(world.random.nextInt(10)-5));
                    world.spawnEntity(orb);
                }
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.addEnchantment(Enchantments.SHARPNESS, 5);
                sword.addEnchantment(Enchantments.UNBREAKING, 5);
                sword.addEnchantment(Enchantments.LOOTING, 5);
                sword.setCustomName(Text.literal("Mothers Sword"));

                ItemEntity item = EntityType.ITEM.create(world);
                item.setStack(sword);
                item.setPos(bossZombie.getX(), bossZombie.getY(), bossZombie.getZ());
                world.spawnEntity(item);

                //clean up stuff
                bossBar.clearPlayers();
                for (LivingEntity zombie : spawnedZombies) {
                    zombie.kill();
                }

                eventPhase = ZombieEventPhase.DONE;
            }
        }
    }

    //get a random spot in a 32 block ring
    public static Vec3d getSpawnSpot() {
        float f = world.random.nextFloat() * 6.2831855F;
        int x = blockPos.getX() + MathHelper.floor(MathHelper.cos(f) * 32.0F);
        int z = blockPos.getZ() + MathHelper.floor(MathHelper.sin(f) * 32.0F);
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        return new Vec3d(x,y,z);
    }
    
    //get rid of dead or gone zombies
    public static void updateZombies() {
        spawnedZombies.removeIf(LivingEntity::isDead);
        spawnedZombies.removeIf(LivingEntity::isRemoved);
    }

    //spawns a group of zombies at a position, and equips them according to the Zombie Type
    public static void spawnZombieGroup(ZombieGroupType type, int amount, Vec3d spawnPosition) {

        for (int i = 0; i < amount; i++) {
            //create zombie
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            zombie.setPos(spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ());
            zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 9999999, 0));
            zombie.setGlowing(true);
            //spawn zombie
            world.spawnEntity(zombie);
            spawnedZombies.add(zombie);
            //zombie.setDespawnCounter(99999999);
            //zombie.setCustomName(Text.literal("Zombie Soldier"));
            //apply case specific effects
            switch (type) {
                case BASIC -> {
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                    zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                    zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
                    zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                }
                case STRONG -> {
                    zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 99999, 5));
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                    zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                    zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                }
                case TANK -> {
                    zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 99999, 9));
                    zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 99999, 1));
                    zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 99999, 1));
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                    zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                }
                case CRACK -> {
                    zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 2));
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                    zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                    zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                    ItemStack stick = new ItemStack(Items.STICK);
                    stick.addEnchantment(Enchantments.KNOCKBACK, 3);
                    zombie.equipStack(EquipmentSlot.MAINHAND, stick);
                    zombie.setBaby(true);
                }
                case JOCKEY -> {
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                    zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                    zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                    zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
                    zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                    SpiderEntity spider = EntityType.SPIDER.create(world);
                    spider.setPos(spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ());
                    world.spawnEntity(spider);
                    spider.setGlowing(true);
                    zombie.startRiding(spider);
                }
            }
        }
    }

    //enums
    public enum ZombieGroupType {
        BASIC,
        TANK,
        STRONG,
        CRACK,
        JOCKEY
    }
    public enum ZombieEventPhase {
        PHASE_ONGOING,
        PHASE_START,
        BOSS_PHASE,
        END_PHASE,
        DONE
    }
}