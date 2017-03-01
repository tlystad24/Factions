package com.massivecraft.factions.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.massivecraft.factions.EconomyParticipator;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.Lang;
import com.massivecraft.factions.Perm;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.RelationParticipator;
import com.massivecraft.factions.event.EventFactionsChunkChangeType;
import com.massivecraft.factions.event.EventFactionsChunksChange;
import com.massivecraft.factions.event.EventFactionsMembershipChange;
import com.massivecraft.factions.event.EventFactionsMembershipChange.MembershipChangeReason;
import com.massivecraft.factions.event.EventFactionsRemovePlayerMillis;
import com.massivecraft.factions.mixin.PowerMixin;
import com.massivecraft.factions.util.RelationUtil;
import com.massivecraft.massivecore.mixin.MixinSenderPs;
import com.massivecraft.massivecore.mixin.MixinTitle;
import com.massivecraft.massivecore.ps.PS;
import com.massivecraft.massivecore.ps.PSFormatHumanSpace;
import com.massivecraft.massivecore.store.SenderEntity;
import com.massivecraft.massivecore.util.IdUtil;
import com.massivecraft.massivecore.util.MUtil;
import com.massivecraft.massivecore.xlib.gson.annotations.SerializedName;

public class MPlayer extends SenderEntity<MPlayer> implements EconomyParticipator
{
	// -------------------------------------------- //
	// META
	// -------------------------------------------- //

	public static MPlayer get(Object oid)
	{
		return MPlayerColl.get().get(oid);
	}

	// -------------------------------------------- //
	// LOAD
	// -------------------------------------------- //

	@Override
	public MPlayer load(MPlayer that)
	{
		this.lastActivityMillis = that.lastActivityMillis;
		this.factionId = that.factionId;
		this.role = that.role;
		this.title = that.title;
		this.powerBoost = that.powerBoost;
		this.power = that.power;
		this.mapAutoUpdating = that.mapAutoUpdating;
		this.overriding = that.overriding;
		this.territoryInfoTitles = that.territoryInfoTitles;

		return this;
	}

	// -------------------------------------------- //
	// IS DEFAULT
	// -------------------------------------------- //

	@Override
	public boolean isDefault()
	{
		// Last activity millis is data we use for clearing out inactive players. So it does not in itself make the player data worth keeping.
		if (this.hasFaction()) return false;
		// Role means nothing without a faction.
		// Title means nothing without a faction.
		if (this.hasPowerBoost()) return false;
		if (this.getPowerRounded() != (int) Math.round(MConf.get().defaultPlayerPower)) return false;
		// if (this.isMapAutoUpdating()) return false; // Just having an auto updating map is not in itself reason enough for database storage.
		if (this.isOverriding()) return false;
		if (this.isTerritoryInfoTitles() != MConf.get().territoryInfoTitlesDefault) return false;

		return true;
	}

	// -------------------------------------------- //
	// UPDATE FACTION INDEXES
	// -------------------------------------------- //

	public void updateFactionIndexes(String beforeId, String afterId)
	{
		// Really?
		if (!Factions.get().isDatabaseInitialized()) return;
		if (!this.attached()) return;

		// Fix IDs
		if (beforeId == null) beforeId = MConf.get().defaultPlayerFactionId;
		if (afterId == null) afterId = MConf.get().defaultPlayerFactionId;

		// NoChange
		if (MUtil.equals(beforeId, afterId)) return;

		// Resolve
		Faction before = FactionColl.get().get(beforeId, false);
		Faction after = FactionColl.get().get(afterId, false);

		// Apply
		if (before != null) before.mplayers.remove(this);
		if (after != null) after.mplayers.add(this);
	}

	@Override
	public void postAttach(String id)
	{
		String beforeId = null;
		String afterId = this.getFactionId();
		this.updateFactionIndexes(beforeId, afterId);
	}

