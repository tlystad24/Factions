package com.massivecraft.factions.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.massivecraft.factions.EconomyParticipator;
import com.massivecraft.factions.FactionEqualsPredicate;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.Lang;
import com.massivecraft.factions.PredicateRole;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.RelationParticipator;
import com.massivecraft.factions.event.EventFactionsDisband;
import com.massivecraft.factions.util.MiscUtil;
import com.massivecraft.factions.util.RelationUtil;
import com.massivecraft.massivecore.Named;
import com.massivecraft.massivecore.collections.MassiveList;
import com.massivecraft.massivecore.collections.MassiveMap;
import com.massivecraft.massivecore.collections.MassiveMapDef;
import com.massivecraft.massivecore.collections.MassiveSet;
import com.massivecraft.massivecore.collections.MassiveTreeSetDef;
import com.massivecraft.massivecore.comparator.ComparatorCaseInsensitive;
import com.massivecraft.massivecore.mixin.MixinMessage;
import com.massivecraft.massivecore.money.Money;
import com.massivecraft.massivecore.predicate.Predicate;
import com.massivecraft.massivecore.predicate.PredicateAnd;
import com.massivecraft.massivecore.predicate.PredicateVisibleTo;
import com.massivecraft.massivecore.ps.PS;
import com.massivecraft.massivecore.store.Entity;
import com.massivecraft.massivecore.store.SenderColl;
import com.massivecraft.massivecore.util.IdUtil;
import com.massivecraft.massivecore.util.MUtil;
import com.massivecraft.massivecore.util.Txt;

public class Faction extends Entity<Faction> implements EconomyParticipator, Named
{
	// -------------------------------------------- //
	// META
	// -------------------------------------------- //
	
	public static Faction get(Object oid)
	{
		return FactionColl.get().get(oid);
	}
	
	// -------------------------------------------- //
	// OVERRIDE: ENTITY
	// -------------------------------------------- //
	
	@Override
	public Faction load(Faction that)
	{
		this.name = that.name;
		this.description = that.description;
		this.motd = that.motd;
		this.createdAtMillis = that.createdAtMillis;
		this.home = that.home;
		this.powerBoost = that.powerBoost;
		this.invitedPlayerIds = that.invitedPlayerIds;
		this.relationWishes = that.relationWishes;
		this.flags = that.flags;
		this.perms = that.perms;
		
		return this;
	}
	
	@Override
	public void preDetach(String id)
	{
		// The database must be fully inited.
		// We may move factions around during upgrades.
		if (!Factions.get().isDatabaseInitialized()) return;
		
		// NOTE: Existence check is required for compatibility with some plugins.
		// If they have money ...
		if (Money.exists(this))
		{
			// ... remove it.
			Money.set(this, null, 0);	
		}
		
		// Clean the board
		BoardColl.get().clean();
		
		// Clean the mplayers
		MPlayerColl.get().clean();
	}
	
	// -------------------------------------------- //
	// FIELDS: RAW
	// -------------------------------------------- //
	// In this section of the source code we place the field declarations only.
	// Each field has it's own section further down since just the getter and setter logic takes up quite some place.
	
	// The actual faction id looks something like "54947df8-0e9e-4471-a2f9-9af509fb5889" and that is not too easy to remember for humans.
	// Thus we make use of a name. Since the id is used in all foreign key situations changing the name is fine.
	// Null should never happen. The name must not be null.
	private String name = null;
	
	// Factions can optionally set a description for themselves.
	// This description can for example be seen in territorial alerts.
	// Null means the faction has no description.
	private String description = null;
	
	// Factions can optionally set a message of the day.
	// This message will be shown when logging on to the server.
	// Null means the faction has no motd
	private String motd = null;
	
	// We store the creation date for the faction.
	// It can be displayed on info pages etc.
	private long createdAtMillis = System.currentTimeMillis();
	
	// Factions can optionally set a home location.
	// If they do their members can teleport there using /f home
	// Null means the faction has no home.
	private PS home = null;
	
	// Factions usually do not have a powerboost. It defaults to 0.
	// The powerBoost is a custom increase/decrease to default and maximum power.
	// Null means the faction has powerBoost (0).
	private Double powerBoost = null;
	
