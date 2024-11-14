package net.richardsprojects.lotrcompanions.container;

import com.github.maximuslotro.lotrrextended.ExtendedLog;
import com.mojang.datafixers.util.Pair;
import lotr.common.entity.npc.NPCEntity;
import lotr.common.item.SpearItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.IContainerListener;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.richardsprojects.lotrcompanions.npcs.HiredUnitHelper;

import java.util.ArrayList;
import java.util.List;

public class CompanionEquipmentContainer extends Container implements IContainerListener {

    private Slot[] armorSlots = new Slot[4];
    private Slot mainHand;
    private Slot offHand;

    private int entityId;

    public static final ResourceLocation EMPTY_ARMOR_SLOT_HELMET = new ResourceLocation("item/empty_armor_slot_helmet");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_CHESTPLATE = new ResourceLocation("item/empty_armor_slot_chestplate");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_LEGGINGS = new ResourceLocation("item/empty_armor_slot_leggings");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_BOOTS = new ResourceLocation("item/empty_armor_slot_boots");
    public static final ResourceLocation EMPTY_ARMOR_SLOT_SHIELD = new ResourceLocation("item/empty_armor_slot_shield");
    private static final ResourceLocation[] TEXTURE_EMPTY_SLOTS = new ResourceLocation[]{EMPTY_ARMOR_SLOT_BOOTS, EMPTY_ARMOR_SLOT_LEGGINGS, EMPTY_ARMOR_SLOT_CHESTPLATE, EMPTY_ARMOR_SLOT_HELMET};
    private static final EquipmentSlotType[] SLOT_IDS = new EquipmentSlotType[]{EquipmentSlotType.HEAD, EquipmentSlotType.CHEST, EquipmentSlotType.LEGS, EquipmentSlotType.FEET};

    private static final int[] yPos = new int[]{31, 49, 67, 85};

    private NPCEntity companion;

    private PlayerEntity player;

    private final World level;

    public static final ITextComponent CONTAINER_TITLE = new TranslationTextComponent("container.lotrcompanions.equipment");

    Inventory tempInventory;


