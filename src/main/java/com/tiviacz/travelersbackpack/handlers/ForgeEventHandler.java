package com.tiviacz.travelersbackpack.handlers;

import com.tiviacz.travelersbackpack.TravelersBackpack;
import com.tiviacz.travelersbackpack.blocks.SleepingBagBlock;
import com.tiviacz.travelersbackpack.capability.CapabilityUtils;
import com.tiviacz.travelersbackpack.capability.ITravelersBackpack;
import com.tiviacz.travelersbackpack.capability.TravelersBackpackCapability;
import com.tiviacz.travelersbackpack.capability.TravelersBackpackWearable;
import com.tiviacz.travelersbackpack.common.BackpackAbilities;
import com.tiviacz.travelersbackpack.common.BackpackDyeRecipe;
import com.tiviacz.travelersbackpack.config.TravelersBackpackConfig;
import com.tiviacz.travelersbackpack.init.ModItems;
import com.tiviacz.travelersbackpack.inventory.TravelersBackpackContainer;
import com.tiviacz.travelersbackpack.items.TravelersBackpackItem;
import com.tiviacz.travelersbackpack.network.SyncBackpackCapabilityClient;
import com.tiviacz.travelersbackpack.util.BackpackUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = TravelersBackpack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEventHandler
{
    @SubscribeEvent
    public static void registerCapabilities(final RegisterCapabilitiesEvent event)
    {
        event.register(ITravelersBackpack.class);
    }

    @SubscribeEvent
    public static void playerSetSpawn(PlayerSetSpawnEvent event)
    {
        Level level = event.getEntity().level;

        if(event.getNewSpawn() != null)
        {
            Block block = level.getBlockState(event.getNewSpawn()).getBlock();

            if(!level.isClientSide && block instanceof SleepingBagBlock && !TravelersBackpackConfig.COMMON.enableSleepingBagSpawnPoint.get())
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void playerWashBackpack(PlayerInteractEvent.RightClickBlock event)
    {
        ItemStack stack = event.getItemStack();

        if(event.getLevel().isClientSide || event.getEntity().isShiftKeyDown()) return;

        if(stack.getItem() == ModItems.STANDARD_TRAVELERS_BACKPACK.get())
        {
            BlockState blockState = event.getLevel().getBlockState(event.getPos());

            if(BackpackDyeRecipe.hasColor(stack) && blockState.getBlock() instanceof LayeredCauldronBlock)
            {
                if(blockState.getValue(LayeredCauldronBlock.LEVEL) > 0)
                {
                    stack.getTag().remove("Color");
                    LayeredCauldronBlock.lowerFillLevel(blockState, event.getLevel(), event.getPos());
                    event.getLevel().playSound(null, event.getPos().getX(), event.getPos().getY(), event.getPos().getY(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemEntityJoin(EntityJoinLevelEvent event)
    {
        if(!(event.getEntity() instanceof ItemEntity) || !TravelersBackpackConfig.SERVER.invulnerableBackpack.get()) return;

        if(((ItemEntity)event.getEntity()).getItem().getItem() instanceof TravelersBackpackItem)
        {
            ((ItemEntity)event.getEntity()).setUnlimitedLifetime();
            event.getEntity().setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public static void attachCapabilities(final AttachCapabilitiesEvent<Entity> event)
    {
        if(event.getObject() instanceof Player)
        {
            final TravelersBackpackWearable travelersBackpack = new TravelersBackpackWearable((Player)event.getObject());
            event.addCapability(TravelersBackpackCapability.ID, TravelersBackpackCapability.createProvider(travelersBackpack));
        }
    }

    @SubscribeEvent
    public static void playerDeath(LivingDeathEvent event)
    {
        if(event.getEntity() instanceof Player)
        {
            Player player = (Player)event.getEntity();

            if(CapabilityUtils.isWearingBackpack(player))
            {
                if(BackpackAbilities.creeperAbility(event))
                {
                    return;
                }

                if(!player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
                {
                    BackpackUtils.onPlayerDeath(player.level, player, CapabilityUtils.getWearingBackpack(player));
                }
                CapabilityUtils.synchronise((Player)event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public static void playerClone(final PlayerEvent.Clone event)
    {
        Player oldPlayer = event.getOriginal();
        oldPlayer.revive();

        CapabilityUtils.getCapability(oldPlayer)
                .ifPresent(oldTravelersBackpack -> CapabilityUtils.getCapability(event.getEntity())
                        .ifPresent(newTravelersBackpack -> newTravelersBackpack.setWearable(oldTravelersBackpack.getWearable())));
    }

    @SubscribeEvent
    public static void playerChangeDimension(final PlayerEvent.PlayerChangedDimensionEvent event)
    {
        CapabilityUtils.synchronise(event.getEntity());
    }

    @SubscribeEvent
    public static void playerJoin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        CapabilityUtils.synchronise(event.getEntity());
    }

    @SubscribeEvent
    public static void entityJoin(EntityJoinLevelEvent event)
    {
        if(event.getEntity() instanceof Player)
        {
            CapabilityUtils.synchronise((Player)event.getEntity());
        }
    }

    @SubscribeEvent
    public static void playerTracking(final PlayerEvent.StartTracking event)
    {
        if(event.getTarget() instanceof Player && !event.getTarget().level.isClientSide)
        {
            ServerPlayer target = (ServerPlayer)event.getTarget();

            CapabilityUtils.getCapability(target).ifPresent(c -> TravelersBackpack.NETWORK.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()),
                    new SyncBackpackCapabilityClient(TravelersBackpackWearable.synchroniseMinimumData(CapabilityUtils.getWearingBackpack(target)), target.getId())));
        }
    }

    @SubscribeEvent
    public static void playerTick(final TickEvent.PlayerTickEvent event)
    {
        if(event.phase == TickEvent.Phase.END && BackpackAbilities.isOnList(BackpackAbilities.ITEM_ABILITIES_LIST, CapabilityUtils.getWearingBackpack(event.player)))
        {
            TravelersBackpackContainer.abilityTick(event.player);
        }
    }

    @SubscribeEvent
    public static void explosionDetonate(final ExplosionEvent.Detonate event)
    {
        for(int i = 0; i < event.getAffectedEntities().size(); i++)
        {
            Entity entity = event.getAffectedEntities().get(i);

            if(entity instanceof ItemEntity && ((ItemEntity)entity).getItem().getItem() instanceof TravelersBackpackItem)
            {
                event.getAffectedEntities().remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onLootLoad(final LootTableLoadEvent event)
    {
        if(TravelersBackpackConfig.enableLoot)
        {
            if(event.getName().equals(new ResourceLocation("chests/abandoned_mineshaft")))
            {
                event.getTable().addPool(new LootPool.Builder().name(new ResourceLocation(TravelersBackpack.MODID, "chests/bat").toString()).build());
            }

            if(event.getName().equals(new ResourceLocation("chests/village/village_armorer")))
            {
                event.getTable().addPool(new LootPool.Builder().name(new ResourceLocation(TravelersBackpack.MODID, "chests/iron_golem").toString()).build());
            }
        }
    }

    @SubscribeEvent
    public static void addVillagerTrade(final VillagerTradesEvent event)
    {
        if(event.getType() == VillagerProfession.LIBRARIAN)
        {
            event.getTrades().get(5).add(new BackpackVillagerTrade());
        }
    }

    private static class BackpackVillagerTrade implements VillagerTrades.ItemListing
    {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random)
        {
            return new MerchantOffer(new ItemStack(Items.EMERALD, random.nextInt(64) + 48), new ItemStack(ModItems.VILLAGER_TRAVELERS_BACKPACK.get().asItem(), 1), 1, 5, 0.5F);
        }
    }
}