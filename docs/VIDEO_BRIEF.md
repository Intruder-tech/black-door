# Video brief: Black Door, made in India

## Why this video matters

You are presenting **Black Door**, a Minecraft anti-cheat and server security system **made in
India**. It is a project its maker holds precious, built with security as the first principle:

- it watches first and blocks only when the owner says so;
- if one of its own parts fails, players are not locked out;
- every change an admin makes is recorded;
- it says plainly what it cannot do.

Give it the care of a proud launch: polished, confident and warm. The pride comes from what it
really does. Never promise that it is unbreakable, unbypassable or that a server using it cannot
be breached. No security software can promise that, and one false claim would damage the trust
this project is built on.

Suggested lines you may use:

- "Black Door. Made in India."
- "Guard your world."
- "Watch first. Block with confidence."

## Theme: Minecraft

Style the whole video as a Minecraft adventure:

- **Story:** the server is the player's base. The "black door" is the gate that guards it. Hacked
  clients are intruders knocking; each check is a guard at the gate.
- **Look:** blocky, pixel-style art and fonts, a night-to-day palette, obsidian black for the door,
  with saffron, white and green accents for the Made in India moments.
- **Chapters:** title each section like an in-game achievement, for example "Chapter 1: Building
  the door".
- **Check icons:** reuse the in-game items so viewers recognise them: ender pearl, eye of ender,
  copper ingot, fire charge, echo shard, netherite ingot, heart of the sea, nautilus shell,
  netherite scrap, book and quill, oak sign, armour trim.
- **Sound:** calm adventure music made for this video or properly licensed.
- Draw original artwork. Do not use official Minecraft logos or soundtrack, and include the line
  "Not affiliated with or endorsed by Mojang or Microsoft."

## Before you start: check your tools

First check what you need to make a video with motion graphics (for example a video rendering
library or framework, FFmpeg, fonts, a text-to-speech voice). Do not install anything silently.

If something is missing, **ask me first with your multiple-choice quick-select question menu** (the
tappable options Claude shows), not as a plain paragraph. For each missing tool:

- say in one line what it is and why the video needs it;
- offer options such as "Install it (recommended)", "Use another way without installing" and
  "Skip this part";
- wait for my answer before installing or continuing with that part.

If your environment has no such menu, ask the same question as a short numbered list.

## Motion graphics (required)

The video must be properly animated. A slideshow of still text is not acceptable.

- An animated opening: the black door assembles block by block and swings shut.
- Chapter cards that slide or build in like achievement pop-ups.
- Chat commands typed out letter by letter, with the reply lines appearing one at a time.
- The control panel animating: items drop into their slots, a click pulses, Watch turns into
  Block with a shimmer, the Protection switch flips.
- Packets shown as small travelling blocks that the guards inspect; a refused one bounces off.
- Smooth transitions between chapters, and text that stays on screen long enough to read.

Render a short test clip of the opening and show it to me before producing the whole video.

## Required segment: setting up with `/blackdoor setup`

Give this its own chapter, shown step by step. This is the most important how-to in the video.

1. Join the server as an operator and type `/blackdoor setup` (or `/bd setup`). The server console
   can run it too. Adding a profile is optional, for example `/blackdoor setup smp`. Choices:
   `survival`, `smp`, `competitive-pvp`, `minigames`, `creative`, `custom`, `proxy-network`.
   Profiles are only recorded for now.
2. Black Door prints "Initial security check", then one line per item, marked:
   - **[OK]**: nothing to do;
   - **[WARN]**: read it and decide;
   - **[FAIL]**: fix it before relying on the server's protection.

   On a backend it checks: PacketEvents is installed, the Minecraft version is known, online mode
   or proxy safety, the configuration files are valid, the global mode and the audit log.
3. How to fix the common ones:
   - PacketEvents missing: install it and restart.
   - Offline mode without a properly secured proxy: turn online mode on, or secure the proxy setup
     and firewall the backend.
   - Configuration invalid: fix the file named in the console, then `/blackdoor reload`.
4. It ends with "Setup complete" and says whether Protection is ON or OFF (watch only). Setup never
   turns blocking on by itself.
5. Next, type `/blackdoor gui` in game to open the control panel.

Show the chat replies as an illustration or mark the spot for a real screen recording; do not
invent lines that are not described here.

## Your task

Produce a beginner-friendly introductory video about Black Door. It must cover three things:

