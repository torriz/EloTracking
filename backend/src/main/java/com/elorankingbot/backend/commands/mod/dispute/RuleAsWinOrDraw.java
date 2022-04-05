package com.elorankingbot.backend.commands.mod.dispute;

import com.elorankingbot.backend.model.MatchResult;
import com.elorankingbot.backend.model.Player;
import com.elorankingbot.backend.model.PlayerMatchResult;
import com.elorankingbot.backend.service.MatchService;
import com.elorankingbot.backend.service.Services;
import com.elorankingbot.backend.tools.EmbedBuilder;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;

import static com.elorankingbot.backend.model.ReportStatus.*;
import static com.elorankingbot.backend.tools.FormatTools.formatRating;

public abstract class RuleAsWinOrDraw extends ButtonCommandRelatedToDispute {

	protected int winningTeamIndex;
	protected boolean isRuleAsWin;

	public RuleAsWinOrDraw(ButtonInteractionEvent event, Services services) {
		super(event, services);
	}

	public void execute() {
		if (!isByModeratorOrAdminDoReply()) return;

		if (isRuleAsWin) {
			for (int i = 0; i < match.getNumTeams(); i++) {
				for (Player player : match.getTeams().get(i)) {
					match.reportAndSetConflictData(player.getId(), i == winningTeamIndex ? WIN : LOSE);
				}
			}
		} else {
			for (Player player : match.getPlayers()) {
				match.reportAndSetConflictData(player.getId(), DRAW);
			}
		}
		match.setIsOrWasConflict(false);// TODO dirty hack! EmbedBuilder neu machen!
		MatchResult matchResult = MatchService.generateMatchResult(match);
		updatePlayerMessages(matchResult);
		dbService.saveMatchResult(matchResult);// TODO das hier ist alles sehr ähnlich mit Report, vllt refaktorn?
		dbService.deleteMatch(match);
		postToDisputeChannelAndUpdateButtons(String.format(
				"**%s has ruled this match a %s %s%s.**",// TODO! hier auch tags, oder nur tags
				moderatorName,
				isRuleAsWin ? WIN.asNoun : DRAW.asNoun,
				isRuleAsWin ? WIN.asEmojiAsString() : DRAW.asEmojiAsString(),
				isRuleAsWin ? " for team #" + (winningTeamIndex + 1) : ""));
		bot.postToResultChannel(matchResult);
		matchResult.getPlayers().forEach(player -> {
			player.addMatchResult(matchResult);
			dbService.savePlayer(player);
		});
		boolean hasLeaderboardChanged = dbService.persistRankings(matchResult);
		if (hasLeaderboardChanged) bot.refreshLeaderboard(matchResult.getGame()).subscribe();

		// TODO! channels closen, sind die pings sinnvoll? was ist mit mentions?

		//addMatchSummarizeToQueue(matchResult);
		event.acknowledge().subscribe();
	}

	private void updatePlayerMessages(MatchResult matchResult) {
		for (Player player : match.getPlayers()) {
			bot.getPlayerMessage(player, match)
					.subscribe(message -> {
						PlayerMatchResult playerMatchResult = matchResult.getPlayerMatchResult(player.getId());
						String embedTitle = String.format("%s has ruled the match %s %s. Your new rating: %s (%s)",
								moderatorName,
								isRuleAsWin ? "your " + playerMatchResult.getResultStatus().asNoun : "a draw",
								playerMatchResult.getResultStatus().asEmojiAsString(),
								formatRating(playerMatchResult.getNewRating()),
								playerMatchResult.getRatingChangeAsString());
						EmbedCreateSpec embedCreateSpec = EmbedBuilder
								.createCompletedMatchEmbed(embedTitle, match, matchResult, player.getTag());

						message.delete().subscribe();
						bot.getPrivateChannelByUserId(player.getUserId()).subscribe(channel ->
								channel.createMessage(embedCreateSpec).subscribe());
					});
		}
	}
}
