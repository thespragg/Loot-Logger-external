package com.lootloggerexternal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonSerializer;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.net.ssl.*;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.account.SessionManager;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.http.api.loottracker.GameItem;
import okhttp3.*;
import sun.net.www.http.HttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
		name = "Loot Logger External",
		tags = {"loot"},
		description = "Logs loot to an external server"
)
public class LootLoggerExternalPlugin extends Plugin
{
	/* LIST OF EVENTS
	 * List taken from Loot Tracker plugin
	 */

	// Activity/Event loot handling
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed [0-9]+ ([a-z]+) Treasure Trails?\\.");
	private static final int THEATRE_OF_BLOOD_REGION = 12867;

	// Herbiboar loot handling
	@VisibleForTesting
	static final String HERBIBOAR_LOOTED_MESSAGE = "You harvest herbs from the herbiboar, whereupon it escapes.";
	private static final String HERBIBOAR_EVENT = "Herbiboar";
	private static final Pattern HERBIBOAR_HERB_SACK_PATTERN = Pattern.compile(".+(Grimy .+?) herb.+");

	// Seed Pack loot handling
	private static final String SEEDPACK_EVENT = "Seed pack";

	// Hespori loot handling
	private static final String HESPORI_LOOTED_MESSAGE = "You have successfully cleared this patch for new crops.";
	private static final String HESPORI_EVENT = "Hespori";
	private static final int HESPORI_REGION = 5021;

	// Chest loot handling
	private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
	private static final Pattern LARRAN_LOOTED_PATTERN = Pattern.compile("You have opened Larran's (big|small) chest .*");
	private static final String STONE_CHEST_LOOTED_MESSAGE = "You steal some loot from the chest.";
	private static final String DORGESH_KAAN_CHEST_LOOTED_MESSAGE = "You find treasure inside!";
	private static final String GRUBBY_CHEST_LOOTED_MESSAGE = "You have opened the Grubby Chest";
	private static final Pattern HAM_CHEST_LOOTED_PATTERN = Pattern.compile("Your (?<key>[a-z]+) key breaks in the lock.*");
	private static final int HAM_STOREROOM_REGION = 10321;
	private static final Map<Integer, String> CHEST_EVENT_TYPES = new ImmutableMap.Builder<Integer, String>().
			put(5179, "Brimstone Chest").
			put(11573, "Crystal Chest").
			put(12093, "Larran's big chest").
			put(12127, "The Gauntlet").
			put(13113, "Larran's small chest").
			put(13151, "Elven Crystal Chest").
			put(5277, "Stone chest").
			put(10835, "Dorgesh-Kaan Chest").
			put(10834, "Dorgesh-Kaan Chest").
			put(7323, "Grubby Chest").
			build();

	// Shade chest loot handling
	private static final Pattern SHADE_CHEST_NO_KEY_PATTERN = Pattern.compile("You need a [a-z]+ key with a [a-z]+ trim to open this chest .*");
	private static final Map<Integer, String> SHADE_CHEST_OBJECTS = new ImmutableMap.Builder<Integer, String>().
			put(ObjectID.BRONZE_CHEST, "Bronze key red").
			put(ObjectID.BRONZE_CHEST_4112, "Bronze key brown").
			put(ObjectID.BRONZE_CHEST_4113, "Bronze key crimson").
			put(ObjectID.BRONZE_CHEST_4114, "Bronze key black").
			put(ObjectID.BRONZE_CHEST_4115, "Bronze key purple").
			put(ObjectID.STEEL_CHEST, "Steel key red").
			put(ObjectID.STEEL_CHEST_4117, "Steel key brown").
			put(ObjectID.STEEL_CHEST_4118, "Steel key crimson").
			put(ObjectID.STEEL_CHEST_4119, "Steel key black").
			put(ObjectID.STEEL_CHEST_4120, "Steel key purple").
			put(ObjectID.BLACK_CHEST, "Black key red").
			put(ObjectID.BLACK_CHEST_4122, "Black key brown").
			put(ObjectID.BLACK_CHEST_4123, "Black key crimson").
			put(ObjectID.BLACK_CHEST_4124, "Black key black").
			put(ObjectID.BLACK_CHEST_4125, "Black key purple").
			put(ObjectID.SILVER_CHEST, "Silver key red").
			put(ObjectID.SILVER_CHEST_4127, "Silver key brown").
			put(ObjectID.SILVER_CHEST_4128, "Silver key crimson").
			put(ObjectID.SILVER_CHEST_4129, "Silver key black").
			put(ObjectID.SILVER_CHEST_4130, "Silver key purple").
			build();

