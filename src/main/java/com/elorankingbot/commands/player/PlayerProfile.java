package com.elorankingbot.commands.mod;

import com.elorankingbot.command.annotations.GlobalCommand;
import com.elorankingbot.command.annotations.ModCommand;
import com.elorankingbot.commands.UserCommand;
import com.elorankingbot.commands.player.help.HelpComponents;
import com.elorankingbot.components.EmbedBuilder;
import com.elorankingbot.model.MatchResult;
import com.elorankingbot.model.MatchResultReference;
import com.elorankingbot.model.Player;
import com.elorankingbot.model.PlayerGameStats;
import com.elorankingbot.service.Services;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.possible.Possible;
import lombok.extern.apachecommons.CommonsLog;
import reactor.core.publisher.Mono;

import java.util.Optional;

@ModCommand
@GlobalCommand
@CommonsLog
public class PlayerProfile extends UserCommand {

	//private MatchResult matchResult;
	//private MatchResultReference matchResultReference;
	private Player player;

	public PlayerProfile(UserInteractionEvent event, Services services) {
		super(event, services);
	}

	public static ApplicationCommandRequest getRequest() {
		return ApplicationCommandRequest.builder()
				.name(HelpComponents.helpEntryNameOf(PlayerProfile.class))
				.type(3)
				.build();
	}

	public static String getShortDescription() {
		return "Display the rating history of a player by right-clicking.";
	}

	public static String getLongDescription() {
		return getShortDescription() + "\n" +
				"This is not a slash command, but rather a user command (user context menu). Access it by right-clicking on a User " +
				"-> Apps -> " + HelpComponents.helpEntryNameOf(PlayerProfile.class);
	}

	protected void execute() {
		Optional<Player> maybePlayer = dbService.findPlayerByGuildIdAndUserId(event.getTargetId().asLong());

		if (maybePlayer.isEmpty()) {
			event.reply("This user is not registered as a Player yet.")
					.withEphemeral(true).subscribe();
			return;
		}
	
		//event.reply("Match reverted.").withEphemeral(true).subscribe();

		event.deferReply().withEphemeral(isSelfInfo).subscribe();

		targetPlayer = maybePlayer.get();
		List<EmbedCreateSpec> embeds = new ArrayList<>();
		for (String gameName : targetPlayer.getGameNameToPlayerGameStats().keySet()) {
			Game game = server.getGame(gameName);
			RankingsExcerpt rankingsExcerpt = dbService.getRankingsExcerptForPlayer(game, targetPlayer);
			embeds.add(EmbedBuilder.createRankingsEmbed(rankingsExcerpt));
			embeds.add(EmbedBuilder.createMatchHistoryEmbed(targetPlayer, getMatchHistory(game)));
		}
		String banString = createBanString();

		if (embeds.isEmpty()) {
			event.editReply(banString + "No match data found. That user has likely not yet played a match.").subscribe();
		} else {
			InteractionReplyEditMono reply = targetPlayer.isBanned() ? event.editReply(banString) : event.editReply();
			reply.withEmbeds(embeds).subscribe();
		}

	}
	
	private String createBanString() {
		String banString = "";
		if (targetPlayer.isBanned()) {
			if (targetPlayer.isPermaBanned()) {
				banString = String.format("**%s banned permanently, or until unbanned.**\n",
						isSelfInfo ? "You are" : "This player is");
			} else {
				int stillBannedMinutes = timedTaskScheduler.getRemainingDuration(targetPlayer.getUnbanAtTimeSlot());
				banString = String.format("**%s still banned for %s.**\n",
						isSelfInfo ? "You are" : "This player is",
						DurationParser.minutesToString(stillBannedMinutes));
			}
		}
		return banString;
	}

	private List<Optional<MatchResult>> getMatchHistory(Game game) {
		List<UUID> fullMatchHistory = targetPlayer.getOrCreatePlayerGameStats(game).getMatchHistory();
		List<UUID> recentMatchHistory = fullMatchHistory.subList(Math.max(0, fullMatchHistory.size() - 20), fullMatchHistory.size());
		return recentMatchHistory.stream().map(dbService::findMatchResult).toList();
	}
}
