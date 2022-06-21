package com.elorankingbot.backend.commands.admin;

import com.elorankingbot.backend.command.AdminCommand;
import com.elorankingbot.backend.commands.SlashCommand;
import com.elorankingbot.backend.commands.mod.ForceWin;
import com.elorankingbot.backend.commands.player.Join;
import com.elorankingbot.backend.commands.player.Leave;
import com.elorankingbot.backend.commands.player.PlayerInfo;
import com.elorankingbot.backend.model.Game;
import com.elorankingbot.backend.model.MatchFinderQueue;
import com.elorankingbot.backend.model.Server;
import com.elorankingbot.backend.service.Services;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData;

import static com.elorankingbot.backend.model.MatchFinderQueue.QueueType.*;
import static com.elorankingbot.backend.service.DiscordBotService.illegalNameMessage;
import static com.elorankingbot.backend.service.DiscordBotService.isLegalDiscordName;
import static discord4j.core.object.command.ApplicationCommandOption.Type.INTEGER;
import static discord4j.core.object.command.ApplicationCommandOption.Type.STRING;

@AdminCommand
public class AddQueue extends SlashCommand {

	public AddQueue(ChatInputInteractionEvent event, Services services) {
		super(event, services);
	}

	public static ApplicationCommandRequest getRequest(Server server) {
		ImmutableApplicationCommandOptionData.Builder gameOptionBuilder = ApplicationCommandOptionData.builder()
				.name("ranking").description("Which ranking to add a queue to?")
				.type(STRING.getValue())
				.required(true);
		server.getGameNameToGame().keySet().forEach(nameOfGame -> gameOptionBuilder
				.addChoice(ApplicationCommandOptionChoiceData.builder()
						.name(nameOfGame).value(nameOfGame).build()));

		return ApplicationCommandRequest.builder()
				.name(AddQueue.class.getSimpleName().toLowerCase())
				.description("Add a queue to a ranking")
				.defaultPermission(false)
				.addOption(gameOptionBuilder.build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("playersperteam").description("How many players per team? Set to 1 if not a team game")
						.type(INTEGER.getValue())
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("numberofteams").description("How many teams per match? " +
								"Use 2 for (team) versus, or 3 or higher for free-for-all")
						.type(INTEGER.getValue())
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("nameofqueue").description("What do you call this queue? " +
								"Only relevant if there is more than one queue for this ranking")
						.type(STRING.getValue())
						.required(true).build())
				.addOption(ApplicationCommandOptionData.builder()
						.name("queuetype").description("Currently the bot only supports solo queue")//"Only if a team queue: " +
						//"is this a solo queue, or a premade team only queue?")//, or a mixed queue
						.type(STRING.getValue())
						.addChoice(ApplicationCommandOptionChoiceData.builder()
								.name("solo queue").value("solo").build())
						//.addChoice(ApplicationCommandOptionChoiceData.builder()
						//		.name("premade only").value("premade").build())
						//.addChoice(ApplicationCommandOptionChoiceData.builder()
						//		.name("mixed queue").value("mixed").build())
						.required(true).build())// false
				//.addOption(ApplicationCommandOptionData.builder()
				//		.name("maxpremade").description("Only if a mixed queue: what is the maximum premade team size?")
				//		.type(INTEGER.getValue())
				//		.required(false).build())
				.build();
	}

	public static String getShortDescription() {
		return "Add a queue to a ranking.";
	}

	public static String getLongDescription() {
		return getShortDescription() + "\n" +
				"`Required:` `playersperteam` The number of players on a team for the queue.\n" +
				"`Required:` `numberofteams` The number of teams in a match.\n" +
				"`Required:` `nameofqueue` The name of the new queue.\n" +
				"`Required:` `queuetype` Currently there is only solo queue.\n" +
				"For more information on queues, see `/help:` `Concept: Rankings and Queues`.";
	}

	protected void execute() {
		int playersPerTeam = (int) event.getOption("playersperteam").get().getValue().get().asLong();
		if (playersPerTeam < 1) {
			event.reply("Cannot create a queue with less than 1 player per team").subscribe();
			return;
		}
		int numberOfTeams = (int) event.getOption("numberofteams").get().getValue().get().asLong();
		if (numberOfTeams < 2) {
			event.reply("Cannot create a queue with less than 2 teams per match").subscribe();
			return;
		}
		Game game = server.getGame(event.getOption("ranking").get().getValue().get().asString());
		String nameOfQueue = event.getOption("nameofqueue").get().getValue().get().asString();
		if (!isLegalDiscordName(nameOfQueue)) {
			event.reply(illegalNameMessage()).subscribe();
			return;
		}
		if (nameOfQueue.length() > 32) {
			event.reply("Queue name cannot exceed 32 characters").subscribe();
			return;
		}
		if (game.getQueueNameToQueue().containsKey(nameOfQueue.toLowerCase())) {
			event.reply("A queue of that name already exists for that ranking. " +
					"Queue names must be unique for each ranking").subscribe();
			return;
		}
		MatchFinderQueue.QueueType queueType;
		int maxPremadeSize = 0;
		if (playersPerTeam == 1) {
			queueType = SOLO;
		} else {
			if (event.getOption("queuetype").isEmpty()) {
				event.reply("Please select an option for solo queue/premade queue/mixed queue " +
						"when creating a team queue").subscribe();
				return;
			}
			String queueTypeOption = event.getOption("queuetype").get().getValue().get().asString();
			if (queueTypeOption.equals("solo")) {
				queueType = SOLO;
			} else if (queueTypeOption.equals("premade")) {
				queueType = PREMADE;
			} else {
				if (event.getOption("maxpremade").isEmpty()) {
					event.reply("Please select a maximum premade team size when creating a mixed " +
							"solo and premade queue").subscribe();
					return;
				} else {
					queueType = MIXED;
					maxPremadeSize = (int) event.getOption("maxpremade").get().getValue().get().asLong();
				}
			}
		}

		MatchFinderQueue queue = new MatchFinderQueue(game, nameOfQueue, numberOfTeams, playersPerTeam,
				queueType, maxPremadeSize);
		game.addQueue(queue);
		// TODO log
		// TODO inputs nach quatsch filtern

		bot.deployCommand(server, Join.getRequest(server)).subscribe();
		bot.deployCommand(server, Leave.getRequest()).subscribe();
		bot.deployCommand(server, PlayerInfo.getRequest()).subscribe();
		bot.deployCommand(server, Edit.getRequest(server)).subscribe(commandData ->
				bot.setPermissionsForAdminCommand(server, Edit.class.getSimpleName()));
		bot.deployCommand(server, DeleteQueue.getRequest(server)).subscribe();
		bot.deployCommand(server, ForceWin.getRequest(server)).subscribe();
		dbService.saveServer(server);

		event.reply(String.format("Queue %s for ranking %s has been created. Relevant commands have been deployed or updated. " +
						"This may take a few minutes to update on the server.",// TODO /join genauer bezeichnen
				queue.getName(), game.getName())).subscribe();
	}
}
