// src/client/java/com/paln21/eutil/client/falling/FallingTown.java
package com.paln21.eutil.client.falling;

public record FallingTown(
		String name,
		String nation,
		String mayor,
		int residents,
		int plots,
		double balance,
		int x, int y, int z,
		String mapUrl,
		boolean isCapital,
		boolean isOpen,
		boolean canOutsidersSpawn,
		boolean pvp,
		long lastOnlineSec,
		long deletionTimeMs,
		long deletionTimestampSec,
		long registered
) {
}