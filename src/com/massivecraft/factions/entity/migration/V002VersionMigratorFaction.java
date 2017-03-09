package com.massivecraft.factions.entity.migration;

import com.massivecraft.factions.entity.Faction;
import com.massivecraft.massivecore.store.migration.VersionMigratorField;
import com.massivecraft.massivecore.store.migration.VersionMigratorRootAbstract;
import com.massivecraft.massivecore.xlib.gson.JsonElement;

public class V002VersionMigratorFaction extends VersionMigratorRootAbstract
{
	// -------------------------------------------- //
	// INSTANCE & CONSTRUCT
	// -------------------------------------------- //

	private static V002VersionMigratorFaction i = new V002VersionMigratorFaction();
	public static V002VersionMigratorFaction get() { return i; }
	public V002VersionMigratorFaction()
	{
		super(Faction.class);

		// Change description
		this.addInnerMigrator(new VersionMigratorField("description")
		{
			@Override public Object migrateInner(JsonElement element, boolean upgrade)
			{
				if (upgrade)
				{
					String desc = element.isJsonNull() ? "" : element.getAsString();
					return "ABC".concat(desc);
				}
				else
				{
					String desc = element.isJsonNull() ? "ABC" : element.getAsString();
					return desc.substring(3);
				}
			}
		});
	}


}
