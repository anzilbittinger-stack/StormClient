package dev.stormclient;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** StormClient client entry point. */
public final class StormClient implements ClientModInitializer {
    public static final String MOD_ID = "stormclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("StormClient initialised.");
    }
}
