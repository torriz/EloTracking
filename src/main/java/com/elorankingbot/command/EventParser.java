package com.elorankingbot.command;

import com.elorankingbot.commands.*;
import com.elorankingbot.logging.ExceptionHandler;
import com.elorankingbot.model.Server;
import com.elorankingbot.service.DBService;
import com.elorankingbot.service.Services;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.role.RoleDeleteEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Hooks;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@CommonsLog
@Component
public class EventParser {

	private final Services services;
	private final DBService dbService;
	private final ExceptionHandler exceptionHandler;
	private final CommandClassScanner commandClassScanner;

	public EventParser(Services services, CommandClassScanner commandClassScanner) {
		this.services = services;
		this.dbService = services.dbService;
		this.exceptionHandler = services.exceptionHandler;
		this.commandClassScanner = commandClassScanner;
		GatewayDiscordClient client = services.client;

		client.on(ReadyEvent.class)
				.subscribe(event -> {
					User self = event.getSelf();
					log.info(String.format("Logged in as %s", self.getTag()));
					String activityMessage = services.props.getActivityMessage();
					client.updatePresence(ClientPresence.of(Status.ONLINE, ClientActivity.playing(activityMessage))).subscribe();
				});
		// On joining a new guild, start interactive setup process.
		client.on(GuildCreateEvent.class)
				.subscribe()
// Send a message starting the setup process. This message stays the entire time, as it contains explainations for Rating Roles, algorithm options etc.

	// Ranking: Collection of lobbies/queues. Scores and rating from within it's queue/lobbies, is stored.
	// Queue/Lobby: (A channel) A contained matchmaking where players who join are matched-up together

	// Rating Roles: Give users roles in the guild based on their rating.
	// Rating Algorithms: Elo, TrueSkill, Glicko2
	

//// [1] Permission Role Setup
// Embed: Set a role as admin role. You can create a new one by pressing the button or select an existing role in the select menu

// 1. Set the admin role
//Buttons: "Create Admin Role", => Modal: Role Name: "admin	"
//Select Menu: with a list of pre-existing roles in the server.

// 2. Set the mod role
//Buttons: "Create Mod Role", 
//Select Menu: with a list of pre-existing roles in the server.

// Embed: Admin Role: <role>, Moderator Role: <role>

// [2] Ranking and Queue Setup
// Create a new "ranking" (TODO: rename?), where scores and rating for lobbies/queues within it, is stored.
// 1. Embed: msg explaining ^, that it will create two channels and a few categories
//Button: "Create new ranking".
// --deploys commands--
// 2. Modal: Rating Alg: "Elo", Initial rating: "1200"
// Embed: confirm name, Rating Alg: "Elo", Initial rating: "1200", Use rank rating roles: "Yes, No"
// Buttons: "Rating Alg" => Modal, "Initial rating" => Modal, "Toggle Rating Roles (red/green button states)", "Toggle Allow Draws (red/green)" "Add Rank Roles" => Modal: 


// [3] Create a queue/lobby within your "ranking"
// 1. Embed: msg
// Button: "Create new queue/lobby"

// 2. Embed: 
// Create a queue/lobby channel and add join queue menu?

// Select Menu: <list of rankings created in guild>

// 3. Modal: Players Per Team: "1", Number Of Teams: "2", Name Of Lobby/Queue: "lobby1", Queue Type: "solo queue"
// --deploys commands--

// 4. Queue/Lobby settings menu
// Embed: 
// Active Maps: <maps from Add Map/selected in select menu>
// Button: "Add Map"
// Select Menu: <Maps from server collection>

// Button: "Save changes", "Abort"




		client.on(ChatInputInteractionEvent.class)
				.subscribe(this::createAndExecuteSlashCommand);

		client.on(ButtonInteractionEvent.class)
				.subscribe(this::processButtonInteractionEvent);

		client.on(SelectMenuInteractionEvent.class)
				.subscribe(this::processSelectMenuInteractionEvent);

		client.on(ModalSubmitInteractionEvent.class)
				.subscribe(this::processModalSubmitInteractionEvent);

		client.on(MessageInteractionEvent.class)
				.subscribe(this::processMessageInteractionEvent);

		client.on(RoleDeleteEvent.class)
				.subscribe(event -> {
					Server server = dbService.getOrCreateServer(event.getGuildId().asLong());
					if (server.getAdminRoleId() == event.getRoleId().asLong()) {
						server.setAdminRoleId(0L);
						dbService.saveServer(server);
					}
				});

		client.on(Event.class).subscribe(event -> log.trace(event.getClass().getSimpleName()));

		Hooks.onErrorDropped(throwable -> exceptionHandler.handleException(throwable, "Dropped Exception"));
	}

