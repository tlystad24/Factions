package com.massivecraft.factions.cmd;

import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsParticipator;
import com.massivecraft.factions.Perm;
import com.massivecraft.massivecore.MassiveCore;
import com.massivecraft.massivecore.MassiveException;
import com.massivecraft.massivecore.command.type.Type;
import com.massivecraft.massivecore.command.type.TypeNullable;
import com.massivecraft.massivecore.command.type.primitive.TypeDouble;
import com.massivecraft.massivecore.util.Txt;

public abstract class CmdFactionsPowerBoostAbstract extends FactionsCommand
{
	// -------------------------------------------- //
	// CONSTRUCT
	// -------------------------------------------- //
	
	protected CmdFactionsPowerBoostAbstract(Type<? extends FactionsParticipator> type, String name)
	{
		// Parameters
		this.addParameter(type, name);
		this.addParameter(TypeNullable.get(TypeDouble.get(), MassiveCore.REMOVE), "amount", "show");
	}
	
	// -------------------------------------------- //
	// OVERRIDE
	// -------------------------------------------- //
	
	@Override
	public void perform() throws MassiveException
	{
		// Parameters
		FactionsParticipator fp = this.readArg();
		Double powerBoost = this.readArg(fp.getPowerBoost());
		
		// Try set the powerBoost
		boolean update = this.trySet(fp, powerBoost);
		
		// Inform
		this.informPowerBoost(fp, powerBoost, update);
	}
	
	private boolean trySet(FactionsParticipator fp, Double powerBoost) throws MassiveException
	{
		// Trying to set?
		if (!this.argIsSet(1)) return false;
		
		// Check set permissions
		if (!Perm.POWERBOOST_SET.has(sender, true)) throw new MassiveException();
		
		// Set
		fp.setPowerBoost(powerBoost);
		
		// Return
		return true;
	}
	
	private void informPowerBoost(FactionsParticipator fp, Double powerBoost, boolean update)
	{
		// Prepare
		String fpDescribe = fp.describeTo(msender, true);
		powerBoost = powerBoost == null ? fp.getPowerBoost() : powerBoost;
		String powerDescription = Txt.parse(Double.compare(powerBoost, 0D) >= 0 ? "<g>bonus" : "<b>penalty");
		String when = update ? "now " : "";
		String verb = fp.equals(msender) ? "have" : "has";
		
		// Create message
		String messagePlayer = Txt.parse("<i>%s <i>%s%s a power %s <i>of <h>%.2f<i> to min and max power levels.", fpDescribe, when, verb, powerDescription, powerBoost);
		String messageLog = Txt.parse("%s %s set the power %s<i> for %s<i> to <h>%.2f<i>.", msender.getName(), verb, powerDescription, fp.getName(), powerBoost);
		
		// Inform
		msender.message(messagePlayer);
		if (update) Factions.get().log(messageLog);
	}
	
}