    public CompanionEquipmentContainer(int p_39230_, PlayerInventory playerInventory, PacketBuffer extraData) {
        super(CompanionsContainers.COMPANION_EQUIPMENT_CONTAINER.get(), p_39230_);

        // read in packet data
        CompoundNBT nbt = extraData.readAnySizeNbt();
        this.entityId = nbt.getInt("entityId");

        // read equipment from NBT
        List<ItemStack> items = new ArrayList<>();
        ListNBT list = nbt.getList("equipment", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundNBT node = list.getCompound(i);
            if (node != null) {
                ItemStack item = ItemStack.of(node.getCompound("item"));
                items.add(item);
            }
        }

        // setup initial inventory
        ItemStack[] equipment = items.toArray(new ItemStack[items.size()]);
        tempInventory = new Inventory(6);
        for (int i = 0; i < equipment.length && i < 6; i++) {
            tempInventory.setItem(i, equipment[i]);
        }

        player = playerInventory.player;
        this.level = player.level;
        this.companion = (NPCEntity) this.level.getEntity(entityId);


        // add the 3 rows of player inventory
        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 142 + l * 18));
            }
        }

        // add the player's hotbar
        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 200));
        }

        // setup 4 custom armor slots
        for(int slot = 9; slot < 13; slot++) {
            final EquipmentSlotType equipmentSlotType = SLOT_IDS[slot - 9];
            armorSlots[slot - 9] = this.addSlot(new Slot(tempInventory, slot - 9, 8, yPos[slot - 9]) {
                public int getMaxStackSize() {
                    return 1;
                }

                public boolean mayPlace(ItemStack itemStack) {
                    if (itemStack.getItem() instanceof ArmorItem) {
                        ArmorItem item = (ArmorItem) itemStack.getItem();
                        return item.getSlot() == equipmentSlotType;
                    } else {
                        return false;
                    }
                }

                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(PlayerContainer.BLOCK_ATLAS, TEXTURE_EMPTY_SLOTS[equipmentSlotType.getIndex()]);
                }
            });
        }

        mainHand = this.addSlot(new Slot(tempInventory, 13 - 9,62,67));
        offHand = this.addSlot(new Slot(tempInventory, 14 - 9,62,85)  {
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(PlayerContainer.BLOCK_ATLAS, EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        this.addSlotListener(this);
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity player, int p_39254_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_39254_);

        // handle shift-clicking the main hand and sending it back to the player's inventory
        if (slot != null && slot.equals(mainHand) && slot.hasItem()) {
            if (player.inventory.getFreeSlot() > -1 || player.inventory.getSlotWithRemainingSpace(slot.getItem()) > -1) {
                player.addItem(slot.getItem());
                mainHand.set(ItemStack.EMPTY);
                mainHand.setChanged();
                HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.MAINHAND, ItemStack.EMPTY);
            } else {
                return ItemStack.EMPTY;
            }
            // handle shift-clicking offhand and sending it back to the player's inventory
        } else if (slot != null && slot.equals(offHand) && slot.hasItem()) {
            if (player.inventory.getFreeSlot() > -1 || player.inventory.getSlotWithRemainingSpace(slot.getItem()) > -1) {
                player.addItem(slot.getItem());
                offHand.set(ItemStack.EMPTY);
                offHand.setChanged();
                HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.OFFHAND, ItemStack.EMPTY);
            } else {
                return ItemStack.EMPTY;
            }
            // TODO: Write if statements for handling the other 4 gear types to fix a crash
            // TODO: Fix bug where shift-clicking items out doesn't actually remove them from the inventory the next time
            // TODO: Fix where moving an item around prevents it from syncing - can probably do this with slot listeners?
        } else if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            boolean slotUpdated = false;
            if (itemstack1.getItem() instanceof ArmorItem) {
                ArmorItem armor = (ArmorItem) itemstack1.getItem();
                if (armor.getSlot() == EquipmentSlotType.HEAD && !armorSlots[0].hasItem()) {
                    armorSlots[0].set(itemstack1);
                    armorSlots[0].setChanged();
                    HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.HEAD, itemstack1);
                    slotUpdated = true;
                } else if (armor.getSlot() == EquipmentSlotType.CHEST && !armorSlots[1].hasItem()) {
                    armorSlots[1].set(itemstack1);
                    armorSlots[1].setChanged();
                    HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.CHEST, itemstack1);
                    slotUpdated = true;
                } else if (armor.getSlot() == EquipmentSlotType.LEGS && !armorSlots[2].hasItem()) {
                    armorSlots[2].set(itemstack1);
                    armorSlots[2].setChanged();
                    HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.LEGS, itemstack1);
                    slotUpdated = true;
                } else if (armor.getSlot() == EquipmentSlotType.FEET && !armorSlots[3].hasItem()) {
                    armorSlots[3].set(itemstack1);
                    armorSlots[3].setChanged();
                    HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.FEET, itemstack1);
                    slotUpdated = true;
                }
            }

            if ((itemstack1.getItem() instanceof SwordItem || itemstack1.getItem() instanceof SpearItem) && !mainHand.hasItem()) {
                mainHand.set(itemstack1);
                mainHand.setChanged();
                HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.MAINHAND, itemstack1);
                slotUpdated = true;
            }

            if (itemstack1.getItem() instanceof ShieldItem && !offHand.hasItem()) {
                offHand.set(itemstack1);
                offHand.setChanged();
                HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.OFFHAND, itemstack1);
                slotUpdated = true;
            }

            if (slotUpdated) {
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                player.inventory.setItem(slot.index, ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(PlayerEntity p_75145_1_) {
        return true;
    }

    public int getEntityId() {
        return entityId;
    }

    public static void writeContainerInitData(PacketBuffer extraData, int entityId, List<ItemStack> equipment) {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt("entityId", entityId); // write the entity Id

        // write their equipment including empty slots
        ListNBT listnbt = new ListNBT();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.get(i);

            CompoundNBT compoundnbt = new CompoundNBT();
            CompoundNBT itemTag = new CompoundNBT();
            itemTag = stack.save(itemTag);
            compoundnbt.put("item", itemTag);
            listnbt.add(compoundnbt);
        }
        nbt.put("equipment", listnbt);

        extraData.writeNbt(nbt);
    }

    @Override
    public void refreshContainer(Container p_71110_1_, NonNullList<ItemStack> p_71110_2_) {

    }

    @Override
    public void slotChanged(Container p_71111_1_, int slot, ItemStack itemStack) {
        // TODO: Make sure there is no potential for item data loss with this implementation
        // TODO: Remove logging
        if (slot == mainHand.index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.MAINHAND, itemStack);
            ExtendedLog.info("Mainhand slot updated: " + itemStack);
        } else if (slot == offHand.index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.OFFHAND, itemStack);
            ExtendedLog.info("Offhand slot updated: " + itemStack);
        } else if (slot == armorSlots[0].index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.HEAD, itemStack);
            ExtendedLog.info("Head slot updated: " + itemStack);
        } else if (slot == armorSlots[1].index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.CHEST, itemStack);
            ExtendedLog.info("Chest slot updated: " + itemStack);
        } else if (slot == armorSlots[2].index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.LEGS, itemStack);
            ExtendedLog.info("Legs slot updated: " + itemStack);
        } else if (slot == armorSlots[3].index) {
            HiredUnitHelper.updateEquipmentSlot(companion, EquipmentSlotType.FEET, itemStack);
            ExtendedLog.info("Feet slot updated: " + itemStack);
        }
    }

    @Override
    public void setContainerData(Container p_71112_1_, int p_71112_2_, int p_71112_3_) {

    }
}
