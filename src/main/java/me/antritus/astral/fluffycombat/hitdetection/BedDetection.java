package me.antritus.astral.fluffycombat.hitdetection;

import me.antritus.astral.fluffycombat.FluffyCombat;
import me.antritus.astral.fluffycombat.api.events.EntityDamageEntityByBedEvent;
import me.antritus.astral.fluffycombat.hooks.citizens.CombatTrait;
import net.citizensnpcs.api.event.NPCDamageByBlockEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BedDetection implements Listener {
	private final FluffyCombat fluffy;
	public final Map<Location, BedTag> detectionMap = new HashMap<>();

	public BedDetection(FluffyCombat fluffy) {
		this.fluffy = fluffy;
	}

	public void clear(Location location){
		BedTag tag = detectionMap.get(location);
		if (tag != null){
			detectionMap.remove(tag.headLocation);
			detectionMap.remove(tag.bodyLocation);
		}
	}

	public static Block getOppositePart(Block block) {
		Bed bedData = (Bed) block.getBlockData();
		Block face;
		if (bedData.getPart() == Bed.Part.HEAD) {
			face = block.getRelative(bedData.getFacing().getOppositeFace());
		} else {
			face = block.getRelative(bedData.getFacing());
		}
		if (!(face.getBlockData() instanceof Bed)) return null;
		return face;
	}


	@EventHandler
	private void onInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Player player = event.getPlayer();
		World world = player.getWorld();
		World.Environment environment = world.getEnvironment();
		if (environment != World.Environment.NETHER
				&& environment != World.Environment.THE_END) {
			return;
		}
		Block block = event.getClickedBlock();
		if (block == null) {
			return;
		}
		if (!(block.getBlockData() instanceof Bed bed)) {
			return;
		}
		ItemStack itemStack = player.getInventory().getItemInMainHand();
		if (itemStack.getType().isAir()) {
			itemStack = player.getInventory().getItemInOffHand();
		}
		if (player.isSneaking() && !itemStack.getType().isAir()) {
			return;
		}

		Bed.Part part = bed.getPart();
		Block headBed = part == Bed.Part.HEAD ? block : getOppositePart(block);
		Block footBed = part == Bed.Part.FOOT ? block : getOppositePart(block);
		Location headLoc = headBed != null ? headBed.getLocation().toBlockLocation() : null;
		Location footLoc = footBed != null ? footBed.getLocation().toBlockLocation() : null;
		BedTag tag = new BedTag(headLoc, footLoc, player, itemStack);
		detectionMap.put(headLoc, tag);
		detectionMap.put(footLoc, tag);
		fluffy.getServer().getAsyncScheduler().runDelayed(fluffy,
				(x) -> {
					clear(headLoc != null ? headLoc : footLoc);
				},
				2,
				TimeUnit.SECONDS
		);
	}

	@EventHandler
	public void  onBedNPCExplode(NPCDamageByBlockEvent event){
		System.out.println(event.getDamager());
		if ((event.getDamager() == null)) {
			return;
		}
		if (!(event.getDamager().getType().name().endsWith("_BED"))){
			return;
		}
		NPC npc = event.getNPC();
		CombatTrait combatTrait = npc.getTraitNullable(CombatTrait.class);
		if (combatTrait == null){
			return;
		}


		Location location = event.getDamager().getLocation().toBlockLocation();
		location.setWorld(npc.getStoredLocation().getWorld());
		BedTag bedTag = detectionMap.get(location);
		if (bedTag == null){
			return;
		}
		ItemStack itemStack = bedTag.itemStack;


		EntityDamageEntityByBedEvent newDamageEvent =
				new EntityDamageEntityByBedEvent(npc, combatTrait.getOwnerPlayer(),
						bedTag.owner,
						event.getDamager(), event.getDamager().getState(), itemStack);
		newDamageEvent.callEvent();
		if (newDamageEvent.isCancelled()) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	private void onBedExplode(EntityDamageByBlockEvent event) {
		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}
		if (!(event.getDamagerBlockState() instanceof org.bukkit.block.Bed bed)) {
			return;
		}
		Location location = bed.getLocation().toBlockLocation();
		location.setWorld(victim.getWorld());
		BedTag bedTag = detectionMap.get(location);
		if (bedTag == null){
			return;
		}
		ItemStack itemStack = bedTag.itemStack;

		EntityDamageEntityByBedEvent newDamageEvent =
				new EntityDamageEntityByBedEvent(victim,
						bedTag.owner,
						event.getDamager(), bed, itemStack);
		newDamageEvent.callEvent();
		if (newDamageEvent.isCancelled()) {
			event.setCancelled(true);
		}
	}


	public record BedTag(@Nullable Location headLocation, @Nullable Location bodyLocation, @NotNull Player owner,
	                     @Nullable ItemStack itemStack) {
	}
}