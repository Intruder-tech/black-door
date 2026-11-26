# Login system

> Backend servers only. Off by default.

## Behaviour

| Situation | Result |
|---|---|
| First join while the system is on | Player must `/signup <password> <password>` |
| Rejoin within 24 hours of the last password entry, same account and IP address | No password needed |
| Rejoin later, or from another address | Player must `/signin <password>` |
| Not authenticated | Cannot move, chat, use other commands, build, interact, use inventories or take damage; kicked after 60 seconds |
| 5 wrong passwords | That address is locked out of the account for 5 minutes and kicked |
| Forgotten password | Staff run `/blackdoor auth reset <player>` |

The 24 hours are counted from the last password entry, not from the last visit, and are checked
when a player joins. A player who stays online is not interrupted.

Switch it on with the **Login system** item in `/blackdoor gui`, or `enabled: true` in `auth.yml`.
Players already online are asked from their next join.

## Security

- Passwords: PBKDF2-HMAC-SHA256, 600,000 iterations, random salt. Stored in `auth/accounts.tsv`.
- The session stores a digest of the address, not the address.
- The signup message shows the password only to that player, hidden behind hover text.
- `/signup` and `/signin` are handled before command dispatch, and their lines are removed from
  the server log.
- Fails closed: if accounts cannot be read, nobody can sign in, and the damaged file is never
  overwritten. This differs from detection checks, which fail open.

## Limitations

- Behind a proxy without player-info forwarding every player has the proxy's address, so the
  address match protects nothing. Configure forwarding.
- Players sharing one address (same household) share that protection on offline-mode servers.
- Other plugins that read commands even when cancelled, or that log chat input, could still see a
  password. The text a player types also stays in their own client chat history.
- No password change command yet; reset and sign up again.
- No Velocity or BungeeCord login, no database storage, no import from other login plugins.
- On 1.8 and other servers without hover support the password is not repeated.
- The accounts file is not encrypted; protect the server's files.

## Approaches considered

- **Chosen:** inside Black Door, as its own module, with file storage and no new runtime
  dependencies.
- A separate plugin: rejected, the owner asked for one plugin.
- Generated passwords: rejected by the owner in favour of player-chosen ones.
