package com.elorankingbot.commands;

import com.elorankingbot.service.Services;
import discord4j.core.event.domain.interaction.MessageInteractionEvent;

// Accessed through right clicking on messages
public abstract class MessageCommand extends Command {

	protected final MessageInteractionEvent event;

	protected MessageCommand(MessageInteractionEvent event, Services services) {
		super(event, services);
		this.event = event;
	}
}
