# Schematica (Babric)

A port of Lunatrius' Schematica 1.2.0.10 for Minecraft Beta 1.7.3 from Risugami's ModLoader to
[Babric](https://babric.github.io/) (Fabric Loader for b1.7.3), using the
[Barn](https://github.com/babric/barn) mappings.

Load an MCEdit `.schematic` file and it is drawn over the world as a ghost you can build against,
with colour-coded boxes showing what is missing or wrong. You can also select a region of the world
and save it back out as a schematic. A material list says what the whole thing will take and counts
down as you gather it, on a screen or in the corner of the screen while you build.

## Using it

| Key            | Controls entry        | Action                                        |
| -------------- | --------------------- | --------------------------------------------- |
| Numpad `/`     | Load Schematic        | Load a schematic                              |
| Numpad `*`     | Save Selection        | Save the current selection                    |
| Numpad `-`     | Move Schematic        | Move / rotate / mirror the loaded schematic   |
| Numpad `+`     | Show/Hide Schematic   | Show or hide the loaded schematic             |
| Numpad `.`     | Schematica Settings   | Open the settings screen                      |
| Page Up        | Schematic Layer Up    | Show the next layer up                        |
| Page Down      | Schematic Layer Down  | Show the next layer down                      |
| Numpad `0`     | Easy Place            | Turn easy place on or off                     |
| Numpad `5`     | Material List         | What the schematic is built out of             |

Schematics live in `<game dir>/schematics`. The keys only work while you are in a world and no
other screen is open. They are vanilla bindings, stored in `options.txt` rather than in the mod's
own config file, and they appear in **Options &rarr; Controls** with everything else - but Beta's
controls screen has room for fourteen keys and puts its Done button on top of the fifteenth, so the
last of them are rebound from the mod's own **Controls...** screen instead. Both write the same
bindings to the same file.

The render settings - transparency, the comparison boxes and how far they are inset - live on their
own screen, reachable from the settings key above, the **Settings** button on the move screen, and
Mod Menu's **Configure** button. All three open the same screen, so the key works with or without
Mod Menu installed.

Hiding and showing keeps the cached geometry, so the toggle key is instant whatever the schematic
costs to draw.

### Layers

The overlay can be cut down to a single Y slice, which is how you build a large schematic a course
at a time. The layer keys step it without opening anything, and the move screen's `[-] [+]` pair
does the same with the current layer shown between them. Stepping below the bottom layer goes back
to showing all of them, and both directions stop at the ends rather than wrapping.

A schematic over 125,000 blocks opens on layer 1 rather than all layers, since that is where the
overlay is cheap enough to fly around in.

### Material list

Everything the loaded schematic is built out of, in one list: what it takes, how much of it you are
carrying and how much is still to be found. The **Materials** button on the move screen and Numpad
`5` both open it.

The rows are the items you would go and fetch rather than the blocks the schematic holds, so a wall
of redstone wire asks for redstone dust, a door asks for one door rather than its two halves, water
asks for buckets, and a stack of white wool is a different row from a stack of red. Where the
metadata is not something a stack carries - which way a piston faces, which way a stair turns -
every one of them is the same row, because any piston will do.

Blocks that are no material of their own are not listed at all, because there is nothing to fetch
for them: the head of an extended piston comes out of the piston, the water in a pool ran there from
its source, fire and portals are lit rather than placed. So an extended piston asks for one piston,
and a pool asks for one bucket per source block rather than one per block of water. Anything you
would genuinely have to carry is still listed even where survival cannot supply it - bedrock in a
schematic is bedrock you need.

The `Have` column is counted again every tick while the screen is open, so the numbers fall as you
pick things up: empty a chest into your pack with the screen still open and you can watch what is
left to gather come down. A spare stack is never a debt - having sixty-four of something the
schematic wants ten of leaves nothing missing rather than a negative number - and a stack is only
ever counted towards one row.

The order is what the schematic takes most of first, and it holds still while you gather: sorting by
what is missing would shuffle the rows about under you as the numbers changed.

#### Info HUD

The same list in the bottom right corner of the screen while you play, so what is left to gather is
in front of you while you are gathering it rather than behind a screen. **Info HUD** at the bottom
of the material list turns it on, and it stays on across sessions.

It only lists what is still a job. A row leaves the HUD the moment you are carrying enough of that
material and comes back the moment you are not, so what is left on screen is what is left to find;
when there is nothing left it says so rather than disappearing, which is the difference between
finished and switched off. Rows are counted again every tick, whether or not a screen is open, so
you can watch a row go while sorting a chest.

**HUD Sort** puts either the most or the fewest left to gather at the top. Unlike the screen, the
HUD does sort on what is missing - it is a few rows in a corner rather than a whole list, so the row
that matters has to be at a known end of it. Rows that are level with each other are ordered by name
whichever way round the sort is, so finishing one pile does not shuffle its neighbours.

The HUD shows as many rows as fit in half the screen, up to ten, with a count of what did not fit
underneath. It sits in the very corner when it is narrow enough to stay clear of the hotbar and
above the hearts when it is not, and F1 hides it along with the rest of the interface.

### Easy place

With easy place on, a right click can only ever put down the block the schematic wants at the
position being aimed at - and it puts that block in your hand first. Point at the face next to where
a block belongs, click, and the right one goes down. Holding the button repeats every tick instead
of vanilla's five, so a course goes in as fast as you can sweep along it.

A click that would land anywhere else does nothing at all: no block, no arm swing. That covers a
position the schematic leaves empty, one that is already built, and anything outside the current
layer slice - so slicing to a course also fences the mode into it.

The mode follows the overlay. With nothing loaded, or the schematic hidden, there is nothing to build
against and easy place is off in every way you can feel: clicks are your own again and the faster
repeat goes with them, so leaving the mode on does not quietly change how the rest of the world
builds.

What it deliberately leaves alone:

- **Blocks that handle a right click themselves.** Chests, doors, buttons and the rest still open
  and toggle with a stack of blocks in hand. Each block class is asked whether it overrides
  vanilla's right-click handler rather than being looked up in a list, so a modded block is read the
  same way as a vanilla one.
- **Clicks that are not a placement.** Only an empty hand or a block in hand counts; a tool, a
  bucket or a piece of food is something else and is passed straight through. An empty hand counts
  because that click does nothing in vanilla anyway, and it is the case the hand switch is most
  useful for.
- **Orientation.** Stairs, torches and ladders still take their facing from where you stand and
  which face you click, exactly as without the mod, so a wrongly turned block is still a wrong block
  and still shows up orange. Metadata is only insisted on where the stack is what carries it - wool
  colour, wood type, sapling type - and there the wrong stack is refused rather than placed. Where
  nothing on the hotbar could carry it, like which way a piston ends up facing, it is ignored and
  the block goes down.

Everything is decided before the click reaches the interaction manager, so a click that is dropped
never becomes a placement packet - the mode behaves the same on a server as in single player.

#### Building into thin air

A block the schematic wants with nothing beside it can be placed anyway. Vanilla has no way to do
this - a click has to land on a block, so a position surrounded by air has no face to aim at - so
easy place follows your line of sight itself and puts down the first block the schematic wants
along it, whether or not anything is holding that block up. Cobblestone hangs in the air quite
happily; a wall goes up course by course without a scaffold under it.

Which blocks can stand on their own is not a list kept by the mod: it is the game's own answer to
whether the block may go there. So redstone, a torch or a sapling is left alone until the block it
needs has been built, because those would drop off the moment they were placed. Sand and gravel are
held back too - they pass the game's check and then fall straight back out of the air - but only
where nothing is holding them, so a schematic that stacks sand on a floor still builds.

The nearest position on the line wins, so what goes down is always the first thing you are looking
at, and a block in the way ends the line exactly as it does for vanilla's own aim - nothing is ever
built through a wall. A position you could have clicked at normally is still left to the ordinary
path, so this only ever adds placements that were impossible before, and it takes none away.

#### Taking blocks off the hotbar

Blocks are fetched from the rest of your inventory, not just the bar. When what the schematic wants
is somewhere in your pack, it is moved onto the bar and the click that found it is spent doing so -
holding the button down, that is one tick before it builds.

Something has to come off the bar to make room, and what goes is deliberate: never a tool, a bucket
or anything else that is not a block, and never what is in your hand. A block this schematic uses
nowhere goes first, since it is left over from something else. If the bar has nothing to spare,
nothing is taken and nothing is built - your hotbar is left as you arranged it.

The move is made with shift clicks, which pass a stack between the bar and the pack **without it
ever going through the cursor**. That is the whole reason for the choice. The obvious sequence -
pick the stack up, put it down on the bar, put the displaced one back - leaves a stack on the cursor
between clicks, and a cursor still holding something is dropped on the floor the moment any
container closes. Shift clicks have no such half-finished state, so a click a server turns down is a
click that did nothing.

On a server the move is a request rather than a fact, so easy place places nothing until the server
has answered it: what is in hand is not settled until then. Beta's client keeps no note of which
clicks are outstanding, so the mod reads the server's answers off the wire itself. A refusal costs a
round trip and a retry, never an item.

### Coordinates

Every coordinate on the move and save screens can be typed as well as nudged. Click the number,
type over it and press **Enter**; **Tab** steps to the next one, applying the one you are leaving,
and clicking anywhere else applies it too. **Escape** abandons what you were typing and gives the
field up, so a second press closes the screen as usual.

While a number is being typed it is drawn in yellow and nothing has moved yet - only a value that
has been applied is white. The `[-] [step] [+]` buttons work exactly as they did, and the step
button still cycles 1, 5, 15, 50, 250.

Overlay colours:

| Colour | Meaning                                                    |
| ------ | ---------------------------------------------------------- |
| Blue   | the schematic has a block here and the world is empty       |
| Red    | the world has a *different* block here                      |
| Orange | right block, wrong metadata (rotation, colour, and so on)   |
| Purple | the schematic's bounding box                                |
| Green  | the save selection, red/blue cubes marking its two corners  |

## Large schematics

The overlay is cut into 16x16x16 regions, each cached in its own pair of display lists - the ghost
blocks, and the coloured boxes over them. Three things follow from that:

- **Only what is on screen is drawn.** Regions are culled against the view frustum, which is read
  back out of the matrix stack rather than taken from the game, so it survives whatever a renderer
  mod has done to the world pass.
- **Placing or breaking a block rebuilds one region**, not the whole schematic. A cell is only ever
  compared against the world block in the same place, so nothing outside that region can change.
- **Whole-schematic rebuilds are spread over frames.** Moving, rotating or re-slicing the ghost
  genuinely does invalidate all of it, so instead each frame spends at most 8 ms rebuilding,
  nearest-to-camera first, and picks the rest up next frame. The overlay refreshes visibly rather
  than freezing the game.

Regions that fall outside the current layer slice, and cells the schematic leaves empty, are skipped
before any of the expensive per-block work happens.

Two settings still matter on top of that. **Highlight** puts a translucent box over every block the
world is missing, which on a build you have not started yet is more geometry than the ghost itself
and all of it blended - turning it off is the largest single saving available. **Layers** limits the
overlay to one Y slice, which cuts both the geometry and the rebuild cost to a fraction.

## Mod Menu

The mod registers a `modmenu` entrypoint, so with
[Mod Menu](https://github.com/Prospector/ModMenu) installed it appears in the list with its icon and
a working **Configure** button that opens the settings screen.

Mod Menu is optional and is not bundled. Nothing in the mod references it except
`compat/ModMenuIntegration`, which Fabric only loads when Mod Menu asks for its entrypoints, so with
Mod Menu absent the class is never touched.

## StationAPI

The mod runs alongside [StationAPI](https://github.com/ModificationStation/StationAPI) and neither
depends on nor compiles against it. Two things needed care:

- StationAPI looks a block's state up through `World.getBlockState`, which goes chunk-first, and its
  Arsenic renderer draws from that state rather than from the block id. A schematic is not a real
  world and used to have no chunks at all, which took the game down the moment an overlay was drawn.
  `SchematicWorld` now hands out chunks that read straight through to the schematic.
- The `.schematic` format has one byte per block id. StationAPI widens the id space well past 255,
  so a block above that is saved as air with a warning rather than truncated into a different block.

`./gradlew runClient -PstationApi` puts StationAPI in the dev run, which is how this stays tested.

## Building

Requires JDK 17 or newer.

```
./gradlew build
```

The mod jar lands in `build/libs/`. `./gradlew runClient` launches a dev client with the mod
installed.

`libs/modmenu-1.8.5-beta.11.jar` is checked in because the Beta 1.7.3 port of Mod Menu is not on any
maven the Babric ecosystem publishes to. It is compiled against and added to the dev run only - it
is never shipped. `./gradlew runClient -PnoModMenu` leaves it out of the dev run.

`./gradlew runClient -PstationApi` fetches StationAPI from the Glass maven into `run/mods`; without
the flag the run folder is cleared of it again. It goes to the run folder rather than the classpath
because StationAPI ships its implementation as nested jars, which only the loader unpacks. Beta 1.7.3
and the Babric loader between them provide no guava, gson, commons or logging framework, so the dev
run adds the ones a launcher would - see the `localRuntime` lines in `build.gradle`.

## Layout

```
schematic/   Schematic          the data model: block ids, metadata, block entities
             SchematicFormat    MCEdit .schematic (gzipped NBT) reader and writer
             SchematicWorld     adapts a Schematic to a World so vanilla renderers can draw it
             BlockTransform     metadata fix-ups for rotate and mirror
render/      SchematicRenderer   the region cache: ghost blocks and comparison boxes
             Frustum             the view planes, read back out of the matrix stack
             SignRenderer        signs, drawn with the ghost alpha instead of vanilla's opaque text
gui/         AxisScreen          routes clicks, keys and the tab order to the focused row
             AxisControls        one coordinate row: X: [value] [-] [step] [+]
             NumberFieldWidget   a text field holding a whole number
             SchematicaKeysScreen  rebinds the mod's keys, which vanilla's screen cannot fit
             MaterialListScreen    what the schematic takes, against what is in the pack
             MaterialListWidget    one row of it: the icon, the name and the two counts
             InfoHud               the same list in the corner of the screen, while playing
             four more Screens and a slider widget
compat/      ModMenuIntegration  optional, loaded only when Mod Menu asks for it
mixin/       seven small hooks (see below)
SchematicaState    everything about the current session
SchematicaConfig   the render settings, and the keybinds it hands to vanilla
EasyPlace          what a right click is allowed to do while easy place is on
HotbarRestock      brings a block onto the hotbar, and waits for the server to agree
Sightline          walks the blocks along the line of sight, nearest first
MaterialList       what a schematic is built out of, counted against an inventory
BlockItems         which item puts a block down, and what you would go and fetch for it
```

`Schematic` is deliberately free of world and rendering state, which is what lets `MaterialList`
count one without a world in front of it - and what a future printer or format converter would
work on the same way.

### The mixins

| Mixin                       | Target                          | Why                                                            |
| --------------------------- | ------------------------------- | -------------------------------------------------------------- |
| `MinecraftMixin`            | `init`, `tick`, world change, use | initialise after the GL context exists; poll keys; reset state; easy place |
| `GameOptionsMixin`          | `GameOptions.load`              | add the mod's keys to the list Controls and options.txt walk    |
| `GameRendererMixin`         | `class_555.method_1847` (weather) | the one point inside the world pass with the camera set up      |
| `InGameHudMixin`            | `InGameHud.render`              | draw the info HUD after it, under whatever screen is open       |
| `WorldMixin`                | `World.method_243`              | invalidate the overlay when a block inside it changes           |
| `TranslationStorageAccessor`| `TranslationStorage`            | merge this mod's language file into the vanilla table           |
| `ClientNetworkHandlerMixin` | the transaction packet          | hear whether the server took an inventory swap                  |

The weather hook is the same place the ModLoader version attached itself: it runs after the terrain,
entities and block outline are drawn, with the modelview matrix still in camera space.

## Differences from the original

Behaviour is otherwise the same as 1.2.0.10; these are deliberate changes:

- **Coordinates can be typed.** The original only had the `[-] [step] [+]` buttons, so putting a
  schematic 300 blocks away meant a lot of clicking.
- **The layer slice has keys of its own.** The original could only change it from the move screen,
  which meant opening a screen between every course.
- **Easy place.** The original had nothing like it: every block was placed by hand, against a ghost
  that was only ever a picture. It also places where vanilla cannot - a block the schematic wants
  with nothing around it goes down in mid air - which is a placement the original could not make at
  all rather than one it made differently. See above for what it does and does not take over.
- **A material list.** The original drew a schematic and left working out what it would take to
  build entirely to you. This counts it, in the items you would go and fetch, and counts your
  inventory against it - on a screen, or in a HUD in the corner listing only what is still short.
- **Rotate and mirror move block entities.** The original left signs and chests at their old
  coordinates after a transform.
- **A fresh dimension backs `SchematicWorld`.** The original passed the live world's dimension to
  `World`'s copy constructor, which re-pointed that dimension (and its biome source) at the
  schematic world.
- **Block ids above 255 are dropped to air with a warning** instead of throwing per block while
  rendering, so schematics from later versions load with whatever b1.7.3 can represent. Saving drops
  them the same way rather than truncating the id into some other block.
- **The overlay is invalidated by any block change inside it**, not just the local player's own
  interactions, so it stays correct on servers.
- **The overlay is cached per region and rebuilt incrementally** (see above). The original compiled
  the whole schematic into a single display list and redid all of it whenever anything changed, and
  held the comparison boxes in growable vertex buffers with a hard cap that silently dropped any
  box past it. Both are gone.
- **Render settings persist** to `config/schematica.properties` and can be changed in game. Keybinds
  persist to `options.txt`; a config file still carrying the old `key.*` entries is migrated across
  once and then rewritten without them.
- Dead code from the original is gone: the unfinished per-alpha texture system and its reflection
  into `TextureManager`, an unused chunk cache, and empty `Config`/`KeyBindingHandler` classes.

## Smoke test

`./gradlew runClient -Psmoketest` runs `debug/SmokeTest`: it checks the NBT round trip and the
rotate/mirror tables in memory, checks that the keybinds reached the vanilla controls list and that
rebinding one lands in `options.txt`, checks that Mod Menu resolved the entrypoint, then creates a
world, builds a structure, saves it, loads it back, and screenshots the overlay, every screen, a
rotation and a layer slice into `run/screenshots`. It prints `SMOKETEST: PASS` or `FAIL` and exits.

Easy place is driven the way the mixin drives it: the hit result is pointed at a face and the mod is
asked what would become of the click. A click at the right position goes through and leaves the
block in hand, and one at a position that is empty in the schematic, already built, outside the
layer slice, made with a tool in hand, or against a block that handles right clicks itself is each
checked separately, so a rule that stopped working would be named rather than just missed.

Bringing a block onto the hotbar is checked the same way, including which slot is given up for it:
never a tool, never what is in hand, and a block this schematic uses nowhere before one it needs.

Placing in mid air is checked end to end rather than by asking: the player is stood three blocks west
of a gap with everything between them cleared out, and each click goes through the client's own
handler, so a block either turns up in the world or it does not. One goes down with nothing at all
around it, and nothing goes down where the schematic wants nothing, where the block needs something
under it, where it would fall, or where something is in the way. A position an ordinary click could
reach is checked to be left to the ordinary path, which is what keeps this from quietly taking over
the common case.

The material list is arithmetic over a schematic and an inventory, so it is checked with neither a
world nor a screen: a schematic holding the cases that make it more than a block count - an item
that is not the block it places, a block put down by one item and standing as two, one item whose
stacks are different materials, and one block built out of a pair of another - is counted, then
counted again against a pack that is filled in stages, which is the countdown itself. A second one
holds the blocks that are no material at all, so a piston head, the water that ran out of a source
and stairs turned different ways are each checked to be counted the way they are built rather than
the way they are stored. The screen over the top is opened for real on top of the loaded schematic
and given a stack to find, so what the rows show is checked to be the schematic that is actually
loaded rather than the one the test built.

The info HUD is the same list read a different way, so it is checked the same way: a list is sorted
from both ends and the order asserted outright, a row is gathered and checked to have left the HUD,
the stack is taken back out of the pack and the row checked to have come back asking only for what
is still short, and two rows level with each other are checked to come out the same way round from
either sort. The buttons under the material list are then clicked for real and the HUD asked what it
would draw, which is what joins the two halves. Scene 8 is a screenshot of it over the world and
scene 9 one of it behind an open screen, since whether it draws is not something the checks can say.

`./gradlew runClient -Psmoketest=multiplayer` joins a b1.7.3 server on `localhost:25565` instead of
making a world, and checks easy place against it. This is the only way to exercise the half of the
inventory swap that exists on a server and nowhere else - the asking, and the being answered - since
in single player a slot click is applied on the spot. It checks that the swap goes out and waits,
that the server takes it, that the block is still on the hotbar once it has, and that the block then
placed is still there a couple of seconds later rather than taken back off the player. Any swap the
server turns down fails the run. `scratchpad/mptest.sh` in the session's temp directory starts the
server, joins with the dev client and hands out the items; the server jar is the vanilla one from
the loom cache and runs on a modern JDK.

The mid-air placement is checked there too, and for the same reason: whether a server accepts a click
on a position that holds nothing at all is not something reading the client can settle. The player
looks straight up at a block of air with nothing touching it, clicks, and the block has to be both
there straight away and still there two seconds later - a placement the server threw out comes back
as a block update putting the air back, so surviving that wait is the server's agreement.

The layer step - which the layer keys and the move screen's buttons share - is checked at both ends
of the range, including that a step past the end rebuilds nothing. The mod's controls screen is
driven the same way as a player would: click a row, press a key, and check that the binding and
`options.txt` both took it, and that Escape gives the rebind up rather than binding Escape.

The coordinate rows are driven the way a player drives them, through the screen's own key and mouse
handlers: a value typed and applied with Enter, another with Tab, one abandoned with Escape, one
clicked into and applied by clicking away, and a `[+]` press on top. One scene is left with a number
half typed, which is both a screenshot of that state and a check that nothing moved until it was
applied.

The last scenes swap in a 48x32x48 schematic with the camera inside it and turn the camera between
shots, which is what covers the region grid and the frustum culling - the structure the earlier
scenes use fits in one region. Each shot waits for the overlay to stop rebuilding first and reports
how many frames that took, so a rebuild that stopped being incremental would show up there.

`-Psmoketest=dataonly` skips world generation and runs only the parts that do not need a world,
which takes about a minute rather than several.

Disabled unless the flag is passed; delete `debug/SmokeTest.java` and the `loom { runs { ... } }`
block in `build.gradle` if you do not want it.

## Credit

Original mod by **Lunatrius**. This is a port; no license was distributed with the b1.7.3 release
that was converted, so check with the author before redistributing.