	private BiFunction<String, Boolean, Consumer<Throwable>> commandFailedCallbackFactory(long guildId) {
		return (commandName, isDeploy) -> throwable -> log.error(String.format("failed to %s command %s on %s",
				isDeploy ? "deploy" : "delete", commandName, guildId));
	}

	@Transactional
	void createAndExecuteSlashCommand(ChatInputInteractionEvent event) {
		try {
			Command command = createSlashCommand(event);
			command.doExecute();
		} catch (Exception e) {
			exceptionHandler.handleUnspecifiedCommandException(e, event, event.getCommandName());
		}
	}

	private SlashCommand createSlashCommand(ChatInputInteractionEvent event) throws Exception {
		String commandFullClassName = commandClassScanner.getFullClassName(event.getCommandName());
		if (commandFullClassName == null) throw new RuntimeException("Unknown Command");
		return (SlashCommand) Class.forName(commandFullClassName)
				.getConstructor(ChatInputInteractionEvent.class, Services.class)
				.newInstance(event, services);
	}

	@Transactional
	void processButtonInteractionEvent(ButtonInteractionEvent event) {
		try {
			Command command = createButtonCommand(event);
			command.doExecute();
		} catch (Exception e) {
			exceptionHandler.handleUnspecifiedCommandException(e, event, event.getCustomId().split(":")[0]);
		}
	}

	private ButtonCommand createButtonCommand(ButtonInteractionEvent event) throws Exception {
		String commandFullClassName = commandClassScanner.getFullClassName(event.getCustomId().split(":")[0]);
		if (commandFullClassName == null) throw new RuntimeException("Unknown Command");
		return (ButtonCommand) Class.forName(commandFullClassName)
				.getConstructor(ButtonInteractionEvent.class, Services.class)
				.newInstance(event, services);
	}

	@Transactional
	void processSelectMenuInteractionEvent(SelectMenuInteractionEvent event) {
		try {
			Command command = createSelectMenuCommand(event);
			command.doExecute();
		} catch (Exception e) {
			exceptionHandler.handleUnspecifiedCommandException(e, event, event.getCustomId().split(":")[0]);
		}
	}

	private SelectMenuCommand createSelectMenuCommand(SelectMenuInteractionEvent event) throws Exception {
		String commandFullClassName = commandClassScanner.getFullClassName(event.getCustomId().split(":")[0]);
		if (commandFullClassName == null) throw new RuntimeException("Unknown Command");
		return (SelectMenuCommand) Class.forName(commandFullClassName)
				.getConstructor(SelectMenuInteractionEvent.class, Services.class)
				.newInstance(event, services);
	}

	@Transactional
	void processMessageInteractionEvent(MessageInteractionEvent event) {
		try {
			Command command = createMessageCommand(event);
			command.doExecute();
		} catch (Exception e) {
			exceptionHandler.handleUnspecifiedCommandException(e, event, event.getCommandName().replace(" ", "").toLowerCase());
		}
	}

	private MessageCommand createMessageCommand(MessageInteractionEvent event) throws Exception {
		String commandFullClassName = commandClassScanner.getFullClassName(event.getCommandName().replace(" ", "").toLowerCase());
		if (commandFullClassName == null) throw new RuntimeException("Unknown Command");
		return (MessageCommand) Class.forName(commandFullClassName)
				.getConstructor(MessageInteractionEvent.class, Services.class)
				.newInstance(event, services);
	}

	@Transactional
	void processModalSubmitInteractionEvent(ModalSubmitInteractionEvent event) {
		try {
			Command command = createModalSubmitCommand(event);
			command.doExecute();
		} catch (Exception e) {
			exceptionHandler.handleUnspecifiedCommandException(e, event, event.getCustomId().split(":")[0]);
		}
	}

	private ModalSubmitCommand createModalSubmitCommand(ModalSubmitInteractionEvent event) throws Exception {
		String commandFullClassName = commandClassScanner.getFullClassName(event.getCustomId().split(":")[0]);
		if (commandFullClassName == null) throw new RuntimeException("Unknown Command");
		return (ModalSubmitCommand) Class.forName(commandFullClassName)
				.getConstructor(ModalSubmitInteractionEvent.class, Services.class)
				.newInstance(event, services);
	}
}
