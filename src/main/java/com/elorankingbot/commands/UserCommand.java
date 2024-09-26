package com.elorankingbot.commands;

import com.elorankingbot.service.Services;
import discord4j.core.event.domain.interaction.UserInteractionEvent;

// Accessed through right clicking on a User
public abstract class UserCommand extends Command {

	protected final UserInteractionEvent event;

	protected UserCommand(UserInteractionEvent event, Services services) {
		super(event, services);
		this.event = event;
	}
}
