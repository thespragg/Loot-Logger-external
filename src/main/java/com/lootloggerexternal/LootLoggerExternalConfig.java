package com.lootloggerexternal;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("loottrackerexternal")
public interface LootLoggerExternalConfig extends Config
{
	@ConfigSection(
			name = "Setup",
			description = "General setup options",
			position = -2,
			closedByDefault = false
	)
	String setup = "setup";

	@ConfigItem(
			keyName = "endpointUrl",
			name = "Endpoint URL",
			description = "API endpoint to send drop data. Please read the github page for info of what the endpoints signature should be.",
			section = setup
	)
	default String getEndpointURL()
	{
		return "";
	}

	@ConfigSection(
			name = "Excluded Entries",
			description = "Options to specify what bosses or items to include",
			position = -1,
			closedByDefault = true
	)
	String included = "included";

	@ConfigItem(
		keyName = "excludedItems",
		name = "Excluded Items",
		description = "Items to exclude",
			section = included
	)
	default String getExcludedItems()
	{
		return "";
	}

	@ConfigItem(
			keyName = "excludedNPCs",
			name = "Excluded NPC's",
			description = "NPCs to exclude",
			section = included
	)
	default String getExcludedNPCs()
	{
		return "";
	}

	@ConfigItem(
			keyName = "excludedEvents",
			name = "Excluded Events",
			description = "Events to exclude (e.g barrows)",
			section = included
	)
	default String getExcludedEvents()
	{
		return "";
	}
}
