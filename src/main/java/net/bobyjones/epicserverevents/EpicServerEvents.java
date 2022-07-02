package net.bobyjones.epicserverevents;

import net.fabricmc.api.ModInitializer;


import java.util.logging.Logger;

public class EpicServerEvents implements ModInitializer {

    public static final String MOD_ID = "epicserverevents";
    public static final Logger LOGGER = Logger.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Commands.Register();
    }
}
