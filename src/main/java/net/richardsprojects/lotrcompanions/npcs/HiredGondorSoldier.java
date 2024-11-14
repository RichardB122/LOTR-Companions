/**
 * This file has been modified from the Human Companions Mod
 * which can be found here:
 *
 * https://github.com/justinwon777/HumanCompanions/tree/main
 */

package net.richardsprojects.lotrcompanions.npcs;

import lotr.common.entity.npc.ExtendedHirableEntity;
import lotr.common.entity.npc.GondorSoldierEntity;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.GroundPathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.server.management.PreYggdrasilConverter;
import net.minecraft.util.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.richardsprojects.lotrcompanions.container.CompanionContainer;
import net.richardsprojects.lotrcompanions.core.PacketHandler;
import net.richardsprojects.lotrcompanions.npcs.ai.*;
import net.richardsprojects.lotrcompanions.networking.OpenInventoryPacket;
import net.richardsprojects.lotrcompanions.utils.Constants;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HiredGondorSoldier extends GondorSoldierEntity implements ExtendedHirableEntity {

    private static final int HEAL_RATE = 80;
    private int lastHealed = 0;
    protected static final DataParameter<Byte> DATA_FLAGS_ID = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.BYTE);
    protected static final DataParameter<Optional<UUID>> DATA_OWNERUUID_ID = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.OPTIONAL_UUID);
    private static final DataParameter<Integer> LVL = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.INT);
    private static final DataParameter<Integer> CURRENT_XP = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.INT);

    private static final DataParameter<Integer> BASE_HEALTH = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.INT);
    private static final DataParameter<Integer> MAX_XP = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.INT);

    private static final DataParameter<Integer> KILLS = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.INT);

    private static final DataParameter<Boolean> FOLLOWING = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);

    private static final DataParameter<Boolean> STATIONARY = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> PATROLLING = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> ALERT = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> GUARDING = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);

    private static final DataParameter<Float> TMP_LAST_HEALTH = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.FLOAT);
    private boolean tmpHealthLoaded = false;
    private boolean healthUpdateFromTmpHealth = false;

    private static final DataParameter<Boolean> INVENTORY_OPEN = EntityDataManager.defineId(HiredGondorSoldier.class,
            DataSerializers.BOOLEAN);

    // Data Parameters for all equipment so it can be automatically synced
    private static final DataParameter<ItemStack> EQUIPMENT_CHEST = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<ItemStack> EQUIPMENT_HEAD = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<ItemStack> EQUIPMENT_LEGS = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<ItemStack> EQUIPMENT_FEET = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<ItemStack> EQUIPMENT_MAINHAND = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<ItemStack> EQUIPMENT_OFFHAND = EntityDataManager.defineId(HiredGondorSoldier.class, DataSerializers.ITEM_STACK);

    // 9 inventory slots + 6 equipment slots
    public Inventory inventory = new Inventory(15);

    private static ItemStack[] baseGear = new ItemStack[] {
            Constants.GONDOR_SOLDIER_HEAD,
            Constants.GONDOR_SOLDIER_CHEST,
            Constants.GONDOR_SOLDIER_LEGS,
            Constants.GONDOR_SOLDIER_LEGS,
            Constants.GONDOR_SOLDIER_MAINHAND,
            Constants.GONDOR_SOLDIER_OFFHAND
    };

    /* Remove consuming goals since we have our own */
    @Override
    protected void addConsumingGoals(int prio) {}

    @Override
    protected void addNPCAI() {
        // reimplement only minimum AI and attack Goals - the rest are added in the register method
        ((GroundPathNavigator)this.getNavigation()).setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathNodeType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathNodeType.DAMAGE_FIRE, -1.0F);
        this.initialiseAttackGoals(getAttackGoalsHolder());
        this.addNPCTargetingAI();
        this.addAttackGoal(2);
    }

    public HiredGondorSoldier(EntityType<? extends GondorSoldierEntity> entityType, World level) {
        super(entityType, level);
        this.setPersistenceRequired();

        // set initial entity data
        entityData.set(EQUIPMENT_HEAD, baseGear[0]);
        entityData.set(EQUIPMENT_CHEST, baseGear[1]);
        entityData.set(EQUIPMENT_LEGS, baseGear[2]);
        entityData.set(EQUIPMENT_FEET, baseGear[3]);
        entityData.set(EQUIPMENT_MAINHAND, baseGear[4]);
        entityData.set(EQUIPMENT_OFFHAND, baseGear[5]);

        this.setTame(false);
    }

    public void updateFeetSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_FEET, stack);
    }

    public void updateHeadSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_HEAD, stack);
    }

    public void updateChestSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_CHEST, stack);
    }

    public void updateLegsSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_LEGS, stack);
    }

    public void updateMainhandSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_MAINHAND, stack);
    }

    public void updateOffhandSlot(ItemStack stack) {
        entityData.set(EQUIPMENT_OFFHAND, stack);
    }

    public Inventory getCustomInventory() {
        return inventory;
    }

    @Override
    public ItemStack eat(World world, ItemStack stack) {
        if (stack.isEdible()) {
            this.heal(stack.getItem().getFoodProperties().getNutrition());
        }
        super.eat(world, stack);
        return stack;
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLAGS_ID, (byte)0);
        this.entityData.define(DATA_OWNERUUID_ID, Optional.empty());
        this.entityData.define(LVL, 1);
        this.entityData.define(CURRENT_XP, 0);
        this.entityData.define(MAX_XP, 1);
        this.entityData.define(KILLS, 0);
        this.entityData.define(FOLLOWING, false);
        this.entityData.define(GUARDING, false);
        this.entityData.define(PATROLLING, false);
        this.entityData.define(ALERT, false);
        this.entityData.define(STATIONARY, false);
        this.entityData.define(BASE_HEALTH, 30);
        this.entityData.define(INVENTORY_OPEN, false);
        this.entityData.define(TMP_LAST_HEALTH, 30f);

        // equipment slots
        this.entityData.define(EQUIPMENT_CHEST, ItemStack.EMPTY);
        this.entityData.define(EQUIPMENT_HEAD, ItemStack.EMPTY);
        this.entityData.define(EQUIPMENT_LEGS, ItemStack.EMPTY);
        this.entityData.define(EQUIPMENT_FEET, ItemStack.EMPTY);
        this.entityData.define(EQUIPMENT_MAINHAND, ItemStack.EMPTY);
        this.entityData.define(EQUIPMENT_OFFHAND, ItemStack.EMPTY);
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return GondorSoldierEntity.regAttrs()
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D);
    }

    /**
     * This method can be run every tick from both client and server to synchronize
     * equipment
     */
    public void synchronizeEquipment() {
        // only on server side update entityData to match inventory
        if (entityData.get(EQUIPMENT_HEAD).isEmpty()) {
            setItemSlot(EquipmentSlotType.HEAD, baseGear[0]);
            inventory.setItem(9, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.HEAD, entityData.get(EQUIPMENT_HEAD));
            inventory.setItem(9, entityData.get(EQUIPMENT_HEAD));
        }

        if (entityData.get(EQUIPMENT_CHEST).isEmpty()) {
            setItemSlot(EquipmentSlotType.FEET, baseGear[1]);
            inventory.setItem(10, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.CHEST, entityData.get(EQUIPMENT_CHEST));
            inventory.setItem(10, entityData.get(EQUIPMENT_CHEST));
        }

        if (entityData.get(EQUIPMENT_LEGS).isEmpty()) {
            setItemSlot(EquipmentSlotType.LEGS, baseGear[2]);
            inventory.setItem(11, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.LEGS, entityData.get(EQUIPMENT_LEGS));
            inventory.setItem(11, entityData.get(EQUIPMENT_LEGS));
        }

        if (entityData.get(EQUIPMENT_FEET).isEmpty()) {
            setItemSlot(EquipmentSlotType.FEET, baseGear[3]);
            inventory.setItem(12, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.FEET, entityData.get(EQUIPMENT_FEET));
            inventory.setItem(12, entityData.get(EQUIPMENT_FEET));
        }

        if (entityData.get(EQUIPMENT_MAINHAND).isEmpty()) {
            setItemSlot(EquipmentSlotType.MAINHAND, baseGear[4]);
            inventory.setItem(13, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.MAINHAND, entityData.get(EQUIPMENT_MAINHAND));
            inventory.setItem(13, entityData.get(EQUIPMENT_MAINHAND));
        }

        if (entityData.get(EQUIPMENT_OFFHAND).isEmpty()) {
            setItemSlot(EquipmentSlotType.OFFHAND, baseGear[5]);
            inventory.setItem(14, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlotType.OFFHAND, entityData.get(EQUIPMENT_OFFHAND));
            inventory.setItem(14, entityData.get(EQUIPMENT_OFFHAND));
        }
    }

    public void setExpLvl(int lvl) {
        this.entityData.set(LVL, lvl);
    }

    public int getExpLvl() {
        return this.entityData.get(LVL);
    }

    public void setMobKills(int kills) {
        this.entityData.set(KILLS, kills);
    }

    public int getMobKills() {
        return this.entityData.get(KILLS);
    }

    @Override
    public ITextComponent getHiredUnitName() {
        return new TranslationTextComponent("entity.lotr.shortname.hired_gondor_soldier",
                new StringTextComponent(getPersonalInfo().getName()));
    }

    public ItemStack checkFood() {
        for (int i = 0; i < 9; ++i) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (itemstack.isEdible()) {
                return itemstack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void setCurrentXp(int currentXp) {
        this.entityData.set(CURRENT_XP, currentXp);
    }

    public int getCurrentXp() {
        return this.entityData.get(CURRENT_XP);
    }

    public boolean isInventoryOpen() {
        return this.entityData.get(INVENTORY_OPEN);
    }

    public void setMaxXp(int maxXp) {
        this.entityData.set(MAX_XP, maxXp);
    }

    public void setInventoryOpen(boolean isOpen) {
        this.entityData.set(INVENTORY_OPEN, isOpen);
    }

    public int getMaxXp() {
        return this.entityData.get(MAX_XP);
    }

    public void setBaseHealth(int health) {
        this.entityData.set(BASE_HEALTH, health);
    }

    public int getBaseHealth() {
        return this.entityData.get(BASE_HEALTH);
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {

        if (hand == Hand.MAIN_HAND) {
            if (this.isAlliedTo(player)) {
                if (!this.level.isClientSide()) {
                    this.openGui((ServerPlayerEntity) player);
                }
            }
            return ActionResultType.sidedSuccess(this.level.isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    public boolean isAlliedTo(Entity p_184191_1_) {
        if (this.isTame()) {
            LivingEntity livingentity = this.getOwner();
            if (p_184191_1_ == livingentity) {
                return true;
            }

            if (livingentity != null) {
                return livingentity.isAlliedTo(p_184191_1_);
            }
        }

        return super.isAlliedTo(p_184191_1_);
    }

    public void openGui(ServerPlayerEntity player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        player.nextContainerCounter();
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenInventoryPacket(
                player.containerCounter, this.inventory.getContainerSize(), this.getId()));
        setInventoryOpen(true);

        player.containerMenu = new CompanionContainer(
                player.containerCounter, player.inventory, inventory, getId()
        );

        player.containerMenu.addSlotListener(player);
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, player.containerMenu));
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNERUUID_ID).orElse((UUID)null);
    }

    public void setOwnerUUID(@Nullable UUID p_184754_1_) {
        this.entityData.set(DATA_OWNERUUID_ID, Optional.ofNullable(p_184754_1_));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new CustomSitGoal(this,this));
        this.goalSelector.addGoal(3, new CustomFollowOwnerGoal(this, this, 1.3D, 8.0F, 2.0F, false));
        this.goalSelector.addGoal(3, new EatGoal(this,this));
        this.goalSelector.addGoal(5, new CustomWaterAvoidingRandomWalkingGoal(this,this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(7, new LookRandomlyGoal(this));
        this.goalSelector.addGoal(8, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(9, new LowHealthGoal(this,this));
        this.targetSelector.addGoal(1, new CustomOwnerHurtByTargetGoal(this,this));
        this.targetSelector.addGoal(2, new CustomOwnerHurtTargetGoal(this,this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
    }

    public boolean wantsToAttack(LivingEntity p_142018_1_, LivingEntity p_142018_2_) {
        return true;
    }

    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        if (this.getOwnerUUID() != null) {
            tag.putUUID("Owner", this.getOwnerUUID());
        }

        // create temp NonNullList to save inventory to
        NonNullList<ItemStack> items = NonNullList.withSize(15, ItemStack.EMPTY);
        for (int i = 0; i < 15; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                items.set(i, item);
            } else {
                items.set(i, ItemStack.EMPTY);
            }
        }
        ItemStackHelper.saveAllItems(tag, items);

        tag.putBoolean("following", this.isFollowing());
        tag.putBoolean("stationary", this.isStationary());
        tag.putInt("mob_kills", this.getMobKills());
        tag.putInt("xp_level", this.getExpLvl());
        tag.putInt("current_xp", this.getCurrentXp());
        tag.putInt("max_xp", this.getMaxXp());
        tag.putInt("base_health", this.getBaseHealth());
        tag.putFloat("tmp_last_health", this.getHealth());
    }

    private float getTmpLastHealth() {
        return this.entityData.get(TMP_LAST_HEALTH);
    }

    public boolean isFollowing() {
        return this.entityData.get(FOLLOWING);
    }

    public boolean isPatrolling() {
        return this.entityData.get(PATROLLING);
    }

    @Override
    public float getHiredUnitMaxHealth() {
        return getMaxHealth();
    }

    @Override
    public float getHiredUnitHealth() {
        return getHealth();
    }

    public boolean isAlert() {
        return this.entityData.get(ALERT);
    }

    public boolean isStationary() {
        return this.entityData.get(STATIONARY);
    }

    public void setStationary(boolean stationary) {
        this.entityData.set(STATIONARY, stationary);
    }

    public void setFollowing(boolean following) {
        this.entityData.set(FOLLOWING, following);
    }

    public void tame(PlayerEntity p_193101_1_) {
        this.setTame(true);
        this.setOwnerUUID(p_193101_1_.getUUID());
        this.setFollowing(true);
    }

    public void setTame(boolean p_70903_1_) {
        byte b0 = this.entityData.get(DATA_FLAGS_ID);
        if (p_70903_1_) {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b0 | 4));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte)(b0 & -5));
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        try {
            UUID uuid = this.getOwnerUUID();
            return uuid == null ? null : this.level.getPlayerByUUID(uuid);
        } catch (IllegalArgumentException illegalargumentexception) {
            return null;
        }
    }

    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);

        UUID uuid;
        if (tag.hasUUID("Owner")) {
            uuid = tag.getUUID("Owner");
        } else {
            String ownerString = tag.getString("Owner");
            uuid = PreYggdrasilConverter.convertMobOwnerIfNecessary(this.getServer(), ownerString);
        }

        if (uuid != null) {
            try {
                this.setOwnerUUID(uuid);
                this.setTame(true);
            } catch (Throwable var4) {
                this.setTame(false);
            }
        }

        NonNullList<ItemStack> itemsStacks = NonNullList.withSize(15, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(tag, itemsStacks);
        for (int i = 0; i < 15; i++) {
            ItemStack itemStack = itemsStacks.get(i);
            if (!itemStack.equals(ItemStack.EMPTY)) {
            	this.inventory.setItem(i, itemsStacks.get(i));
            }
        }

        initializeEquipmentServerSide();

        if (tag.contains("following")) {
            this.setFollowing(tag.getBoolean("following"));
        }
        if (tag.contains("xp_level")) {
            this.setExpLvl(tag.getInt("xp_level"));
        }
        if (tag.contains("current_xp")) {
            this.setCurrentXp(tag.getInt("current_xp"));
        }
        if (tag.contains("max_xp")) {
            this.setMaxXp(tag.getInt("max_xp"));
        }
        if (tag.contains("mob_kills")) {
            this.setMobKills(tag.getInt("mob_kills"));
        }
        if (tag.contains("base_health")) {
            this.setBaseHealth(tag.getInt("base_health"));
        }
        if (tag.contains("tmp_last_health")) {
            this.entityData.set(TMP_LAST_HEALTH, tag.getFloat("tmp_last_health"));
            this.setHealth(tag.getFloat("tmp_last_health"));
            tmpHealthLoaded = true;
        }

        if (tag.contains("stationary")) {
            this.setStationary(tag.getBoolean("stationary"));
        }
    }

    public void tick() {
        // heal
        if (lastHealed == HEAL_RATE)  {
            if (this.getHealth() < this.getMaxHealth()) this.setHealth(this.getHealth() + 1);
            lastHealed = 0;
        } else {
            lastHealed++;
        }

        checkStats();
        synchronizeEquipment();

        if (tmpHealthLoaded && !healthUpdateFromTmpHealth) {
            this.setHealth(getTmpLastHealth());
            healthUpdateFromTmpHealth = true;
        }

        super.tick();
    }

    public void die(DamageSource p_70645_1_) {
        HiredUnitHelper.die(this.level, p_70645_1_, this);
        super.die(p_70645_1_);
    }

    public boolean isTame() {
        return (this.entityData.get(DATA_FLAGS_ID) & 4) != 0;
    }

    public void checkStats() {
        if ((int) this.getMaxHealth() != getBaseHealth()) {
            modifyMaxHealth(getBaseHealth() - 30, "Base Health from current level", false);
        }
    }

    /**
     * This method should only run once after the inventory has been read from
     * NBT data and then it uses the level information to only run on the server
     */
    public void initializeEquipmentServerSide() {
        // only on server side update entityData to match inventory
        if (!level.isClientSide) {
            if (inventory.getItem(9).isEmpty()) {
                entityData.set(EQUIPMENT_HEAD, baseGear[0]);
            } else {
                entityData.set(EQUIPMENT_HEAD, inventory.getItem(9));
            }
            if (inventory.getItem(10).isEmpty()) {
                entityData.set(EQUIPMENT_CHEST, baseGear[1]);
            } else {
                entityData.set(EQUIPMENT_CHEST, inventory.getItem(10));
            }
            if (inventory.getItem(11).isEmpty()) {
                entityData.set(EQUIPMENT_LEGS, baseGear[2]);
            } else {
                entityData.set(EQUIPMENT_LEGS, inventory.getItem(11));
            }
            if (inventory.getItem(12).isEmpty()) {
                entityData.set(EQUIPMENT_FEET, baseGear[3]);
            } else {
                entityData.set(EQUIPMENT_FEET, inventory.getItem(12));
            }
            if (inventory.getItem(13).isEmpty()) {
                entityData.set(EQUIPMENT_MAINHAND, baseGear[4]);
            } else {
                entityData.set(EQUIPMENT_MAINHAND, inventory.getItem(13));
            }
            if (inventory.getItem(14).isEmpty()) {
                entityData.set(EQUIPMENT_OFFHAND, baseGear[5]);
            } else {
                entityData.set(EQUIPMENT_OFFHAND, inventory.getItem(14));
            }
        }

        // update item slot on both server and client
        setItemSlot(EquipmentSlotType.HEAD, entityData.get(EQUIPMENT_HEAD));
        setItemSlot(EquipmentSlotType.CHEST, entityData.get(EQUIPMENT_CHEST));
        setItemSlot(EquipmentSlotType.LEGS, entityData.get(EQUIPMENT_LEGS));
        setItemSlot(EquipmentSlotType.FEET, entityData.get(EQUIPMENT_FEET));
        setItemSlot(EquipmentSlotType.OFFHAND, entityData.get(EQUIPMENT_OFFHAND));
        setItemSlot(EquipmentSlotType.MAINHAND, entityData.get(EQUIPMENT_MAINHAND));
    }

    @Override
    public int getHiredUnitId() {
        return getId();
    }

    public void modifyMaxHealth(int change, String name, boolean permanent) {
        ModifiableAttributeInstance attributeInstance = this.getAttribute(Attributes.MAX_HEALTH);
        Set<AttributeModifier> modifiers = attributeInstance.getModifiers();
        if (!modifiers.isEmpty()) {
            Iterator<AttributeModifier> iterator = modifiers.iterator();
            while (iterator.hasNext()) {
                AttributeModifier attributeModifier = iterator.next();
                if (attributeModifier != null && attributeModifier.getName().equals(name)) {
                    this.getAttribute(Attributes.MAX_HEALTH).removeModifier(attributeModifier);
                }
            }
        }
        AttributeModifier HEALTH_MODIFIER = new AttributeModifier(name,
                change, AttributeModifier.Operation.ADDITION);
        if (permanent) {
            attributeInstance.addPermanentModifier(HEALTH_MODIFIER);
        } else {
            attributeInstance.addTransientModifier(HEALTH_MODIFIER);
        }
    }

    @Override
    public void setHiredUnitHealth(float p_70606_1_) {
        setHealth(p_70606_1_);
    }

    public void giveExperiencePoints(int points) {
        HiredUnitHelper.giveExperiencePoints(this, points);
    }
}
