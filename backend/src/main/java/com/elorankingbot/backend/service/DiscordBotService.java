package com.elorankingbot.backend.service;

import com.elorankingbot.backend.commands.admin.Setup;
import com.elorankingbot.backend.dto.PlayerInRankingsDto;
import com.elorankingbot.backend.model.ChallengeModel;
import com.elorankingbot.backend.model.Game;
import com.elorankingbot.backend.model.Match;
import com.elorankingbot.backend.model.Player;
import com.elorankingbot.backend.timedtask.TimedTaskQueue;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandPermissionsData;
import discord4j.discordjson.json.ApplicationCommandPermissionsRequest;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.service.ApplicationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

import static com.elorankingbot.backend.service.EloRankingService.formatRating;

@Slf4j
@Component
public class DiscordBotService {

	@Getter
	private final GatewayDiscordClient client;
	private final EloRankingService service;
	private final TimedTaskQueue queue;
	private final ApplicationService applicationService;
	private PrivateChannel ownerPrivateChannel;
	private final long botId;


	public DiscordBotService(GatewayDiscordClient gatewayDiscordClient, EloRankingService service, TimedTaskQueue queue) {
		this.client = gatewayDiscordClient;
		this.service = service;
		this.queue = queue;
		this.botId = client.getSelfId().asLong();
		applicationService = client.getRestClient().getApplicationService();
	}

	@PostConstruct
	public void initAdminDm() {
		long ownerId = Long.valueOf(service.getPropertiesLoader().getOwnerId());
		User owner = client.getUserById(Snowflake.of(ownerId)).block();
		this.ownerPrivateChannel = owner.getPrivateChannel().block();
		sendToOwner("I am logged in and ready");
	}

	public void sendToOwner(String text) {
		if (text == null) text = "null";
		if (text.equals("")) text = "empty String";
		ownerPrivateChannel.createMessage(text).subscribe();
	}

	public Mono<PrivateChannel> getPrivateChannelByUserId(long userId) {
		return client.getUserById(Snowflake.of(userId)).flatMap(User::getPrivateChannel);
	}

	public Mono<Message> sendToUser(long userId, String text) {
		return client.getUserById(Snowflake.of(userId)).block()
				.getPrivateChannel().block()
				.createMessage(text);
	}

	public Mono<Message> sendToUser(long userId, MessageCreateSpec spec) {
		return client.getUserById(Snowflake.of(userId)).block()
				.getPrivateChannel().block()
				.createMessage(spec);
	}

	public String getPlayerName(long playerId) {
		return client.getUserById(Snowflake.of(playerId)).block().getTag();
	}

	public Mono<Message> getMessageById(long messageId, long channelId) {
		return client.getMessageById(Snowflake.of(channelId), Snowflake.of(messageId));
	}

	public Mono<Guild> getGuildById(long guildId) {
		return client.getGuildById(Snowflake.of(guildId));
	}

	public void postToResultChannel(Game game, Match match) {
		TextChannel resultChannel;
		try {
			resultChannel = (TextChannel) client.getChannelById(Snowflake.of(game.getResultChannelId())).block();
		} catch (ClientException e) {
			resultChannel = Setup.createResultChannel(getGuildById(game.getGuildId()).block(), game);
			game.setResultChannelId(resultChannel.getId().asLong());
			service.saveGame(game);
		}
		resultChannel.createMessage(String.format("%s (%s) %s %s (%s)",// TODO aenderung ausformulieren
						match.getWinnerTag(client), formatRating(match.getWinnerNewRating()),
						match.isDraw() ? "drew" : "defeated",
						match.getLoserTag(client), formatRating(match.getLoserNewRating())))
				.subscribe();
	}