	@Override
	public void preDetach(String id)
	{
		String before = this.getFactionId();
		String after = null;
		this.updateFactionIndexes(before, after);
	}

	// -------------------------------------------- //
	// FIELDS: RAW
	// -------------------------------------------- //
	// In this section of the source code we place the field declarations only.
	// Each field has it's own section further down since just the getter and setter logic takes up quite some place.

	// The last known time of explicit player activity, such as login or logout.
	// This value is most importantly used for removing inactive players.
	// For that reason it defaults to the current time.
	// Really inactive players will be considered newly active when upgrading Factions from 2.6 --> 2.7.
	// There is actually more than one reason we store this data ourselves and don't use the OfflinePlayer#getLastPlayed.
	// 1. I don't trust that method. It's been very buggy or even completely broken in previous Bukkit versions.
	// 2. The method depends on the player.dat files being present.
	// Server owners clear those files at times, or move their database data around between different servers.
	private long lastActivityMillis = System.currentTimeMillis();

	// This is a foreign key.
	// Each player belong to a faction.
	// Null means default.
	private String factionId = null;

	// What role does the player have in the faction?
	// Null means default.
	private Rel role = null;

	// What title does the player have in the faction?
	// The title is just for fun. It's not connected to any game mechanic.
	// The player title is similar to the faction description.
	//
	// Question: Can the title contain chat colors?
	// Answer: Yes but in such case the policy is that they already must be parsed using Txt.parse.
	// If the title contains raw markup, such as "<white>" instead of "§f" it will not be parsed and "<white>" will be displayed.
	//
	// Null means the player has no title.
	private String title = null;

	// Player usually do not have a powerboost. It defaults to 0.
	// The powerBoost is a custom increase/decrease to default and maximum power.
	// Note that player powerBoost and faction powerBoost are very similar.
	private Double powerBoost = null;

	// Each player has an individual power level.
	// The power level for online players is occasionally updated by a recurring task and the power should stay the same for offline players.
	// For that reason the value is to be considered correct when you pick it. Do not call the power update method.
	// Null means default.
	private Double power = null;

	// Has this player requested an auto-updating ascii art map?
	// Null means false
	private Boolean mapAutoUpdating = null;

	// Is this player overriding?
	// Null means false
	@SerializedName(value = "usingAdminMode")
	private Boolean overriding = null;

	// Does this player use titles for territory info?
	// Null means default specified in MConf.
	private Boolean territoryInfoTitles = null;

	// The id for the faction this player is currently autoclaiming for.
	// Null means the player isn't auto claiming.
	// NOTE: This field will not be saved to the database ever.
	private transient Faction autoClaimFaction = null;

	public Faction getAutoClaimFaction() { return this.autoClaimFaction; }
	public void setAutoClaimFaction(Faction autoClaimFaction) { this.autoClaimFaction = autoClaimFaction; }

	// Does the player have /f seechunk activated?
	// NOTE: This field will not be saved to the database ever.
	private transient boolean seeingChunk = false;
	public boolean isSeeingChunk() { return this.seeingChunk; }
	public void setSeeingChunk(boolean seeingChunk) { this.seeingChunk = seeingChunk; }

	// -------------------------------------------- //
	// CORE UTILITIES
	// -------------------------------------------- //

	public void resetFactionData()
	{
		// The default neutral faction
		this.setFactionId(null);
		this.setRole(null);
		this.setTitle(null);
		this.setAutoClaimFaction(null);
	}

	// -------------------------------------------- //
	// FIELD: lastActivityMillis
	// -------------------------------------------- //

	public long getLastActivityMillis()
	{
		return this.lastActivityMillis;
	}

	public void setLastActivityMillis(long lastActivityMillis)
	{
		// Detect Nochange
		if (MUtil.equals(this.lastActivityMillis, lastActivityMillis)) return;

		// Apply
		this.lastActivityMillis = lastActivityMillis;

		// Mark as changed
		this.changed();
	}

