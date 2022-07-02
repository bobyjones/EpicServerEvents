package net.bobyjones.epicserverevents;


import com.mojang.brigadier.context.CommandContext;
import net.bobyjones.epicserverevents.Events.ZombieEvent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

public class Commands {

    //register commands
    public static void Register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, two, env) -> {
                dispatcher.register(CommandManager.literal("StartEvent").then(CommandManager.literal("zombie").executes(context -> {
                    if (context.getSource().hasPermissionLevel(4) && context.getSource().isExecutedByPlayer() && !context.getSource().getWorld().isClient) {
                        ZombieEvent.run(context.getSource().getPlayer());
                        return 1;
                    }
                    return -1;
                })));
        });
    }
}
