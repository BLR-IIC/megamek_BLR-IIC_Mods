/*
 * MegaMek - Copyright (c) 2025 - The MegaMek Team. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.client.bot.princess.commands;

import megamek.client.bot.Messages;
import megamek.client.bot.princess.Princess;
import megamek.common.Entity;
import megamek.common.GunEmplacement;
import megamek.common.InGameObject;
import megamek.server.commands.arguments.Argument;
import megamek.server.commands.arguments.Arguments;
import megamek.server.commands.arguments.TeamArgument;
import megamek.server.commands.arguments.UnitArgument;

import java.util.List;

/**
 * Command to ignore a target unit for the bot.
 * @author Luana Coppio
 */
public class IgnoreTurretsCommand implements ChatCommand {
    private static final String TEAM_ID = "teamID";
    @Override
    public List<Argument<?>> defineArguments() {
        return List.of(
            new TeamArgument(TEAM_ID, Messages.getString("Princess.command.ignoreTurrets.teamID"))
        );
    }

    @Override
    public void execute(Princess princess, Arguments arguments) {
        TeamArgument unitArg = arguments.get(TEAM_ID, TeamArgument.class);
        princess.getGame().getInGameObjects().stream().filter(e -> e instanceof GunEmplacement)
            .filter(e -> ((GunEmplacement) e).getOwner().getTeam() == unitArg.getValue())
            .map(InGameObject::getId)
            .forEach(i -> princess.getBehaviorSettings().addIgnoredUnitTarget(i));
    }
}