	public void setLastActivityMillis()
	{
		this.setLastActivityMillis(System.currentTimeMillis());
	}

	// -------------------------------------------- //
	// FIELD: factionId
	// -------------------------------------------- //
	
	// This method never returns null
	public String getFactionId()
	{
		return this.convertGet(this.factionId, MConf.get().defaultPlayerFactionId);
	}

	// This method never returns null
	public Faction getFaction()
	{
		return Faction.get(this.getFactionId());
	}

	public boolean hasFaction()
	{
		return !this.getFactionId().equals(Factions.ID_NONE);
	}

	// This setter is so long because it search for default/null case and takes
	// care of updating the faction member index
	public void setFactionId(String factionId)
	{
		// Before
		String beforeId = this.factionId;

		// NoChange
		if (MUtil.equals(beforeId, factionId)) return;

		// Apply
		this.factionId = factionId;

		// Must be attached and initialized
		if (!this.attached()) return;
		if (!Factions.get().isDatabaseInitialized()) return;

		if (beforeId == null) beforeId = MConf.get().defaultPlayerFactionId;

		// Update index
		Faction before = Faction.get(beforeId);
		Faction after = this.getFaction();

		if (before != null) before.mplayers.remove(this);
		if (after != null) after.mplayers.add(this);

		// Mark as changed
		this.changed();
	}

	public void setFaction(Faction faction)
	{
		this.setFactionId(faction.getId());
	}

	// -------------------------------------------- //
	// FIELD: role
	// -------------------------------------------- //

	public Rel getRole()
	{
		return this.convertGet(this.role, MConf.get().defaultPlayerRole);
	}

	public void setRole(Rel role)
	{
		// Detect Nochange
		if (MUtil.equals(this.role, role)) return;

		// Apply
		this.role = this.convertSet(role, MConf.get().defaultPlayerRole);
	}

	// -------------------------------------------- //
	// FIELD: title
	// -------------------------------------------- //

	public boolean hasTitle()
	{
		return this.title != null;
	}

	public String getTitle()
	{
		return this.convertGet(this.title, Lang.PLAYER_NOTITLE);
	}

	public void setTitle(String title)
	{
		// Clean input
		if (title != null)
		{
			title = title.trim();
			if (title.length() == 0)
			{
				title = null;
			}
		}

		// Detect Nochange
		if (MUtil.equals(this.title, title)) return;

		// Apply
		this.title = title;

		// Mark as changed
		this.changed();
	}

	// -------------------------------------------- //
	// FIELD: powerBoost
	// -------------------------------------------- //

	public double getPowerBoost()
	{
		return this.convertGet(this.powerBoost, 0D);
	}

	public void setPowerBoost(Double powerBoost)
	{
		// Detect Nochange
		if (MUtil.equals(this.powerBoost, powerBoost)) return;

		// Apply
		this.powerBoost = this.convertSet(powerBoost, 0D);
	}

	public boolean hasPowerBoost()
	{
		return this.getPowerBoost() != 0D;
	}

	// -------------------------------------------- //
	// FIELD: power
	// -------------------------------------------- //

	// MIXIN: RAW

	public double getPowerMaxUniversal()
	{
		return PowerMixin.get().getMaxUniversal(this);
	}

	public double getPowerMax()
	{
		return PowerMixin.get().getMax(this);
	}

	public double getPowerMin()
	{
		return PowerMixin.get().getMin(this);
	}

	public double getPowerPerHour()
	{
		return PowerMixin.get().getPerHour(this);
	}

	public double getPowerPerDeath()
	{
		return PowerMixin.get().getPerDeath(this);
	}

	// MIXIN: FINER
	public double getLimitedPower(double power)
	{
		return MUtil.limitNumber(power, this.getPowerMin(), this.getPowerMax());
	}

	public int getPowerMaxRounded()
	{
		return (int) Math.round(this.getPowerMax());
	}