	// Can anyone join the Faction?
	// If the faction is open they can.
	// If the faction is closed an invite is required.
	// Null means default.
	// private Boolean open = null;
	
	// This is the ids of the invited players.
	// They are actually "senderIds" since you can invite "@console" to your faction.
	// Null means no one is invited
	private MassiveTreeSetDef<String, ComparatorCaseInsensitive> invitedPlayerIds = new MassiveTreeSetDef<String, ComparatorCaseInsensitive>(ComparatorCaseInsensitive.get());
	
	// The keys in this map are factionIds.
	// Null means no special relation whishes.
	private MassiveMapDef<String, Rel> relationWishes = new MassiveMapDef<String, Rel>();
	
	// The flag overrides are modifications to the default values.
	// Null means default.
	private MassiveMapDef<String, Boolean> flags = new MassiveMapDef<String, Boolean>();

	// The perm overrides are modifications to the default values.
	// Null means default.
	private MassiveMapDef<String, Set<Rel>> perms = new MassiveMapDef<String, Set<Rel>>();
	
	// -------------------------------------------- //
	// FIELD: id
	// -------------------------------------------- //
	
	// FINER
	
	public boolean isNone()
	{
		return this.getId().equals(Factions.ID_NONE);
	}
	
	public boolean isNormal()
	{
		return ! this.isNone();
	}
	
	// -------------------------------------------- //
	// FIELD: name
	// -------------------------------------------- //
	
	// RAW
	@Override
	public String getName()
	{
		String ret = this.name;
		
		if (MConf.get().factionNameForceUpperCase)
		{
			ret = ret.toUpperCase();
		}
		
		return ret;
	}
	
	public void setName(String name)
	{
		// Detect Nochange
		if (MUtil.equals(this.name, name)) return;

		// Apply
		this.name = name;
		
		// Mark as changed
		this.changed();
	}
	
	// FINER
	
	public String getComparisonName()
	{
		return MiscUtil.getComparisonString(this.getName());
	}
	
	public String getName(String prefix)
	{
		return prefix + this.getName();
	}
	
	public String getName(RelationParticipator observer)
	{
		if (observer == null) return getName();
		return this.getName(this.getColorTo(observer).toString());
	}
	
	// -------------------------------------------- //
	// FIELD: description
	// -------------------------------------------- //
	
	// RAW
	public String getDescription()
	{
		return this.convertGet(this.description, Lang.FACTION_NODESCRIPTION);
	}
	
