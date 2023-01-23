package org.zeith.equivadds.items.tools;

import com.google.common.collect.Multimap;
import moze_intel.projecte.capability.ModeChangerItemCapabilityWrapper;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.PETags;
import moze_intel.projecte.gameObjs.items.IItemMode;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.ToolHelper;
import moze_intel.projecte.utils.text.ILangEntry;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeith.equivadds.init.EnumMatterTypesEA;
import org.zeith.equivadds.util.ToolHelperEA;

import java.util.List;

public class ItemMorningStarEA
		extends ItemToolEA
		implements IItemMode
{
	
	private final ToolHelper.ChargeAttributeCache attributeCache = new ToolHelper.ChargeAttributeCache();
	private final ILangEntry[] modeDesc;
	
	public ItemMorningStarEA(EnumMatterTypesEA matterType, int numCharges, Properties props)
	{
		super(matterType, PETags.Blocks.MINEABLE_WITH_PE_MORNING_STAR, 16, -3, numCharges, props);
		modeDesc = new ILangEntry[] {
				PELang.MODE_MORNING_STAR_1,
				PELang.MODE_MORNING_STAR_2,
				PELang.MODE_MORNING_STAR_3,
				PELang.MODE_MORNING_STAR_4
		};
		addItemCapability(ModeChangerItemCapabilityWrapper::new);
	}
	
	@Override
	public ILangEntry[] getModeLangEntries()
	{
		return modeDesc;
	}
	
	@Override
	public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltips, @NotNull TooltipFlag flags)
	{
		super.appendHoverText(stack, level, tooltips, flags);
		tooltips.add(getToolTip(stack));
	}
	
	@Override
	public boolean canPerformAction(ItemStack stack, ToolAction toolAction)
	{
		return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction) || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(toolAction) ||
				ToolHelper.DEFAULT_PE_HAMMER_ACTIONS.contains(toolAction) || ToolHelper.DEFAULT_PE_MORNING_STAR_ACTIONS.contains(toolAction);
	}
	
	@Override
	public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity damaged, @NotNull LivingEntity damager)
	{
		ToolHelper.attackWithCharge(stack, damaged, damager, 1.0F);
		return true;
	}
	
	@Override
	public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity living)
	{
		ToolHelper.digBasedOnMode(stack, level, pos, living, Item::getPlayerPOVHitResult);
		return true;
	}
	
	@NotNull
	@Override
	public InteractionResult useOn(UseOnContext context)
	{
		Player player = context.getPlayer();
		if(player == null)
		{
			return InteractionResult.PASS;
		}
		InteractionHand hand = context.getHand();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction sideHit = context.getClickedFace();
		ItemStack stack = context.getItemInHand();
		BlockState state = level.getBlockState(pos);
		//Order that it attempts to use the item:
		// Till (Shovel), Vein (or AOE) mine gravel/clay, vein mine ore, AOE dig (if it is sand, dirt, or grass don't do depth)
		return ToolHelper.performActions(ToolHelper.flattenAOE(context, state, 0),
				() -> ToolHelper.dowseCampfire(context, state),
				() ->
				{
					if(state.is(Tags.Blocks.GRAVEL) || state.getBlock() == Blocks.CLAY)
					{
						if(ProjectEConfig.server.items.pickaxeAoeVeinMining.get())
						{
							return ToolHelper.digAOE(level, player, hand, stack, pos, sideHit, false, 0);
						}
						return ToolHelper.tryVeinMine(player, stack, pos, sideHit);
					}
					return InteractionResult.PASS;
				}, () ->
				{
					if(ItemHelper.isOre(state) && !ProjectEConfig.server.items.pickaxeAoeVeinMining.get())
					{
						return ToolHelper.tryVeinMine(player, stack, pos, sideHit);
					}
					return InteractionResult.PASS;
				}, () -> ToolHelper.digAOE(level, player, hand, stack, pos, sideHit,
						!(state.getBlock() instanceof GrassBlock) && !state.is(BlockTags.SAND) && !state.is(BlockTags.DIRT), 0));
	}
	
	@NotNull
	@Override
	public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);
		if(ProjectEConfig.server.items.pickaxeAoeVeinMining.get())
		{
			return ItemHelper.actionResultFromType(ToolHelper.mineOreVeinsInAOE(player, hand), stack);
		}
		return InteractionResultHolder.pass(stack);
	}
	
	@Override
	public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state)
	{
		return ToolHelperEA.canMatterMine(matterType, state.getBlock()) ? 1_200_000 : super.getDestroySpeed(stack, state) + 48.0F;
	}
	
	@NotNull
	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(@NotNull EquipmentSlot slot, ItemStack stack)
	{
		return attributeCache.addChargeAttributeModifier(super.getAttributeModifiers(slot, stack), slot, stack);
	}
}