	public int getPowerMinRounded()
	{
		return (int) Math.round(this.getPowerMin());
	}

	public int getPowerMaxUniversalRounded()
	{
		return (int) Math.round(this.getPowerMaxUniversal());
	}

	// RAW
	public double getPower()
	{
		Double ret = this.convertGet(this.power, MConf.get().defaultPlayerPower);
		return this.getLimitedPower(ret);
	}

	public void setPower(Double power)
	{
		// Detect Nochange
		if (MUtil.equals(this.power, power)) return;

		// Apply
		this.power = this.convertSet(power, 0D);
	}

	// FINER
	public int getPowerRounded()
	{
		return (int) Math.round(this.getPower());
	}

	// -------------------------------------------- //
	// FIELD: mapAutoUpdating
	// -------------------------------------------- //

	public boolean isMapAutoUpdating()
	{
		if (this.mapAutoUpdating == null) return false;
		if (this.mapAutoUpdating == false) return false;
		return true;
	}

	public void setMapAutoUpdating(Boolean mapAutoUpdating)
	{
		// Detect Nochange
		if (MUtil.equals(this.mapAutoUpdating, mapAutoUpdating)) return;

		// Apply
		this.mapAutoUpdating = this.convertSet(mapAutoUpdating);
	}

	// -------------------------------------------- //
	// FIELD: overriding
	// -------------------------------------------- //

	public boolean isOverriding()
	{
		if (!Boolean.TRUE.equals(this.overriding)) return false;
		if (!this.hasPermission(Perm.OVERRIDE, true))
		{
			this.setOverriding(false);
			return false;
		}
		return true;
	}

	public void setOverriding(Boolean overriding)
	{
		// Detect Nochange
		if (MUtil.equals(this.overriding, overriding)) return;

		// Apply
		this.overriding = this.convertSet(overriding);
	}

	// -------------------------------------------- //
	// FIELD: territoryInfoTitles
	// -------------------------------------------- //

	public boolean isTerritoryInfoTitles()
	{
		if (!MixinTitle.get().isAvailable()) return false;
		return this.convertGet(this.territoryInfoTitles, MConf.get().territoryInfoTitlesDefault);
	}

	public void setTerritoryInfoTitles(Boolean territoryInfoTitles)
	{
		// Detect Nochange
		if (MUtil.equals(this.territoryInfoTitles, territoryInfoTitles)) return;

		// Apply
		this.territoryInfoTitles = this.convertSet(territoryInfoTitles, MConf.get().territoryInfoTitlesDefault);

		// Mark as changed
		this.changed();
	}

	// -------------------------------------------- //
	// TITLE, NAME, FACTION NAME AND CHAT
	// -------------------------------------------- //

	public String getFactionName()
	{
		Faction faction = this.getFaction();
		return faction.isNone() ? "" : faction.getName();
	}

	// Base concatenations:
	public String getNameAndSomething(ChatColor color, String something)
	{
		// Create
		StringBuilder ret = new StringBuilder(6);
		
		// Fill
		ret.append(color);
		ret.append(this.getRole().getPrefix());
		if (something != null && something.length() > 0)
		{
			ret.append(something);
			ret.append(" ");
			if (color != null) ret.append(color.toString());
		}
		ret.append(this.getName());
		
		// Return
		return ret.toString();
	}

	public String getNameAndFactionName()
	{
		return this.getNameAndSomething(null, this.getFactionName());
	}

	// These are used in information messages
	public String getNameAndTitle(RelationParticipator rp)
	{
		return this.getNameAndSomething(this.getColorTo(rp), this.hasTitle() ? this.getTitle() : null);
	}

	// -------------------------------------------- //
	// RELATION AND RELATION COLORS
	// -------------------------------------------- //

	@Override
	public String describeTo(RelationParticipator observer, boolean ucfirst)
	{
		return RelationUtil.describeThatToMe(this, observer, ucfirst);
	}

