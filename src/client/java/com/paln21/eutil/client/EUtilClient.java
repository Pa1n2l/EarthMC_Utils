package com.paln21.eutil.client;

import com.paln21.eutil.client.falling.FallingCache;
import com.paln21.eutil.client.falling.FallingCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class EUtilClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EarthMcApi.start();
		VotePartyHud.register();

		FallingCache.start();
		FallingCommand.register(); // ← EVENT.registerで包まない、直接呼ぶだけでOK
	}
}