package de.neuefische.elotracking.backend.commands;

import de.neuefische.elotracking.backend.model.ChallengeModel;
import de.neuefische.elotracking.backend.model.Game;
import de.neuefische.elotracking.backend.service.DiscordBotService;
import de.neuefische.elotracking.backend.service.EloTrackingService;
import de.neuefische.elotracking.backend.timedtask.TimedTaskQueue;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Message;

import java.util.Optional;

public abstract class EmojiCommand {

	protected final EloTrackingService service;
	protected final DiscordBotService bot;
	protected final TimedTaskQueue queue;
	protected final ReactionAddEvent event;
	protected final Message reporterMessage;
	protected final long guildId;
	protected final Game game;
	protected final ChallengeModel challenge;
	protected final boolean isChallengerCommand;

	public EmojiCommand(ReactionAddEvent event, EloTrackingService service, DiscordBotService bot, TimedTaskQueue queue) {
		this.event = event;
		this.service = service;
		this.bot = bot;
		this.queue = queue;
		this.reporterMessage = event.getMessage().block();
		Optional<ChallengeModel> maybeChallengeByChallengerMessageId = service.getChallengeByChallengerMessageId(event.getMessageId().asLong());
		if (maybeChallengeByChallengerMessageId.isPresent()) {
			this.challenge = maybeChallengeByChallengerMessageId.get();
			this.guildId = challenge.getGuildId();
			this.isChallengerCommand = true;
		} else {
			this.challenge = service.getChallengeByAcceptorMessageId(event.getMessageId().asLong()).get();
			this.guildId = challenge.getGuildId();
			this.isChallengerCommand = false;
		}
		this.game = service.findGameByGuildId(guildId).get();
	}

	public abstract void execute();

}
