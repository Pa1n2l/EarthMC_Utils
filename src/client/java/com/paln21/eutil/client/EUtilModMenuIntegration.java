// src/client/java/com/paln21/eutil/client/EUtilModMenuIntegration.java
package com.paln21.eutil.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class EUtilModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VotePartyConfigScreen::new;
	}
}