1. What the plugin does (and what it does not do).
2. How to install and set it up.
3. The core settings an owner needs to actually protect the server.

**Audience:** Minecraft server owners who are not security experts.
**Length:** no limit. Take the time needed to be clear; do not pad.
**Tone:** calm, plain, practical. Explain a term the first time you use it.

This brief is your only source. You do not have the code or the plugin. If something is not stated
here, leave it out instead of guessing.

## Accuracy rules

- Black Door is an early development build (`0.1.0-SNAPSHOT`). Say so near the start.
- "Anti-cheat" is what the project is building. Say clearly that today it has **no** fly, speed,
  reach, kill-aura or X-ray detection yet; never call it complete.
- Never say or imply "unbreachable", "100% secure", "unbypassable" or "stops all hackers".
- Do not claim it is better than, or the first compared with, any other product.
- Never claim it stops DDoS attacks. Only the hosting provider can absorb large traffic floods.
- Do not give a download link or a release date; none is published here.
- Do not present mock-ups as real footage. If you draw the menus or chat output yourself, label
  them "illustration". Mark places where a real screen recording should be inserted.
- Do not use real player names, server addresses or IP addresses.

## What Black Door does today

**1. Packet checks (backend servers).** A packet is a message the game client sends to the server.
Black Door spots messages a normal game would not send.

| Name shown in game | Catches |
|---|---|
| Impossible location | Positions that cannot exist (broken or out-of-world numbers) |
| Impossible head angle | Looking further up or down than the game allows |
| Invalid hotbar slot | Selecting a hotbar slot that does not exist |
| Hitting themselves | Attacking or using your own character |
| Fake teleport reply | Answering a teleport the server never sent |
| Oversized messages | Chat, command or movement data far too big |
| Fake connection replies | Wrong answers to Black Door's hidden pings (lag or timer cheats) |
| Spam / flooding | Far more actions per second than normal play |
| Oversized mod data | Hidden client or mod data that is far too big |
| Crash books | Books with too many pages or too much text |
| Crash signs | Sign text longer than the game allows |
| Illegal creative items | Creative items with impossible amounts, slots or huge hidden data |

**2. Join and ping flood protection.** Limits how fast one IP address can connect, ping the server
list and log in. Works on backend servers, Velocity and BungeeCord.

**3. Optional login system** (backend servers, off by default). Switch it on with the "Login
system" item in the control panel. New players type `/signup <password> <password>`; returning
players type `/signin <password>`. For 24 hours after typing the password, the same account from
the same IP address rejoins without it. Until signed in, a player cannot move, chat or build.
Staff reset a forgotten password with `/blackdoor auth reset <player>`. Never show a real
password on screen.

**Not available yet** (shown as "coming soon" or not shown at all): movement and combat checks,
anti X-ray / ESP, bot scoring, automatic punishments such as bans, a menu on proxies.

## Requirements

| Server type | Needs |
|---|---|
| Spigot, Paper, Purpur, Folia and similar | Java 17 or newer, plus the **PacketEvents** plugin |
| Velocity 4.x | Java 25 |
| BungeeCord | Java 17 or newer |

Without PacketEvents on a backend server, Black Door starts in a "degraded" state and the packet
checks and join protection do not run.

Testing so far: the newest features were tried live on Paper 26.3, Velocity and BungeeCord only.
Do not promise that they work on other versions.

## Installation

Backend server:

1. Stop the server.
2. Put `BlackDoor-Bukkit-<version>.jar` and the PacketEvents jar in the `plugins` folder.
3. Start the server. Black Door creates `config.yml`, `checks.yml` and `network.yml` in
   `plugins/BlackDoor/`.
4. The console should say Black Door is enabled, with state `RUNNING` and mode `MONITOR`.

Proxy (Velocity or BungeeCord): put the matching proxy jar in the proxy's `plugins` folder and
restart the proxy. On Velocity the settings folder is `plugins/blackdoor/`.

## First-time setup (backend)

All in-game commands need operator status or the matching permission. `/bd` is a short form of
`/blackdoor`.

1. Run `/blackdoor setup` (optionally with a profile name such as `survival`). It checks the
   server's security basics and marks each line OK, WARN or FAIL. It changes no protection
   settings. Profiles are only recorded for now; they do not change any behaviour.
2. Fix every FAIL line. The important one: an **offline-mode** server that is not behind a properly
   secured proxy lets anyone join as any player. Behind BungeeCord, a firewall must allow only the
   proxy to reach the backend server.