	@Override
	public String describeTo(RelationParticipator observer)
	{
		return RelationUtil.describeThatToMe(this, observer);
	}

	@Override
	public Rel getRelationTo(RelationParticipator observer)
	{
		return RelationUtil.getRelationOfThatToMe(this, observer);
	}

	@Override
	public Rel getRelationTo(RelationParticipator observer, boolean ignorePeaceful)
	{
		return RelationUtil.getRelationOfThatToMe(this, observer, ignorePeaceful);
	}

	@Override
	public ChatColor getColorTo(RelationParticipator observer)
	{
		return RelationUtil.getColorOfThatToMe(this, observer);
	}

	// -------------------------------------------- //
	// TERRITORY
	// -------------------------------------------- //

	public boolean isInOwnTerritory()
	{
		PS ps = MixinSenderPs.get().getSenderPs(this.getId());
		if (ps == null) return false;
		return BoardColl.get().getFactionAt(ps) == this.getFaction();
	}

	public boolean isInEnemyTerritory()
	{
		PS ps = MixinSenderPs.get().getSenderPs(this.getId());
		if (ps == null) return false;
		return BoardColl.get().getFactionAt(ps).getRelationTo(this) == Rel.ENEMY;
	}

	// -------------------------------------------- //
	// INACTIVITY TIMEOUT
	// -------------------------------------------- //

	public long getRemovePlayerMillis(boolean async)
	{
		EventFactionsRemovePlayerMillis event = new EventFactionsRemovePlayerMillis(async, this);
		event.run();
		return event.getMillis();
	}

	public boolean considerRemovePlayerMillis(boolean async)
	{
		// This may or may not be required.
		// Some users have been reporting a loop issue with the same player
		// detaching over and over again.
		// Maybe skipping ahead if the player is detached will solve the issue.
		if (this.detached()) return false;

		// Get the last activity millis.
		long lastActivityMillis = this.getLastActivityMillis();

		// Consider
		long toleranceMillis = this.getRemovePlayerMillis(async);
		if (System.currentTimeMillis() - lastActivityMillis <= toleranceMillis) return false;

		// Inform
		if (MConf.get().logFactionLeave || MConf.get().logFactionKick)
		{
			Factions.get().log("Player " + this.getName() + " was auto-removed due to inactivity.");
		}

		// Apply

		// Promote a new leader if required.
		if (this.getRole() == Rel.LEADER)
		{
			Faction faction = this.getFaction();
			if (faction != null) this.getFaction().promoteNewLeader();
		}
		
		// Remove
		this.leave();
		this.detach();

		return true;
	}

	// -------------------------------------------- //
	// ACTIONS
	// -------------------------------------------- //

	public void leave()
	{
		Faction faction = this.getFaction();
		boolean permanent = faction.getFlag(MFlag.getFlagPermanent());

		if (faction.getMPlayers().size() > 1)
		{
			if (!permanent && this.getRole() == Rel.LEADER)
			{
				msg("<b>You must give the leader role to someone else first.");
				return;
			}

			if (!MConf.get().canLeaveWithNegativePower && this.getPower() < 0)
			{
				msg("<b>You cannot leave until your power is positive.");
				return;
			}
		}

		// Event
		EventFactionsMembershipChange membershipChangeEvent = new EventFactionsMembershipChange(this.getSender(), this, faction, MembershipChangeReason.LEAVE);
		membershipChangeEvent.run();
		if (membershipChangeEvent.isCancelled()) return;

		if (faction.isNormal())
		{
			for (MPlayer mplayer : faction.getMPlayersWhereOnline(true))
			{
				mplayer.msg("%s<i> left %s<i>.", this.describeTo(mplayer, true), faction.describeTo(mplayer));
			}

			if (MConf.get().logFactionLeave)
			{
				Factions.get().log(this.getName() + " left the faction: " + faction.getName());
			}
		}

		this.resetFactionData();

		if (faction.isNormal() && faction.getMPlayers().isEmpty())
		{
			faction.attemptDisband(this.getSender(), "due to the last player (" + this.getName() + ") leaving");
		}
	}

