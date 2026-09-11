# eUtil

English | [日本語](README.md)

A Fabric client mod for [EarthMC](https://earthmc.net/).
It bundles handy features for playing on EarthMC, such as a VoteParty progress display and a command that lists towns nearing collapse (deletion).

## Requirements

| Item | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or higher |
| Fabric API | Required |
| Mod Menu | 17.0.0-alpha.1 or higher |
| Java | 21 or higher |

## Features

### 🗳️ VoteParty HUD

Polls the EarthMC API (`https://api.earthmc.net/v4/`) every 60 seconds and permanently displays the number of votes remaining until the next VoteParty on screen.

- Display position (four corners), offset, whether to show the progress bar, text color, and bar color can all be changed from the [Mod Menu](https://modrinth.com/mod/modmenu) config screen
- Settings are saved to `config/eutil-voteparty.json`

### 🌲 `/falling` command

Towns are automatically deleted (collapse) 42 days after their last login. This command lists **towns that are approaching collapse**.

```
/falling                      # List towns sorted by default (soonest to collapse first)
/falling <sort>                # Specify a sort order
/falling <sort> <nation>       # Further filter by nation name (partial match)
```

**Sort options**

| Option | Description |
|---|---|
| (none) | Soonest to collapse first |
| `alphabetical` | By town name |
| `founded` | Most recently founded first |
| `residents` | Most residents first |
| `size` | Most plots first |
| `balance` | Highest balance first |
| `capital` | Whether it's a capital |
| `open` | Whether it's an open town |

For each town, it shows the remaining time (color-coded by urgency), the collapse date/time, coordinates (click to copy), the mayor, resident count, plot count, balance, and whether it's a capital / open / allows outsider spawns / has PVP enabled. Results are paginated 5 per page, with buttons in chat (`<<` `<` `>` `>>`) to navigate between pages.

### About data caching

- Data on towns nearing collapse is fetched from the EarthMC API and cached in `config/eutil/falling_cache.json`
- The deletion check (last login + 42 days) is automatically refreshed every day at **19:01 (JST)**
- If a cache from that time already exists, it is reused instead of making a new API request

## Building

```bash
./gradlew build
```

The generated jar is output to `build/libs/`.

## Development

```bash
./gradlew runClient
```

launches a test client.

## License

CC0-1.0
