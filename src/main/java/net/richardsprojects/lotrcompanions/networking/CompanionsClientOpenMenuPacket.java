package net.richardsprojects.lotrcompanions.networking;

import lotr.common.entity.npc.NPCEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.richardsprojects.lotrcompanions.container.CompanionContainer;
import net.richardsprojects.lotrcompanions.core.PacketHandler;
import net.richardsprojects.lotrcompanions.npcs.HiredBreeGuard;
import net.richardsprojects.lotrcompanions.npcs.HiredGondorSoldier;

import java.util.Objects;
import java.util.function.Supplier;

public class CompanionsClientOpenMenuPacket {
    private final int entityId;

    public CompanionsClientOpenMenuPacket(int entityId) {
        this.entityId = entityId;
    }

    public static CompanionsClientOpenMenuPacket decode(PacketBuffer buf) {
        return new CompanionsClientOpenMenuPacket(buf.readInt());
    }

    public static void encode(CompanionsClientOpenMenuPacket msg, PacketBuffer buf) {
        buf.writeInt(msg.entityId);
    }

    public int getId() {
        return this.entityId;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public static void handle(CompanionsClientOpenMenuPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            CompanionsClientOpenMenuPacket.processPacket(Objects.requireNonNull(context.get().getSender()), msg);
        });

        context.get().setPacketHandled(true);
    }

    public static void processPacket(ServerPlayerEntity player, CompanionsClientOpenMenuPacket msg) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        IInventory companionInventory = null;

        if (player.level.getEntity(msg.getEntityId()) instanceof NPCEntity) {
           NPCEntity npcEntity = (NPCEntity) player.level.getEntity(msg.getEntityId());
           if (npcEntity instanceof HiredBreeGuard) {
               ((HiredBreeGuard) npcEntity).setInventoryOpen(true);
               companionInventory = ((HiredBreeGuard) npcEntity).inventory;
           }
            if (npcEntity instanceof HiredGondorSoldier) {
                ((HiredGondorSoldier) npcEntity).setInventoryOpen(true);
                companionInventory = ((HiredGondorSoldier) npcEntity).inventory;
            }
        }

        if (companionInventory != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenInventoryPacket(
                    player.containerCounter, companionInventory.getContainerSize(), msg.getEntityId()));
            player.nextContainerCounter();

            player.containerMenu = new CompanionContainer(
                    player.containerCounter, player.inventory, companionInventory, msg.getEntityId()
            );

            player.containerMenu.addSlotListener(player);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, player.containerMenu));
        }
    }
}