	// NEW
	public boolean tryClaim(Faction newFaction, Collection<PS> pss)
	{
		return this.tryClaim(newFaction, pss, null, null);
	}

	public boolean tryClaim(Faction newFaction, Collection<PS> pss, String formatOne, String formatMany)
	{
		// Args
		if (formatOne == null) formatOne = "<h>%s<i> %s <h>%d <i>chunk %s<i>.";
		if (formatMany == null) formatMany = "<h>%s<i> %s <h>%d <i>chunks near %s<i>.";

		if (newFaction == null) throw new NullPointerException("newFaction");

		if (pss == null) throw new NullPointerException("pss");
		final Set<PS> chunks = PS.getDistinctChunks(pss);

		// NoChange
		// We clean the chunks further by removing what does not change.
		// This is also very suggested cleaning of EventFactionsChunksChange input.
		Iterator<PS> iter = chunks.iterator();
		while (iter.hasNext())
		{
			PS chunk = iter.next();
			Faction oldFaction = BoardColl.get().getFactionAt(chunk);
			if (newFaction == oldFaction) iter.remove();
		}
		if (chunks.isEmpty())
		{
			msg("%s<i> already owns this land.", newFaction.describeTo(this, true));
			return true;
		}

		// Event
		// NOTE: We listen to this event ourselves at LOW.
		// NOTE: That is where we apply the standard checks.
		CommandSender sender = this.getSender();
		if (sender == null)
		{
			msg("<b>ERROR: Your \"CommandSender Link\" has been severed.");
			msg("<b>It's likely that you are using Cauldron.");
			msg("<b>We do currently not support Cauldron.");
			msg("<b>We would love to but lack time to develop support ourselves.");
			msg("<g>Do you know how to code? Please send us a pull request <3, sorry.");
			return false;
		}
		EventFactionsChunksChange event = new EventFactionsChunksChange(sender, chunks, newFaction);
		event.run();
		if (event.isCancelled()) return false;

		// Apply
		for (PS chunk : chunks)
		{
			BoardColl.get().setFactionAt(chunk, newFaction);
		}

		// Inform
		for (Entry<Faction, Set<PS>> entry : event.getOldFactionChunks().entrySet())
		{
			final Faction oldFaction = entry.getKey();
			final Set<PS> oldChunks = entry.getValue();
			final PS oldChunk = oldChunks.iterator().next();
			final Set<MPlayer> informees = getClaimInformees(this, oldFaction, newFaction);
			final EventFactionsChunkChangeType type = EventFactionsChunkChangeType.get(oldFaction, newFaction, this.getFaction());

			String chunkString = oldChunk.toString(PSFormatHumanSpace.get());
			String typeString = type.past;

			for (MPlayer informee : informees)
			{
				informee.msg((oldChunks.size() == 1 ? formatOne : formatMany), this.describeTo(informee, true), typeString, oldChunks.size(), chunkString);
				informee.msg("  <h>%s<i> --> <h>%s", oldFaction.describeTo(informee, true), newFaction.describeTo(informee, true));
			}
		}

		// Success
		return true;
	}

	// -------------------------------------------- //
	// UTIL
	// -------------------------------------------- //

	public static Set<MPlayer> getClaimInformees(MPlayer msender, Faction... factions)
	{
		Set<MPlayer> ret = new HashSet<MPlayer>();

		if (msender != null) ret.add(msender);

		for (Faction faction : factions)
		{
			if (faction == null) continue;
			if (faction.isNone()) continue;
			ret.addAll(faction.getMPlayers());
		}

		if (MConf.get().logLandClaims)
		{
			ret.add(MPlayer.get(IdUtil.getConsole()));
		}

		return ret;
	}

}