3. Run `/blackdoor gui` to open the control panel.

## The control panel

Main page items:

- **Protection** (the master switch): OFF means watch only; ON allows blocking.
- **Basic cheat checks**: opens the page with the twelve checks.
- **Join flood protection**: click to change its mode.
- **Status**, **Reload settings**, **Close**.
- Two "coming soon" items.

Each check has its own icon. Clicking cycles its mode:

| Mode | Meaning | How it looks |
|---|---|---|
| Off | Does nothing | Gray dye |
| Watch | Logs it, never interferes (default) | The check's own item |
| Block | Stops the action, **only while Protection is ON** | The item shimmers |

**The most important idea in the video:** something is blocked only when **both** are true: the
check (or join protection) is set to **Block**, and **Protection is ON**.

## Core settings: how to roll out protection safely

Recommended order:

1. **Days 1 to 3 or longer: watch only.** Keep the defaults. Let players play normally.
2. **Review.** Use `/blackdoor checks` (counts per check), `/blackdoor player <name>` (one player)
   and the server console, where lines marked `[alert]` show what was noticed.
3. **If a normal player or a trusted plugin or mod is flagged,** keep that check on Watch or Off,
   or raise its limit in `checks.yml`.
4. **Switch quiet, trustworthy checks to Block,** then turn **Protection ON**.
5. **Join flood protection:** set it to Block once the console shows no false alerts.

Limits worth knowing (`checks.yml` and `network.yml`; change them only with a reason):

| Setting | Default |
|---|---|
| Connections per address | 30 at once, then 3 per second |
| Server list pings per address | 30 at once, then 5 per second |
| Logins per address | 10 at once, then 1 per second |
| Local and private network addresses | Never limited (`exempt-private-addresses: true`) |
| Extra trusted addresses | `exempt-addresses` (comma separated) |
| Plugin message size | `payload.max-bytes: 32767` (raise if an allowed mod needs more) |
| Creative item size | `creative-item.max-bytes: 65536` (raise for plugins with large custom items) |

After editing files by hand, run `/blackdoor reload`.

Because local addresses are never limited, testing join protection from the same computer or home
network will appear to do nothing. That is expected. Operators are **not** exempt from the packet
checks; a normal game client simply does not send the data they look for.

## Networks with a proxy

- Install the proxy jar on the proxy so the join limits see each player's real address.
- On a backend with BungeeCord forwarding enabled, per-address limits switch off automatically,
  because every player arrives from the proxy's address.
- With Velocity modern forwarding, a proxy that reaches the backend over a public address must be
  listed in the backend's `exempt-addresses`.
- Proxies have **no** in-game panel. Set `mode` in `network.yml` to `mitigate`, set `mode` in
  `config.yml` to `enforce`, then run `/blackdoorproxy reload`. Ignore any message on the proxy
  that suggests using the in-game panel.
- BungeeCord cannot refuse a server-list ping by itself; only the connection limit applies there.

## Command reference

| Command | Use |
|---|---|
| `/blackdoor help` | List commands |
| `/blackdoor setup [profile]` | First-time security check |
| `/blackdoor gui` | Control panel |
| `/blackdoor status` | Health and protection overview |
| `/blackdoor checks` | All checks and their modes |
| `/blackdoor player <name>` | Inspect one player |
| `/blackdoor reload` | Reload the configuration files |
| `/blackdoor version`, `/blackdoor diagnostics` | Version and technical details |

Permissions to mention for staff: `blackdoor.command.gui` (view the panel) and `blackdoor.gui.edit`
(change settings).

In the settings files, the mode names are `disabled`, `monitor` and `mitigate` (shown in game as
Off, Watch and Block), and the master switch is `mode: monitor` or `mode: enforce` in `config.yml`.

## Suggested structure

1. Hook: what can go wrong on an unprotected server.
2. What Black Door is, and an honest statement of what it is not yet.
3. Requirements and installation.
4. `/blackdoor setup` and what the results mean.
5. Tour of the control panel: Off, Watch, Block and the Protection switch.
6. Safe rollout: watch, review, then block.
7. Join flood protection and proxy networks.
8. Recap checklist: install, run setup, fix FAIL lines, watch, review, block.

## Deliverable

A finished, animated video file. If it cannot be produced even after asking me about missing tools,
tell me why, and with my agreement deliver a complete production package instead: the narration
script, a scene-by-scene storyboard with on-screen text and animation notes, and a list of the
screen recordings I need to capture.
