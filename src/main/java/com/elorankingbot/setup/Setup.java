package com.elorankingbot.commands.admin.settings;

import com.elorankingbot.command.annotations.AdminCommand;
import com.elorankingbot.command.annotations.GlobalCommand;
import com.elorankingbot.commands.SlashCommand;
import com.elorankingbot.model.Game;
import com.elorankingbot.model.Server;
import com.elorankingbot.service.Services;
import com.elorankingbot.timedtask.DurationParser;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandRequest;

import java.util.stream.Collectors;

// Server Setup
@AdminCommand
//@GlobalCommand
public class Setup extends SlashCommand {

	//public Setup(MessageInteractionEvent event, Services services) {
	public Setup(Guild guild, Services services) {
		super(guild, services);
		this.bot = services.bot;
	}

	public static String getSetupHelpMessage() {
		return "**BOT Setup**: Welcome\nBla bla bla\n
		`Ranking`: Collection of lobbies/queues. Scores and rating from within it's queue/lobbies, is stored.\n
		`Queue/Lobby`: (A channel) A contained matchmaking where players who join are matched-up together\n
		`Rating Roles`: Give users roles in the guild based on their rating.\n
		`Rating Algorithms`: Elo, TrueSkill, Glicko2";
	}

    //public static Mono<Channel>getFirstChannel() {
	//	return guild.getChannels()
	//	.transform(OrderUtil::orderGuildChannels)
	//	.next();
    //}
	//
// TODO: I  guess
    public void sendPermissionRoleSetupMessage() {
		//bot.sendMSGFirstChannel("Permission Role Setup", "Please select a role as admin role", guild);
		var firstMessage = getSetupHelpMessage();
		firstMessage.append("\n\n**Permission Role Setup**: Admin\nPlease create or select a role for the Admin role");

		bot.sendMSGFirstChannel(EmbedBuilder.createSetupEmbed(firstMessage))

		//bot.sendMSGFirstChannel(EmbedBuilder.createSetupEmbed("**Permission Role Setup**: Mod\nPlease create or select a role for the Admin role"))
	}

//// [1] Permission Role Setup
// Embed: Set a role as admin role. You can create a new one by pressing the button or select an existing role in the select menu

// 1. Set the admin role
//Buttons: "Create Admin Role", => Modal: Role Name: "admin	"
//Select Menu: with a list of pre-existing roles in the server.

// 2. Set the mod role
//Buttons: "Create Mod Role", 
//Select Menu: with a list of pre-existing roles in the server.

// Embed: Admin Role: <role>, Moderator Role: <role>


    // Dont need it, since we're not deploying this as a command.
	/*public static ApplicationCommandRequest getRequest() {
		return ApplicationCommandRequest.builder()
				.name(Settings.class.getSimpleName().toLowerCase())
				.description(getShortDescription())
				.defaultPermission(true)
				.build();
	}

	public static String getShortDescription() {
		return "Open the settings menu for the server.";
	}

	public static String getLongDescription() {
		return getShortDescription();
	}*/

	protected void execute() {
		sendPermissionRoleSetupMessage();
		/*event.reply("Welcome to the settings menu.")
				.withEmbeds(serverSettingsEmbed(server))
				.withComponents(SelectServerVariableOrGame.menu(server), ActionRow.of(Exit.button())).subscribe();*/
	}
	/*
		private void processQueueSelected() {
		Game game = server.getGame(event.getValues().get(0).split(",")[0]);
		MatchFinderQueue queue = game.getQueue(event.getValues().get(0).split(",")[1]);
		event.getMessage().get().edit()
				.withEmbeds(queueSettingsEmbed(queue))
				.withComponents(SelectQueueVariable.menu(queue), ActionRow.of(Exit.button(), EscapeToGameMenu.button(game))).subscribe();
		acknowledgeEvent();
	} */

	static EmbedCreateSpec serverSettingsEmbed(Server server) {
		String gamesAsString = server.getGames().stream().map(Game::getName)
				.collect(Collectors.joining(", "));
		if (gamesAsString.equals("")) gamesAsString = "No rankings";
		String autoLeaveQueuesAsString = server.getAutoLeaveQueuesAfter() == Server.NEVER ? "never"
				: DurationParser.minutesToString(server.getAutoLeaveQueuesAfter());
		return EmbedCreateSpec.builder()
				.title("Server settings and rankings")
				.addField(EmbedCreateFields.Field.of(AutoLeaveModal.variableName, autoLeaveQueuesAsString, false))
				.addField(EmbedCreateFields.Field.of("Rankings", gamesAsString, false))
				.build();
	}

	static EmbedCreateSpec serverSetupEmbed(Server server) {
		return EmbedCreateSpec.builder()
				.title("Server Setup")
				.addField("Explain", getSetupHelpMessage(), false)
				.build();
	}

		static EmbedCreateSpec serverSetupAdminRoleEmbed(Server server) {
		return EmbedCreateSpec.builder()
				.title("Server Setup")
				.addField("**Permission Role Setup**: Admin", "Please create or select a role for the Admin role", false)
				.build();
	}
}
