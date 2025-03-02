package com.ghostipedia.cosmiccore.common.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.common.machine.owner.ArgonautsOwner;
import com.gregtechceu.gtceu.common.machine.owner.FTBOwner;
import com.gregtechceu.gtceu.common.machine.owner.IMachineOwner;
import com.gregtechceu.gtceu.common.machine.owner.PlayerOwner;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerLifecycleEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WirelessChargerMachine extends TieredEnergyMachine {

    private int tier;
    private ChargeMode mode;
    private long longRange;
    private long shortRange;
    private long chargeAmount;

    private TickableSubscription charge;

    public WirelessChargerMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        this.tier = tier;
        mode = ChargeMode.SUPER_CHARGED;
        longRange = (long)Math.pow(2, (tier + 7));
        shortRange = (long)Math.pow(2, (tier + 6));
        chargeAmount = GTValues.V[tier];
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        long chargeAmount = GTValues.V[getTier()];
        return new NotifiableEnergyContainer(this, chargeAmount * 64L, chargeAmount, 4, 0L, 0L) {
            @Override
            public long getInputAmperage() {
                if(mode == ChargeMode.SUPER_CHARGED) {
                    return 4;
                }
                return 1;
            };
        };
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if(isRemote()) return;

        charge = subscribeServerTick(this::chargeLoop);
    }

    @Override
    public void onUnload() {
        super.onUnload();

        if(charge != null) {
            charge.unsubscribe();
            charge = null;
        }
    }

    public void chargeLoop() {
        var maxChargeValue = chargeAmount * energyContainer.getInputAmperage();
        if(energyContainer.getEnergyStored() < maxChargeValue) return;
        int tickRate = mode == ChargeMode.SUPER_CHARGED ? 4 : 20;
        if(getOffsetTimer() % tickRate == 0) {
            IMachineOwner owner = getHolder().getOwner();
            List<Player> players = new ArrayList<>();
            if (owner.type() == IMachineOwner.MachineOwnerType.PLAYER) {
                UUID pUUID = ((PlayerOwner) owner).getUUID();
                Player player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(pUUID);
                if(player != null && isPlayerInRange(player)) players.add(player);
            } else if (owner.type() == IMachineOwner.MachineOwnerType.FTB) {
                Optional<Team> t = FTBTeamsAPI.api().getClientManager().getTeamByID(((FTBOwner) owner).getUUID());
                if(t.isPresent()) {
                    for (var pUUID : t.get().getMembers()) {
                        Player player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(pUUID);
                        if(player != null && isPlayerInRange(player)) players.add(player);
                    }
                }
            } else if (owner.type() == IMachineOwner.MachineOwnerType.ARGONAUTS) {
                // DN
            }

            if (players.isEmpty()) return;

            for (var player : players) {
                if (GTCEu.Mods.isCuriosLoaded()) {
                    IItemHandler curios = CuriosApi.getCuriosInventory(player)
                            .<IItemHandler>map(ICuriosItemHandler::getEquippedCurios)
                            .orElse(EmptyHandler.INSTANCE);
                    for (int i = 0; i < curios.getSlots(); i++) {
                        var itemInSlot = curios.getStackInSlot(i);
                        var slotElectricItem = GTCapabilityHelper.getElectricItem(itemInSlot);
                        if (slotElectricItem != null && slotElectricItem.canProvideChargeExternally()) {
                            slotElectricItem.charge(maxChargeValue, tier, true, false);
                            energyContainer.changeEnergy(-maxChargeValue);
                            if (energyContainer.getEnergyStored() < maxChargeValue) return;
                        }
                    }
                }

                var playerInv = player.getInventory();
                for (int i = 0; i < playerInv.getContainerSize(); i++) {
                    var itemInSlot = playerInv.getItem(i);
                    var slotElectricItem = GTCapabilityHelper.getElectricItem(itemInSlot);
                    if (slotElectricItem != null && slotElectricItem.canProvideChargeExternally()) {
                        slotElectricItem.charge(maxChargeValue, tier, true, false);
                        energyContainer.changeEnergy(-maxChargeValue);
                        if (energyContainer.getEnergyStored() < maxChargeValue) return;
                    }
                }
            }
        }
    }

    private boolean isPlayerInRange(Player player) {
        int radius = mode == ChargeMode.SUPER_CHARGED ? (int)shortRange : (int)longRange;
        BlockPos a = new BlockPos(getPos().offset(new Vec3i(-radius, -radius, -radius)));
        BlockPos b = new BlockPos(getPos().offset(new Vec3i(radius, radius, radius)));
        var entityList = getLevel().getEntities(null, new AABB(a, b));
        return entityList.contains(player);
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {

        if(!getLevel().isClientSide) {
            mode = ChargeMode.values()[((mode.ordinal() + 1) % ChargeMode.values().length)];
            if(mode == ChargeMode.SUPER_CHARGED) {
                playerIn.displayClientMessage(Component.translatable("cosmiccore.wireless_charger.mode.0", FormattingUtil.formatNumbers(longRange)),false);
            } else if (mode == ChargeMode.MIXED) {
                playerIn.displayClientMessage(Component.translatable("cosmiccore.wireless_charger.mode.1", FormattingUtil.formatNumbers(shortRange)),false);
            }
        }

        return super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
    }

    enum ChargeMode {
        SUPER_CHARGED,
        MIXED;
    }
}