	// Hallow Sepulchre Coffin handling
	private static final String COFFIN_LOOTED_MESSAGE = "You push the coffin lid aside.";
	private static final String HALLOWED_SEPULCHRE_COFFIN_EVENT = "Coffin (Hallowed Sepulchre)";
	private static final Set<Integer> HALLOWED_SEPULCHRE_MAP_REGIONS = ImmutableSet.of(8797, 10077, 9308, 10074, 9050); // one map region per floor

	// Last man standing map regions
	private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(13658, 13659, 13914, 13915, 13916);

	private static final Pattern PICKPOCKET_REGEX = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	private static final String BIRDNEST_EVENT = "Bird nest";
	private static final Set<Integer> BIRDNEST_IDS = ImmutableSet.of(ItemID.BIRD_NEST, ItemID.BIRD_NEST_5071, ItemID.BIRD_NEST_5072, ItemID.BIRD_NEST_5073, ItemID.BIRD_NEST_5074, ItemID.BIRD_NEST_7413, ItemID.BIRD_NEST_13653, ItemID.BIRD_NEST_22798, ItemID.BIRD_NEST_22800);

	// Birdhouses
	private static final Pattern BIRDHOUSE_PATTERN = Pattern.compile("You dismantle and discard the trap, retrieving (?:(?:a|\\d{1,2}) nests?, )?10 dead birds, \\d{1,3} feathers and (\\d,?\\d{1,3}) Hunter XP\\.");
	private static final Map<Integer, String> BIRDHOUSE_XP_TO_TYPE = new ImmutableMap.Builder<Integer, String>().
			put(280, "Regular Bird House").
			put(420, "Oak Bird House").
			put(560, "Willow Bird House").
			put(700, "Teak Bird House").
			put(820, "Maple Bird House").
			put(960, "Mahogany Bird House").
			put(1020, "Yew Bird House").
			put(1140, "Magic Bird House").
			put(1200, "Redwood Bird House").
			build();

	private static final Multimap<String, String> PICKPOCKET_DISAMBIGUATION_MAP = ImmutableMultimap.of(
			"H.A.M. Member", "Man",
			"H.A.M. Member", "Woman"
	);

	private static final String CASKET_EVENT = "Casket";
	public static final MediaType JSON = MediaType.parse("application/json");
	private static final Gson GSON = RuneLiteAPI.GSON;
	private static final Set<Integer> EXCLUDED_IDS = ImmutableSet.of(526,530,532,534,536);

	@Inject
	private Client client;

	@Inject
	private LootLoggerExternalConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private LootManager lootManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private final OkHttpClient httpClient = getUnsafeOkHttpClient(); //new OkHttpClient();

	private List<String> excludedItems = new ArrayList<>();
	private List<String> excludedNPCs = new ArrayList<>();
	private List<String> excludedEvents = new ArrayList<>();

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("loottrackerexternal"))
		{
			excludedItems = Text.fromCSV(config.getExcludedItems());
			excludedNPCs = Text.fromCSV(config.getExcludedNPCs());
			excludedEvents = Text.fromCSV(config.getExcludedEvents());
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "", null);
		}
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived) throws IOException {
		final NPC npc = npcLootReceived.getNpc();
		final Collection<GameItem> items = npcLootReceived.getItems().stream().map(item -> new GameItem(item.getId(), item.getQuantity())).collect(Collectors.toList());
		final String name = npc.getName();
		final int combat = npc.getCombatLevel();
		final String username = client.getLocalPlayer().getName();

		for(GameItem item: items){
			if(EXCLUDED_IDS.contains(item.getId())) continue;
			String itemName = itemManager.getItemComposition(item.getId()).getName();

			DropData data = new DropData();
			data.ItemName = itemName;
			data.Username = username;
			data.EventName = npc.getName();
			data.ItemId = item.getId();
			data.ItemQuantity = item.getQty();

			String dataStr = GSON.toJson(data);
			Request request = new Request.Builder()
					.post(RequestBody.create(JSON, dataStr))
					.url("https://localhost:44370/api/bot/add")
					.build();

			httpClient.newCall(request).enqueue(new Callback() {
				@Override
				public void onFailure(Call call, IOException e) {
					log.info(e.getMessage());
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException {
					log.info("API call successful");
				}
			});
		}
	}

	//REMOVE ME IN PRODUCTION
	private static OkHttpClient getUnsafeOkHttpClient() {
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[] {
					new X509TrustManager() {
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return new java.security.cert.X509Certificate[]{};
						}
					}
			};

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
			builder.hostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			});

			OkHttpClient okHttpClient = builder
					.connectTimeout(15, TimeUnit.SECONDS)
					.writeTimeout(15, TimeUnit.SECONDS)
					.readTimeout(15, TimeUnit.SECONDS)
					.build();
			return okHttpClient;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Provides
	LootLoggerExternalConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootLoggerExternalConfig.class);
	}
}