	public void updateLeaderboard(Game game) {
		Message leaderboardMessage;
		try {
			leaderboardMessage = getMessageById(game.getLeaderboardMessageId(), game.getLeaderboardChannelId()).block();
		} catch (ClientException e) {
			sendToOwner("exception in updateLeaderBoard");
			e.printStackTrace();

			Setup.createLeaderboardChannelAndMessage(getGuildById(game.getGuildId()).block(), game);
			service.saveGame(game);
			leaderboardMessage = getMessageById(game.getLeaderboardMessageId(), game.getLeaderboardChannelId()).block();
		}

		List<PlayerInRankingsDto> playerList = service.getRankings(game.getGuildId());
		int numPlayers = playerList.size();
		if (numPlayers > game.getLeaderboardLength())
			playerList = playerList.subList(0, game.getLeaderboardLength());
		String rankString = "";
		String nameString = "";
		String ratingString = "";
		for (int i = 0; i < playerList.size(); i++) {
			PlayerInRankingsDto player = playerList.get(i);
			rankString += String.format("  %s\n", i + 1);
			nameString += String.format("  %s\n", player.getName());
			ratingString += String.format("  %s\n", formatRating(player.getRating()));
		}
		if (rankString.equals("")) {
			rankString = "no matches";
			nameString = "played";
			ratingString = "so far";
		}

		leaderboardMessage.edit().withContent("\n").withEmbeds(EmbedCreateSpec.builder()
				.title(game.getName() + " Rankings")
				.addField(EmbedCreateFields.Field.of(
						"Rank",
						rankString,
						true))
				.addField(EmbedCreateFields.Field.of(
						"Name",
						nameString,
						true))
				.addField(EmbedCreateFields.Field.of(
						"Rating",
						ratingString,
						true))
				.footer(String.format("%s players total", numPlayers), null)
				.build()).subscribe();
	}

	public Mono<Message> getChallengerMessage(ChallengeModel challenge) {// TODO schauen wo das noch uber client gemacht wird
		return client.getMessageById(Snowflake.of(challenge.getChallengerChannelId()),
				Snowflake.of(challenge.getChallengerMessageId()));
	}

	public Mono<Message> getAcceptorMessage(ChallengeModel challenge) {
		return client.getMessageById(Snowflake.of(challenge.getAcceptorChannelId()),
				Snowflake.of(challenge.getAcceptorMessageId()));
	}

	// Commands
	public Mono<ApplicationCommandData> deployCommand(long guildId, ApplicationCommandRequest request) {
		return applicationService.createGuildApplicationCommand(botId, guildId, request)
				.doOnNext(commandData -> log.debug(String.format("deployed command %s:%s to %s",
						commandData.name(), commandData.id(), guildId)));
	}

	public Mono<Void> deleteCommand(long guildId, String name) {
		log.debug("deleting command " + name);
		return getCommandIdByName(guildId, name)
				.flatMap(commandId -> applicationService.deleteGuildApplicationCommand(botId, guildId, commandId));
	}

	public Flux<ApplicationCommandData> deleteAllGuildCommands(long guildId) {
		return applicationService.getGuildApplicationCommands(botId, guildId)
				.doOnNext(commandData -> log.trace(String.format("deleting command %s:%s on %s",
						commandData.name(), commandData.id(), guildId)))
				.doOnNext(commandData -> applicationService.deleteGuildApplicationCommand(
						botId, guildId, Long.parseLong(commandData.id())).subscribe());
	}

	public void setDiscordCommandPermissions(long guildId, String commandName, Role... roles) {
		var requestBuilder = ApplicationCommandPermissionsRequest.builder();
		Arrays.stream(roles).forEach(role -> {
			log.debug(String.format("setting permissions for command %s to role %s", commandName, role.getName()));
			requestBuilder.addPermission(ApplicationCommandPermissionsData.builder()
					.id(role.getId().asLong()).type(1).permission(true).build()).build();
		});
		getCommandIdByName(guildId, commandName).subscribe(commandId ->
				applicationService.modifyApplicationCommandPermissions(botId, guildId, commandId, requestBuilder.build())
						.subscribe(permissionsData -> log.debug("...to command " + permissionsData.id())));
	}

	private Mono<Long> getCommandIdByName(long guildid, String commandName) {
		return applicationService.getGuildApplicationCommands(botId, guildid)
				.filter(applicationCommandData -> applicationCommandData.name().equals(commandName))
				.next()
				.map(commandData -> Long.parseLong(commandData.id()));
	}
}
