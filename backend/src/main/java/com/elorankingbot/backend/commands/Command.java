package com.elorankingbot.backend.commands;

import com.elorankingbot.backend.command.AdminCommand;
import com.elorankingbot.backend.command.EventParser;
import com.elorankingbot.backend.command.ModCommand;
import com.elorankingbot.backend.commands.admin.SetPermission;
import com.elorankingbot.backend.model.Server;
import com.elorankingbot.backend.service.*;
import com.elorankingbot.backend.timedtask.TimedTaskQueue;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.User;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class Command {

	protected final DBService dbService;
	protected final DiscordBotService bot;
	protected final ChannelManager channelManager;
	protected final MatchService matchService;
	protected final QueueService queueService;
	protected final TimedTaskQueue timedTaskQueue;
	private final EventParser eventParser;
	protected final DeferrableInteractionEvent event;
	protected final long guildId;
	protected final Server server;
	protected final User activeUser;
	protected final long activeUserId;

	protected static final List none = new ArrayList<>();

	protected Command(DeferrableInteractionEvent event, Services services) {
		this.dbService = services.dbService;
		this.bot = services.bot;
		this.channelManager = services.channelManager;
		this.matchService = services.matchService;
		this.queueService = services.queueService;
		this.timedTaskQueue = services.timedTaskQueue;
		this.eventParser = services.eventParser;
		this.event = event;
		this.guildId = event.getInteraction().getGuildId().get().asLong();
		this.server = dbService.getOrCreateServer(guildId);
		this.activeUser = event.getInteraction().getUser();
		this.activeUserId = activeUser.getId().asLong();
	}

	public void doExecute() throws Exception {
		String executeLog = String.format("execute %s by %s on %s",
				this.getClass().getSimpleName(),
				event.getInteraction().getUser().getTag(),
				event.getInteraction().getGuild().block().getName());
		log.debug(executeLog);

		// bypass permission check when admin role is not set or not present
		if (this.getClass() == SetPermission.class) {
			boolean adminRoleExists = event.getInteraction().getGuild().block()
					.getRoles().map(role -> role.getId().asLong()).collectList().block()
					.contains(server.getAdminRoleId());
			if (server.getAdminRoleId() == 0L || !adminRoleExists) {
				execute();
				return;
			}
		}

		List<Long> memberRoleIds = new ArrayList<>(event.getInteraction().getMember().get().getRoleIds()
				.stream().map(Snowflake::asLong).toList());
		memberRoleIds.add(Long.valueOf(guildId));
		boolean userIsAdmin = memberRoleIds.contains(server.getAdminRoleId());
		boolean userIsMod = memberRoleIds.contains(server.getModRoleId());
		if (this.getClass().isAnnotationPresent(AdminCommand.class)) {
			if (server.getAdminRoleId() == 0L) {
				event.reply("This command requires Admin permissions. The Admin role is not currently set. " +
						"Please use /setpermissions.").subscribe();
				return;
			}
			if (!userIsAdmin) {
				event.reply(String.format("This command requires the <@&%s> role.", server.getAdminRoleId()))
						.withEphemeral(true).subscribe();
				return;
			}
		}
		if (this.getClass().isAnnotationPresent(ModCommand.class)) {
			if (!userIsAdmin && server.getModRoleId() == 0L) {
				event.reply("This command requires Moderator permissions. The Moderator role is not currently set. " +
						"Please use /setpermissions.").subscribe();
				return;
			}
			if (!userIsAdmin && !userIsMod) {
				event.reply(String.format("This command requires the <@&%s> role.", server.getModRoleId()))
						.withEphemeral(true).subscribe();
				return;
			}
		}

		execute();

		log.trace(String.format("Done executing %s by %s on %s",
				this.getClass().getSimpleName(),
				event.getInteraction().getUser().getTag(),
				event.getInteraction().getGuild().block().getName()));
	}

	protected abstract void execute() throws Exception;

	protected void acknowledgeEvent() {
		event.deferReply().subscribe();
	}

	protected void forwardToEventParser(Throwable throwable) {
		eventParser.handleException(throwable, event, this.getClass().getSimpleName());
	}
}