	public void setDescription(String description)
	{
		// Detect Nochange
		if (MUtil.equals(this.description, description)) return;

		// Apply
		this.description = this.convertSet(description, "");
		
		// Mark as changed
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: motd
	// -------------------------------------------- //
	
	// RAW
	public boolean hasMotd()
	{
		return this.motd != null;
	}
	
	public String getMotd()
	{
		return Txt.parse(this.convertGet(this.motd, Lang.FACTION_NOMOTD));
	}
	
	public void setMotd(String description)
	{
		// Detect Nochange
		if (MUtil.equals(this.motd, description)) return;

		// Apply
		this.motd = this.convertSet(description, "");
		
		// Mark as changed
		this.changed();
	}
	
	// FINER
	public List<Object> getMotdMessages()
	{
		// Create
		List<Object> ret = new MassiveList<>();
		
		// Fill
		Object title = this.getName() + " - Message of the Day";
		title = Txt.titleize(title);
		ret.add(title);
		
		String motd = Txt.parse("<i>" + this.getMotd());
		ret.add(motd);
		
		ret.add("");
		
		// Return
		return ret;
	}
	
	// -------------------------------------------- //
	// FIELD: createdAtMillis
	// -------------------------------------------- //
	
	public long getCreatedAtMillis()
	{
		return this.createdAtMillis;
	}
	
	public void setCreatedAtMillis(long createdAtMillis)
	{
		// Detect Nochange
		if (MUtil.equals(this.createdAtMillis, createdAtMillis)) return;

		// Apply
		this.createdAtMillis = createdAtMillis;
		
		// Mark as changed
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: home
	// -------------------------------------------- //
	
	public PS getHome()
	{
		this.verifyHomeIsValid();
		return this.home;
	}
	
	public void verifyHomeIsValid()
	{
		if (this.isValidHome(this.home)) return;
		this.home = null;
		this.changed();
		msg("<b>Your faction home has been un-set since it is no longer in your territory.");
	}
	
	public boolean isValidHome(PS ps)
	{
		if (ps == null) return true;
		if (!MConf.get().homesMustBeInClaimedTerritory) return true;
		if (BoardColl.get().getFactionAt(ps) == this) return true;
		return false;
	}
	
	public boolean hasHome()
	{
		return this.getHome() != null;
	}
	
	public void setHome(PS home)
	{
		// Detect Nochange
		if (MUtil.equals(this.home, home)) return;
		
		// Apply
		this.home = home;
		
		// Mark as changed
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: powerBoost
	// -------------------------------------------- //
	
	// RAW
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
		
		// Mark as changed
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: open
	// -------------------------------------------- //
	
	// Nowadays this is a flag!
	@Deprecated
	public boolean isDefaultOpen()
	{
		return MFlag.getFlagOpen().isStandard();
	}
	
	@Deprecated
	public boolean isOpen()
	{
		return this.getFlag(MFlag.getFlagOpen());
	}
	
	@Deprecated
	public void setOpen(Boolean open)
	{
		MFlag flag = MFlag.getFlagOpen();
		if (open == null) open = flag.isStandard();
		this.setFlag(flag, open);
	}
	
	// -------------------------------------------- //
	// FIELD: invitedPlayerIds
	// -------------------------------------------- //
	
	// RAW
	public MassiveTreeSetDef<String, ComparatorCaseInsensitive> getInvitedPlayerIds()
	{
		return this.invitedPlayerIds;
	}
	
	public void setInvitedPlayerIds(MassiveTreeSetDef<String, ComparatorCaseInsensitive> invitedPlayerIds)
	{
		// Detect Nochange
		if (MUtil.equals(this.invitedPlayerIds, invitedPlayerIds)) return;
		
		// Apply
		this.invitedPlayerIds = invitedPlayerIds;
		
		// Mark as changed
		this.changed();
	}
	
	// FINER
	private boolean isInvited(String playerId)
	{
		return this.getInvitedPlayerIds().contains(playerId);
	}
	
	public boolean isInvited(MPlayer mplayer)
	{
		return this.isInvited(mplayer.getId());
	}
	
	private boolean setInvited(String playerId, boolean invited)
	{
		// Get
		MassiveTreeSetDef<String, ComparatorCaseInsensitive> invitedPlayerIds = new MassiveTreeSetDef<String, ComparatorCaseInsensitive>(ComparatorCaseInsensitive.get(), this.getInvitedPlayerIds());
		
		// Set
		boolean ret;
		if (invited)
		{
			ret = invitedPlayerIds.add(playerId);
		}
		else
		{
			ret = invitedPlayerIds.remove(playerId);
		}
		
		// Return
		this.changed();
		return ret;
	}
	
	public void setInvited(MPlayer mplayer, boolean invited)
	{
		this.setInvited(mplayer.getId(), invited);
	}
	
	public List<MPlayer> getInvitedMPlayers()
	{
		// Create
		List<MPlayer> mplayers = new ArrayList<MPlayer>();
		
		// Fill
		for (String id : this.getInvitedPlayerIds())
		{	
			MPlayer mplayer = MPlayer.get(id);
			if (mplayer != null) mplayers.add(mplayer);
		}
		
		// Return
		return mplayers;
	}
	
	// -------------------------------------------- //
	// FIELD: relationWish
	// -------------------------------------------- //
	
	// RAW
	public MassiveMapDef<String, Rel> getRelationWishes()
	{
		return this.relationWishes;
	}
	
	public void setRelationWishes(MassiveMapDef<String, Rel> relationWishes)
	{
		// Detect Nochange
		if (MUtil.equals(this.relationWishes, relationWishes)) return;
		
		// Apply
		this.relationWishes = relationWishes;
		
		// Mark as changed
		this.changed();
	}
	
	// FINER
	private Rel getRelationWish(String factionId)
	{
		return this.convertGet(this.getRelationWishes().get(factionId), Rel.NEUTRAL);
	}
	
	public Rel getRelationWish(Faction faction)
	{
		return this.getRelationWish(faction.getId());
	}
	
	private void setRelationWish(String factionId, Rel rel)
	{
		// Get
		Map<String, Rel> relationWishes = this.getRelationWishes();
		
		// Set
		if (rel == null || rel == Rel.NEUTRAL)
		{
			relationWishes.remove(factionId);
		}
		else
		{
			relationWishes.put(factionId, rel);
		}
		
		// Changed
		this.changed();
	}
	
	public void setRelationWish(Faction faction, Rel rel)
	{
		this.setRelationWish(faction.getId(), rel);
	}
	
	// -------------------------------------------- //
	// FIELD: flagOverrides
	// -------------------------------------------- //
	
	// RAW
	public Map<MFlag, Boolean> getFlags()
	{
		// We start with default values ...
		Map<MFlag, Boolean> ret = new LinkedHashMap<MFlag, Boolean>();
		for (MFlag mflag : MFlag.getAll())
		{
			ret.put(mflag, mflag.isStandard());
		}
		
		// ... and if anything is explicitly set we use that info ...
		for (Iterator<Entry<String, Boolean>> it = this.flags.entrySet().iterator(); it.hasNext();)
		{
			// ... for each entry ...
			Entry<String, Boolean> entry = it.next();
			
			// ... extract id and remove null values ...
			String id = entry.getKey();
			if (id == null)
			{
				it.remove();
				this.changed();
				continue;
			}
			
			// ... resolve object and skip unknowns ...
			MFlag mflag = MFlag.get(id);
			if (mflag == null) continue;
			
			ret.put(mflag, entry.getValue());
		}
		
		return ret;
	}
	
	private void setFlagIds(MassiveMapDef<String, Boolean> flagIds)
	{
		// Detect Nochange
		if (MUtil.equals(this.flags, flagIds)) return;
		
		// Apply
		this.flags = flagIds;
		
		// Mark as changed
		this.changed();
	}
	
	// FINER
	public boolean getFlag(String flagId)
	{
		if (flagId == null) throw new NullPointerException("flagId");
		
		Boolean ret = this.flags.get(flagId);
		if (ret != null) return ret;
		
		MFlag flag = MFlag.get(flagId);
		if (flag == null) throw new NullPointerException("flag");
		
		return flag.isStandard();
	}
	
	public boolean getFlag(MFlag flag)
	{
		if (flag == null) throw new NullPointerException("flag");
		return this.getFlag(flag.getId());
	}
	
	private Boolean setFlag(String flagId, boolean value)
	{
		if (flagId == null) throw new NullPointerException("flagId");
		
		Boolean ret = this.flags.put(flagId, value);
		if (ret == null || ret != value) this.changed();
		return ret;
	}
	
	public Boolean setFlag(MFlag flag, boolean value)
	{
		if (flag == null) throw new NullPointerException("flag");
		
		return this.setFlag(flag.getId(), value);
	}
	
	// -------------------------------------------- //
	// FIELD: permOverrides
	// -------------------------------------------- //
	
	// RAW
	public MassiveMapDef<String, Set<Rel>> getPermIds()
	{
		return this.perms;
	}
	
	public void setPermIds(MassiveMapDef<String, Set<Rel>> perms)
	{
		// Clean input
		MassiveMapDef<String, Set<Rel>> target = new MassiveMapDef<String, Set<Rel>>();
		for (Entry<String, Set<Rel>> entry : perms.entrySet())
		{
			String key = entry.getKey();
			if (key == null) continue;
			key = key.toLowerCase(); // Lowercased Keys Version 2.6.0 --> 2.7.0
			
			Set<Rel> value = entry.getValue();
			if (value == null) continue;
			
			target.put(key, value);
		}
		
		// Detect Nochange
		if (MUtil.equals(this.perms, target)) return;
		
		// Apply
		this.perms = target;
		
		// Mark as changed
		this.changed();
	}
	
	// Finer
	public Map<MPerm, Set<Rel>> getPerms()
	{
		// We start with default values ...
		Map<MPerm, Set<Rel>> ret = new MassiveMap<>();
		for (MPerm mperm : MPerm.getAll())
		{
			ret.put(mperm, new LinkedHashSet<>(mperm.getStandard()));
		}
		
		// ... and if anything is explicitly set we use that info ...
		for (Iterator<Entry<String, Set<Rel>>> it = this.perms.entrySet().iterator(); it.hasNext();)
		{
			// ... for each entry ...
			Entry<String, Set<Rel>> entry = it.next();
			
			// ... extract id and remove null values ...
			String id = entry.getKey();					
			if (id == null)
			{
				it.remove();
				continue;
			}
			
			// ... resolve object and skip unknowns ...
			MPerm mperm = MPerm.get(id);
			if (mperm == null) continue;
			
			ret.put(mperm, new MassiveSet<>(entry.getValue()));
		}
		
		return ret;
	}
	
	public void setPerms(Map<MPerm, Set<Rel>> perms)
	{
		// Create
		MassiveMapDef<String, Set<Rel>> permIds = new MassiveMapDef<>();
		
		// Fill
		for (Entry<MPerm, Set<Rel>> entry : perms.entrySet())
		{
			permIds.put(entry.getKey().getId(), entry.getValue());
		}
		
		// Set
		this.setPermIds(permIds);
	}
	
	// FINEST
	private boolean isPermitted(String permId, Rel rel)
	{
		if (permId == null) throw new NullPointerException("permId");
		
		Set<Rel> rels = this.perms.get(permId);
		if (rels != null) return rels.contains(rel);
		
		MPerm perm = MPerm.get(permId);
		if (perm == null) throw new NullPointerException("perm");
		
		return perm.getStandard().contains(rel);
	}
	
	public boolean isPermitted(MPerm perm, Rel rel)
	{
		if (perm == null) throw new NullPointerException("perm");
		return this.isPermitted(perm.getId(), rel);
	}
	
	private Set<Rel> getPermitted(String permId)
	{
		Set<Rel> rels = this.perms.get(permId);
		if (rels != null) return rels;
		
		MPerm perm = MPerm.get(permId);
		if (perm == null) throw new NullPointerException("perm");
		
		return perm.getStandard();
	}
	
	public Set<Rel> getPermitted(MPerm perm)
	{
		if (perm == null) throw new NullPointerException("perm");
		return this.getPermitted(perm.getId());
	}
	
	public void setPermittedRelations(MPerm perm, Set<Rel> rels)
	{
		this.getPermIds().put(perm.getId(), rels);
		this.changed();
	}
	
	public void setPermittedRelations(MPerm perm, Rel... rels)
	{
		this.setPermittedRelations(perm, new MassiveSet<>(rels));
	}
	
	public void setRelationPermitted(MPerm perm, Rel rel, boolean permitted)
	{
		// Get
		Map<String, Set<Rel>> perms = this.getPermIds();
		String permId = perm.getId();
		Set<Rel> rels = perms.get(permId);
		
		// Change
		if (permitted)
		{
			rels.add(rel);
		}
		else
		{
			rels.remove(rel);
		}
		
		// Set
		this.setPermittedRelations(perm, rels);
	}
	
	// -------------------------------------------- //
	// OVERRIDE: RelationParticipator
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
	// POWER
	// -------------------------------------------- //
	// TODO: Implement a has enough feature.
	
	public double getPower()
	{
		if (this.getFlag(MFlag.getFlagInfpower())) return 999999;
		
		double ret = 0;
		for (MPlayer mplayer : this.getMPlayers())
		{
			ret += mplayer.getPower();
		}
		
		double factionPowerMax = MConf.get().factionPowerMax;
		if (factionPowerMax > 0 && ret > factionPowerMax)
		{
			ret = factionPowerMax;
		}
		
		ret += this.getPowerBoost();
		
		return ret;
	}
	
	public double getPowerMax()
	{
		if (this.getFlag(MFlag.getFlagInfpower())) return 999999;
	
		double ret = 0;
		for (MPlayer mplayer : this.getMPlayers())
		{
			ret += mplayer.getPowerMax();
		}
		
		double factionPowerMax = MConf.get().factionPowerMax;
		if (factionPowerMax > 0 && ret > factionPowerMax)
		{
			ret = factionPowerMax;
		}
		
		ret += this.getPowerBoost();
		
		return ret;
	}
	
	public int getPowerRounded()
	{
		return (int) Math.round(this.getPower());
	}
	
	public int getPowerMaxRounded()
	{
		return (int) Math.round(this.getPowerMax());
	}
	
	public int getLandCount()
	{
		return BoardColl.get().getCount(this);
	}
	public int getLandCountInWorld(String worldName)
	{
		return Board.get(worldName).getCount(this);
	}
	
	public boolean hasLandInflation()
	{
		return this.getLandCount() > this.getPowerRounded();
	}
	
	// -------------------------------------------- //
	// WORLDS
	// -------------------------------------------- //
	
	public Set<String> getClaimedWorlds()
	{
		return BoardColl.get().getClaimedWorlds(this);
	}
	
	// -------------------------------------------- //
	// FOREIGN KEY: MPLAYER
	// -------------------------------------------- //
	
	protected transient Set<MPlayer> mplayers = new MassiveSet<MPlayer>();
	
	public void reindexMPlayers()
	{
		this.mplayers.clear();
		
		String factionId = this.getId();
		if (factionId == null) return;
		
		for (MPlayer mplayer : MPlayerColl.get().getAll())
		{
			if (!MUtil.equals(factionId, mplayer.getFactionId())) continue;
			this.mplayers.add(mplayer);
		}
	}
	
	// TODO: Even though this check method removeds the invalid entries it's not a true solution.
	// TODO: Find the bug causing non-attached MPlayers to be present in the index.
	private void checkMPlayerIndex()
	{
		Iterator<MPlayer> iter = this.mplayers.iterator();
		while (iter.hasNext())
		{
			MPlayer mplayer = iter.next();
			if (!mplayer.attached())
			{
				String msg = Txt.parse("<rose>WARN: <i>Faction <h>%s <i>aka <h>%s <i>had unattached mplayer in index:", this.getName(), this.getId());
				Factions.get().log(msg);
				Factions.get().log(Factions.get().getGson().toJson(mplayer));
				iter.remove();
			}
		}
	}
	
	public List<MPlayer> getMPlayers()
	{
		this.checkMPlayerIndex();
		return new ArrayList<MPlayer>(this.mplayers);
	}
	
	public List<MPlayer> getMPlayersWhere(Predicate<? super MPlayer> predicate)
	{
		List<MPlayer> ret = this.getMPlayers();
		for (Iterator<MPlayer> it = ret.iterator(); it.hasNext();)
		{
			if ( ! predicate.apply(it.next())) it.remove();
		}
		return ret;
	}
	
	public List<MPlayer> getMPlayersWhereOnline(boolean online)
	{
		return this.getMPlayersWhere(online ? SenderColl.PREDICATE_ONLINE : SenderColl.PREDICATE_OFFLINE);
	}

	public List<MPlayer> getMPlayersWhereOnlineTo(Object senderObject)
	{
		return this.getMPlayersWhere(PredicateAnd.get(SenderColl.PREDICATE_ONLINE, PredicateVisibleTo.get(senderObject)));
	}
	
	public List<MPlayer> getMPlayersWhereRole(Rel role)
	{
		return this.getMPlayersWhere(PredicateRole.get(role));
	}
	
	public MPlayer getLeader()
	{
		List<MPlayer> ret = this.getMPlayersWhereRole(Rel.LEADER);
		if (ret.size() == 0) return null;
		return ret.get(0);
	}
	
	// -------------------------------------------- //
	// NEW LEADER
	// -------------------------------------------- //

	// Used when current leader is about to be removed from the faction; promotes new leader, or disbands faction if no other members left
	public void promoteNewLeader()
	{
		if ( ! this.isNormal()) return;
		if (this.getFlag(MFlag.getFlagPermanent()) && MConf.get().permanentFactionsDisableLeaderPromotion) return;

		MPlayer oldLeader = this.getLeader();

		// Get list of officers, or list of normal members if there are no officers
		List<MPlayer> replacements = this.getMPlayersWhereRole(Rel.OFFICER);
		if (replacements.isEmpty()) replacements = this.getMPlayersWhereRole(Rel.MEMBER);
		if (replacements.isEmpty())
		{
			this.attemptDisband(IdUtil.getConsole(), "since it has no members left.");
			return;
		}
		
		// Promote new faction leader
		if (oldLeader != null) oldLeader.setRole(Rel.MEMBER);
		MPlayer mplayer = replacements.get(0);
		mplayer.setRole(Rel.LEADER);
		
		// Inform
		String leaderName = mplayer.getName();
		this.msg("<i>Faction leader <h>%s<i> has been removed. %s<i> has been promoted as the new faction leader.", oldLeader == null ? "" : oldLeader.getName(), leaderName);
		Factions.get().log("Faction " + this.getName() + " (" + this.getId() + ") leader was removed. Replacement leader: " + leaderName);
	}
	
	public void attemptDisband(CommandSender sender, String reason)
	{
		// Faction leader is the only member; one-man faction
		if (this.getFlag(MFlag.getFlagPermanent())) return;
		
		// Run an event
		EventFactionsDisband eventFactionsDisband = new EventFactionsDisband(sender, this);
		eventFactionsDisband.run();
		if (eventFactionsDisband.isCancelled()) return;
		
		// No members left and faction isn't permanent, so disband it
		this.logDisband(reason);
		
		for (MPlayer mplayer : MPlayerColl.get().getAllOnline())
		{
			mplayer.msg("<i>The faction %s<i> was disbanded.", this.getName(mplayer));
		}
		
		this.detach();
	}
	
	private void logDisband(String reason)
	{
		if (!MConf.get().logFactionDisband) return;
		
		String message = String.format("The faction %s (%s) has been disbanded %s.", this.getName(), this.getId(), reason);
		Factions.get().log(message);
	}
	
	// -------------------------------------------- //
	// FACTION ONLINE STATE
	// -------------------------------------------- //

	public boolean isAllMPlayersOffline()
	{
		return this.getMPlayersWhereOnline(true).size() == 0;
	}
	
	public boolean isAnyMPlayersOnline()
	{
		return !this.isAllMPlayersOffline();
	}
	
	public boolean isFactionConsideredOffline()
	{
		return this.isAllMPlayersOffline();
	}
	
	public boolean isFactionConsideredOnline()
	{
		return !this.isFactionConsideredOffline();
	}
	
	public boolean isExplosionsAllowed()
	{
		boolean explosions = this.getFlag(MFlag.getFlagExplosions());
		boolean offlineexplosions = this.getFlag(MFlag.getFlagOfflineexplosions());

		if (explosions && offlineexplosions) return true;
		if ( ! explosions && ! offlineexplosions) return false;

		boolean online = this.isFactionConsideredOnline();
		
		return (online && explosions) || (!online && offlineexplosions);
	}
	
	// -------------------------------------------- //
	// MESSAGES
	// -------------------------------------------- //
	// These methods are simply proxied in from the Mixin.
	
	// CONVENIENCE SEND MESSAGE
	public boolean sendMessage(Object message)
	{
		return MixinMessage.get().messagePredicate(new FactionEqualsPredicate(this), message);
	}
	
	public boolean sendMessage(Object... messages)
	{
		return MixinMessage.get().messagePredicate(new FactionEqualsPredicate(this), messages);
	}
	
	public boolean sendMessage(Collection<Object> messages)
	{
		return MixinMessage.get().messagePredicate(new FactionEqualsPredicate(this), messages);
	}
	
	// CONVENIENCE MSG
	public boolean msg(String msg)
	{
		return MixinMessage.get().msgPredicate(new FactionEqualsPredicate(this), msg);
	}
	
	public boolean msg(String msg, Object... args)
	{
		return MixinMessage.get().msgPredicate(new FactionEqualsPredicate(this), msg, args);
	}
	
	public boolean msg(Collection<String> msgs)
	{
		return MixinMessage.get().msgPredicate(new FactionEqualsPredicate(this), msgs);
	}
	
}
