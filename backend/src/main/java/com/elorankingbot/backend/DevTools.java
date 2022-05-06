package com.elorankingbot.backend;

import com.elorankingbot.backend.command.CommandClassScanner;
import com.elorankingbot.backend.commands.admin.CreateRanking;
import com.elorankingbot.backend.commands.admin.SetPermissions;
import com.elorankingbot.backend.configuration.ApplicationPropertiesLoader;
import com.elorankingbot.backend.dao.*;
import com.elorankingbot.backend.model.Server;
import com.elorankingbot.backend.service.DBService;
import com.elorankingbot.backend.service.DiscordBotService;
import com.elorankingbot.backend.service.Services;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DevTools {

	private final DBService dbService;
	private final DiscordBotService bot;
	private final GatewayDiscordClient client;
	private final CommandClassScanner commandClassScanner;
	private final ApplicationPropertiesLoader props;
	private final PlayerDao playerDao;
	private final MatchDao matchDao;
	private final MatchResultDao matchResultDao;
	private final TimeSlotDao timeSlotDao;
	private final ServerDao serverDao;

	public DevTools(Services services,
					PlayerDao playerDao, MatchDao matchDao, MatchResultDao matchResultDao, TimeSlotDao timeSlotDao, ServerDao serverDao) {
		this.dbService = services.dbService;
		this.bot = services.bot;
		this.client = services.client;
		this.commandClassScanner = services.commandClassScanner;
		this.playerDao = playerDao;
		this.matchDao = matchDao;
		this.matchResultDao = matchResultDao;
		this.props = services.props;
		this.timeSlotDao = timeSlotDao;
		this.serverDao = serverDao;

		if (props.isDeleteDataOnStartup()) {
			deleteAllData();
			deployInitialCommands();
		}
		if (props.isDoUpdateGuildCommands()) updateGuildCommands();
	}

	private void updateGuildCommands() {
		log.warn("updating guild commands...");
		dbService.findAllServers().forEach(
				server -> {
					try {
						//dbService.saveServer(server);

						//bot.deleteCommand(server, "setrole").subscribe();
						//bot.deployCommand(server, SetPermissions.getRequest()).subscribe();
						//server.getGames().forEach(game -> game.setRequiredRatingToRankId(new HashMap<>()));
						//dbService.saveServer(server);
						//deleteAllChannels(server, "vs");
						//bot.deployCommand(server, Help.getRequest()).block();
						//bot.deployCommand(server, AddRank.getRequest(server)).block();
						//bot.setPermissionsForAdminCommand(server, AddRank.class.getSimpleName().toLowerCase());
						//bot.deployCommand(server, DeleteRanks.getRequest(server)).block();
						//bot.setPermissionsForAdminCommand(server, DeleteRanks.class.getSimpleName().toLowerCase());
						//bot.setPermissionsForAdminCommand(server, Reset.class.getSimpleName().toLowerCase());
						//bot.deployCommand(server, Help.getRequest(commandClassScanner)).block();

						//bot.setAdminPermissionToAdminCommand(server, "edit");
						//Role adminRole = client.getRoleById(Snowflake.of(game.getGuildId()), Snowflake.of(game.getAdminRoleId())).block();
						//Role modRole = client.getRoleById(Snowflake.of(game.getGuildId()), Snowflake.of(game.getModRoleId())).block();
						//bot.setDiscordCommandPermissions(game.getGuildId(), "ban", adminRole, modRole);
					} catch (Exception e) {
						log.error(e.getMessage());
					}
				}
		);
	}

	private void deployInitialCommands() {
		log.warn("Deploying initial guild commands...");
		long entenwieseId = props.getEntenwieseId();
		Server entenwieseServer = new Server(entenwieseId);
		dbService.saveServer(entenwieseServer);

		bot.deleteAllGuildCommands(entenwieseId).blockLast();
		bot.deployCommand(entenwieseServer, SetPermissions.getRequest()).block();
		bot.setCommandPermissionForRole(entenwieseServer, "setrole", entenwieseId);
		bot.deployCommand(entenwieseServer, CreateRanking.getRequest()).subscribe();
	}

	private void deleteAllData() {
		if (props.getSpringDataMongodbDatabase().equals("deploy")) {
			throw new RuntimeException("deleteAllData is being called on deploy database");
		}

		log.warn(String.format("Deleting all data on %s...", props.getSpringDataMongodbDatabase()));
		//challengeDao.deleteAll();
		matchDao.deleteAll();
		matchResultDao.deleteAll();
		playerDao.deleteAll();
		timeSlotDao.deleteAll();
		serverDao.deleteAll();
	}

	private void deleteAllChannels(Server server, String name) {
		client.getGuildById(Snowflake.of(server.getGuildId())).block().getChannels()
				.doOnNext(System.out::println)
				.filter(channel -> channel.getName().contains(name))
				.subscribe(channel -> channel.delete().subscribe());
	}
}
