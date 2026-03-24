# Tactics Game — Claude Code Reference

## Project Overview
A turn-based tactics game built with LibGDX in Java. Two teams of characters take turns on a 9x9 grid. The game supports both local play and online multiplayer via KryoNet. Characters are drafted before the match and deployed during a pre-game setup phase.

---

## Package Structure
```
com.mygame.tactics                  → Core engine, screens, data
com.mygame.tactics.characters       → Individual character classes
com.mygame.tactics.network          → Multiplayer server/client
```

---

## Key Files and Their Roles

| File | Role |
|------|------|
| `GameState.java` | Pure data container. All game state lives here. No rendering types. |
| `GameEngine.java` | Mutates GameState in response to Actions. All game logic lives here. |
| `CombatScreen.java` | Renders the game and handles input. Reads GameState, dispatches Actions. |
| `Character.java` | Abstract base class for all characters. |
| `Ability.java` | Abstract base class for all abilities. |
| `Action.java` | Sealed hierarchy of plain data objects representing player intent. |
| `CombatBoard.java` | 9x9 grid. Handles character placement, movement, and tile state. |
| `Tile.java` | Individual tile state (poisoned, collapsed, structure, clothes pile, etc). |
| `Timeline.java` | Sorts characters by currentWait to determine turn order. |
| `Enums.java` | All enums: CharClass, CharType, Alliance, Rarity. |
| `EngineEvent.java` | Events emitted by GameEngine (popups, animations) read by CombatScreen. |
| `AbilityResult.java` | Returned by Ability.execute() to tell the engine what phase transition to do. |
| `GameServer.java` | KryoNet server. Runs standalone. Ports 54555 and 54777. |
| `NetworkClient.java` | KryoNet client. Connects to server IP. |
| `DraftScreen.java` | Character selection screen before the match. |
| `CombatScreen.java` | Main game screen. |
| `OnlineScreen.java` | Online lobby/connection screen. |
| `MenuScreen.java` | Main menu. |

---

## Character Classes (Enums.CharClass)
- `FIGHTER` — Melee, bonus move after ability
- `MAGE` — Magic damage
- `TANK` — High armor
- `SUPPORT` — Healing/buffs
- `ASSASSIN` — Stealth/poison
- `STATUE` — Can't move (baseMoveDist = 0), deployed in wider zone during pre-game
- `SNIPER` — Skips movement phase, goes straight to ability
- `ENGINEER` — Places structures
- `COLLECTOR` — Special mechanics
- `CHAOS` — Unpredictable/special rules

---

## Character Roster

### Team 1 / Team 2 (all in `com.mygame.tactics.characters`)
| Character | Class | Notable Mechanics |
|-----------|-------|-------------------|
| Aaron | — | — |
| Anna | — | — |
| Ben | — | Lockdown ability |
| Billy | ASSASSIN | Chooses a disguise before deploying. Two pre-game turns: disguise selection then deployment. Invisible/stealth mechanics. |
| Brad | — | — |
| Emily | — | — |
| Evan | — | — |
| Ghia | — | Clothes pile invisibility |
| GuardTower | STATUE | Volley, Wind Power, Summon The Wind (direction-click ability) |
| Hunter | — | Invisibility mechanic |
| Jaxon | — | — |
| Lark | — | Two-step Wall of Fire ability |
| Luke | ENGINEER | Places pergola tiles that grant range bonus |
| Mason | ASSASSIN | Starts invisible. Should be hidden from enemy team after deployment. Take Flight movement. |
| Maxx | FIGHTER | Not Even Close — revives as zombie on first death |
| Nathan | — | — |
| Sean | — | Two-step Painted Walls ability |
| Snowguard | STATUE | — |
| Thomas | — | — |
| Tyler | — | — |
| Weirdguard | STATUE | Bull Charge (direction-click ability) |

---

## Game Flow

### Phase: PRE_GAME
- `setupQueue` determines order: Billy first (twice — disguise then deploy), then STATUEs, then everyone else
- STATUE deployment zone: team 1 rows 0–4, team 2 rows 4–8
- Regular character deployment zone: team 1 rows 0–1, team 2 rows 7–8
- Billy deployment zone: same as regular characters (rows 0–1 / 7–8)
- `hasDeployed` flag tracks whether a character has been placed

### Phase: BATTLE
- Timeline sorts by `currentWait` — lowest goes first
- Each turn has two sub-phases: `MOVEMENT` then `ABILITY`
- STATUEs and characters with `baseMoveDist == 0` skip movement during battle (NOT during pre-game)
- SNIPERs skip straight to ABILITY phase

### Phase: GAME_OVER
- Triggered when one team has no living units

---

## Multiplayer Architecture
- `client == null` means local play; `myTeam` is 0
- `client != null` means online play; `myTeam` is 1 or 2
- `CombatScreen.dispatch()` sends Actions to server when online, applies locally when offline
- Enemy actions arrive via `NetworkClient.MessageListener` and are applied to local GameState
- **Visibility rule:** Always use `myTeam` (not `state.activeUnit.team`) when deciding what to hide from the enemy in online play

---

## Ability System

### Ability Constructor
```java
new Ability(String name, String description, int range, boolean needsTarget)
```
- `range == 0` + `needsTarget == false` = self-cast, fires immediately
- `range == 0` + STATUE class = direction-click ability, must call `calculateTargetRange()` and wait for grid click
- `range > 0` = targeted ability, calls `calculateTargetRange()` and waits for grid click
- `isHeal = true` = shows green highlight instead of red

### Two-Step Abilities
Some abilities require two grid clicks (anchor + direction):
- Sean: Painted Walls
- Lark: Wall of Fire
- Uses `Action.TwoStepAbilityAction` and stores anchor in character-specific fields

### STATUE Direction Abilities
Abilities on STATUE-class characters with `range == 0` that need a tile click:
- GuardTower: Summon The Wind
- Weirdguard: Bull Charge
- Pattern: button click → `calculateTargetRange()` → grid click → `dispatch(AbilityAction)`
- Exception: abilities named "Claws" fire immediately (if any STATUE has one)

---

## Rendering Rules (CombatScreen.drawBoard)
- Movement range tiles: cyan/teal highlight — only shown to `myTeam` in online play
- Targetable tiles: colored fill + colored outline (red for damage, green for heal)
- Selected target tile: white outline override
- Invisible enemy characters: hidden from opponent (`chr.team != viewerTeam`)
- `viewerTeam` = `myTeam` in online play, `state.activeUnit.team` in local play
- Billy: hidden before deployment (`!chr.hasDeployed`), shown after (using disguise portrait if set)
- Mason: hidden from enemy after deploying since he starts invisible

---

## Common Pitfalls / Known Patterns

1. **`targetableTiles` must be cleared in `calculateMovementRange()`** — otherwise stale red highlights persist into movement phase
2. **`baseMoveDist == 0` skip must check `state.isBattle()`** — otherwise STATUEs skip their deployment turn
3. **`state.activeUnit.team` vs `myTeam`** — always use `myTeam` for visibility/ownership checks in online play
4. **Billy's `setupQueue` entry** — Billy appears twice: once for disguise, once for deployment
5. **Server port conflict** — if server fails to start, kill the old process: `netstat -ano | findstr :54555` then `taskkill /PID <n> /F`

---

## Build / Run
- Built with LibGDX + Gradle
- Server runs standalone: `GameServer.main()`
- Client runs via LibGDX desktop launcher: `Main.java`
- Server ports: TCP/UDP 54555 and 54777
