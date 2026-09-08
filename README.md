# Schematica (Babric)

A port of Lunatrius' Schematica 1.2.0.10 for Minecraft Beta 1.7.3 from Risugami's ModLoader to
[Babric](https://babric.github.io/) (Fabric Loader for b1.7.3), using the
[Barn](https://github.com/babric/barn) mappings.

Load an MCEdit `.schematic` file and it is drawn over the world as a ghost you can build against,
with colour-coded boxes showing what is missing or wrong. You can also select a region of the world
and save it back out as a schematic. A material list says what the whole thing will take and counts
down as you gather it, on a screen or in the corner of the screen while you build. In a creative
single player world, one button builds the whole thing where it stands. Several schematics can be
open at once, each standing in its own place. What you had open is still open when you next log in,
in the place you left it, for each world and server separately.

## Using it

| Key            | Controls entry        | Action                                          |
| -------------- | --------------------- | ----------------------------------------------- |
| Numpad `/`     | Load Schematic        | Open a schematic, alongside any already open     |
| Numpad `*`     | Save Selection        | Save the current selection                      |
| Numpad `-`     | Move Schematic        | Move / rotate / mirror one, and pick which      |
| Numpad `+`     | Show/Hide Schematic   | Show or hide every open schematic               |
| Numpad `.`     | Schematica Settings   | Open the settings screen                        |
| Page Up        | Schematic Layer Up    | Show the next layer up                          |
| Page Down      | Schematic Layer Down  | Show the next layer down                        |
| Numpad `0`     | Easy Place            | Turn easy place on or off                       |
| Numpad `5`     | Material List         | What the picked schematic is built out of       |

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

### Several at once

Up to eight schematics can be open together, each standing wherever you put it, and all of them are
drawn. A build is rarely one file: the farm and the roof that goes over it are two, and the tower
you are half way through is a third.

Loading adds one rather than swapping the last one out. The picker along the top of the move screen
is where the set is: it names the schematic the screen is pointed at and how many there are, and
clicking it drops a list of all of them - numbered, the one you are on in yellow, the ones you have
hidden in grey. Beside it, **Open...** goes to the load screen and **Close** takes the one you are
on away. Loading past eight is refused and the button greys out until you close one.

Everything else on that screen is about the one schematic the picker is on: where it stands, which
way it is turned, which layer of it is showing, whether it is drawn at all, the material list and
the paste. Two schematics can be sliced to different courses and turned different ways at once, and
neither knows about the other.

Three things are deliberately about the whole set instead:

- **The show/hide key** hides all of them, and brings all of them back. It is the key you reach for
  when the ghosts are in the way of what you have built, and half of them going is no use then. The
  move screen's **Hide** button is the one that hides a single schematic.
- **Easy place** builds whichever schematic you are aiming at, so you can work on two at once
  without going back to a screen between them. Where two overlap, the one the picker is on wins.
- **Taking blocks off the hotbar** counts every open schematic, since the next click may want back
  a slot holding something from the build next door.

The **-- No schematic --** entry at the top of the load list still means nothing, and closes the one
you are on - the same thing the **Close** button does.

### Picking up where you left off

Whatever you had open is still open when you come back to it. Log off, shut the game down, come back
a week later: the ghosts are standing where you left them, turned the way you turned them and sliced
to the layers you were working on - or hidden, if that is how you left them. All of them come back,
in the order you opened them, with the picker on the one it was on.

Each world remembers its own, because a schematic pinned to a corner of one world means nothing in
another. A single player world is told apart by its save folder and a server by the address you
dialled, and the dimension counts as well - a build sits at coordinates in the overworld that are
somewhere else entirely in the Nether, so the two ends of a portal keep separate notes.

Closing a schematic is what forgets that one, using the **Close** button you would use anyway. The
note is kept up to date as you build rather than written only on the way out, so a game that is
killed rather than closed still remembers. Notes live in `config/schematica-worlds.properties`,
keyed by world and then by which schematic in it; deleting a world's lines by hand forgets it too. A
file written by a version that could only hold one schematic per world is still read, as the first
of a set.

The overlay compares the schematic against the world, so it is only ever as good as the world was
when it was built - and a world does not always say when it changes. A single player world is
prepared with the game already running: it ticks and draws for seconds while every block in it still
reads as air. So a schematic waits for the world to say it has finished loading before it is put
back, and on top of that the mod reads the world back one chunk column per tick and rebuilds
anything that has changed underneath it. That covers a world arriving late, chunks coming and going
as you walk about, and anything else that writes into a chunk without a word.

That wait is a courtesy, not a condition. In a pack, another mod can reach the same world swap and
leave the mod never told the world arrived; waiting on that would mean waiting forever, and a
feature that quietly stops remembering anything is worse than one that redraws an overlay once. So
a world is written down from the moment it can be named, whoever else is in the way, and a schematic
that has been waiting three seconds for an announcement that is not coming goes back anyway - the
watcher above is what makes that safe. It says so in the log when it happens.

### Layers

The overlay can be cut down to a single Y slice, which is how you build a large schematic a course
at a time. The layer keys step it without opening anything, and the move screen's `[-] [+]` pair
does the same with the current layer shown between them. Stepping below the bottom layer goes back
to showing all of them, and both directions stop at the ends rather than wrapping.

A schematic over 125,000 blocks opens on layer 1 rather than all layers, since that is where the
overlay is cheap enough to fly around in.

### Material list

Everything the picked schematic is built out of, in one list: what it takes, how much of it you are
carrying and how much is still to be found. The **Materials** button on the move screen and Numpad
`5` both open it. With several open it is the one the picker is on that is counted, so the list
follows what you are working on.

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

**HUD Sort** puts either the most or the fewest the schematic takes at the top. That is what it
takes, not what is left of it, so a row holds its place while you work on it: collect eleven of the
thirty redstone a build wants and it stays above the twenty cobblestone you have not touched instead
of dropping under it. A row only ever leaves this list by being finished.

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

### Pasting it into the world

The **Paste** button on the move screen writes the whole schematic into the world where the ghost is
standing. Everything the overlay was drawing is simply there. It pastes the one the picker is on,
like everything else on that screen, so several open schematics go down one at a time.

It is offered in a single player world, in creative mode, and nowhere else. On a server the world
you are drawing on is not yours to write into - blocks put into the client's copy of it would be a
lie the next chunk update takes back - and in survival it would hand over a building nobody
gathered. Beta 1.7.3 has no creative mode of its own, so the answer comes from
[BHCreative](https://github.com/paulevsGitch/BHCreative); see below.

Where any of that is not true the button is greyed out, and hovering it says which of the reasons it
is rather than leaving you to work it out.

What gets written is what the ghost draws: the blocks the schematic asks for, and nothing else. A
cell the schematic leaves empty is left alone rather than cleared, so a build dropped onto a
hillside does not carve the hill out from under itself. Empty means "no part of this build"
everywhere else in the mod - the overlay draws nothing there, the material list counts nothing for
it, easy place will not put anything in it - and pasting reads it the same way.

It goes down in two passes: everything that can be hung on, and then everything that hangs. A block
is told it has landed the moment it is written, and some of them look around when they hear it - a
torch checks that it still has a wall, and if it has not, takes itself down and drops on the floor
as an item. Writing every wall before any torch means the wall is always there to be found.

Reading order is not enough on its own, which is what makes this worth a pass of its own rather than
a rule of thumb. A redstone wire landing tells the wire next to it, and that one tells all of *its*
neighbours, so a torch two blocks clear of the wire that arrived can still be asked the question
before its own wall has been written.

Neighbours are not notified as each block lands, only once all of it is down, so sand does not fall
through floors that are not laid yet. Each block's own placement still runs, so water flows and
gravel looks at the ground under it once the whole thing is standing.

Then anything a block decided for itself on the way in is put back to what the schematic says. A
plain torch re-reads its own facing from whichever neighbour is solid, so one with a wall on both
sides would otherwise come out facing the wrong one.

The pistons go in last of all, once everything they read is there. A piston is the one block that
reads the circuit around it the instant it lands rather than waiting to be told about it, and one
saved extended that finds nothing powering it throws its arm off and pulls itself back in. In
reading order it reads too early: a farm with a torch on either side of every piston comes out with
half of them retracted - whichever half had its torch on the far side, written a moment later -
each with an arm stranded on top of it, a block no player can obtain and nothing will ever take
away. Left until the circuit is finished and forced back to the state it was saved in, every piston
gets the answer the saved build gave it. Each arm goes in just before the piston that owns it, so
one that does decide to pull in takes its own arm with it as it goes.

It happens in one go rather than as a job running in the background, which it can afford to: 42,624
blocks go down in 31 ms in the dev run, with the lighting the game queues behind them settling over
the next few ticks. The ghost disappears as it lands, because there is nothing left for it to draw -
every block it was asking for is now standing there.

### Coordinates

Every coordinate on the move and save screens can be typed as well as nudged. Click the number,
type over it and press **Enter**; **Tab** steps to the next one, applying the one you are leaving,
and clicking anywhere else applies it too. **Escape** abandons what you were typing and gives the
field up, so a second press closes the screen as usual.

Typing a digit into a number you have just clicked replaces it, the way it does anywhere else.
**Backspace** does not: it rubs out one digit and leaves the rest to be typed onto, since nothing is
drawn as selected and taking the whole number away just looks like the field ate it.

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

That 8 ms is the whole frame's budget, not each schematic's. The dirty regions of everything open go
into one queue sorted by distance from the camera, so the work in front of you is done first
whichever build it belongs to, and a frame costs the same whether that work came from one schematic
or six. Culling is shared the same way: the view planes are read once, in world coordinates, and
every schematic is tested against them wherever it happens to be standing.

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

## BHCreative

Beta 1.7.3 has no creative mode. It arrived in Beta 1.8, so in this version it is something a mod
adds, and the one nearly every b1.7.3 pack adds it with is
[BHCreative](https://github.com/paulevsGitch/BHCreative).

Only the paste button needs an answer from it, and it is a dependency of that button and of nothing
else. The mod is not compiled against - which would have pulled StationAPI in behind it, since
BHCreative is built on it, and made a build of this one need both - but reached by reflection from
`compat/CreativeMode`. With BHCreative absent that answers "not in creative mode", the paste button
greys out and says so, and every other part of the mod carries on exactly as it did.

Reflection is the only way in even with the jar present: BHCreative hangs the flag off the player
through an interface its own mixin adds to the player class, so there is nothing there to compile
against. The name it goes in under is the mod's own and is never remapped, which is what makes
looking it up by string safe. If a later version moves it, the log says so on startup rather than
leaving a button greyed out for no reason anyone can see.

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
             SchematicPickerWidget which of the open schematics the move screen is pointed at
             four more Screens and a slider widget
compat/      ModMenuIntegration  optional, loaded only when Mod Menu asks for it
             CreativeMode        optional, asks BHCreative whether the player is in creative mode
mixin/       eight small hooks (see below)
SchematicaState    everything about the current session: the open schematics, and which is picked
OpenSchematic      one of them: what it is, where it stands, how it is turned and what is drawn
SchematicaConfig   the render settings, and the keybinds it hands to vanilla
EasyPlace          what a right click is allowed to do while easy place is on
HotbarRestock      brings a block onto the hotbar, and waits for the server to agree
Sightline          walks the blocks along the line of sight, nearest first
SchematicPaste     writing a schematic into the world at once, and who is allowed to
MaterialList       what a schematic is built out of, counted against an inventory
SchematicMemory    what was open in each world, kept between sessions
BlockItems         which item puts a block down, and what you would go and fetch for it
```

`Schematic` is deliberately free of world and rendering state, which is what lets `MaterialList`
count one without a world in front of it - and what a future printer or format converter would
work on the same way.

### The mixins

| Mixin                       | Target                          | Why                                                            |
| --------------------------- | ------------------------------- | -------------------------------------------------------------- |
| `MinecraftMixin`            | `init`, `tick`, world start, change and ready, use | initialise after the GL context exists; poll keys; name the single player world; reset state; hear that the world is readable, if anything says so; easy place |
| `GameOptionsMixin`          | `GameOptions.load`              | add the mod's keys to the list Controls and options.txt walk    |
| `GameRendererMixin`         | `class_555.method_1847` (weather) | the one point inside the world pass with the camera set up      |
| `InGameHudMixin`            | `InGameHud.render`              | draw the info HUD after it, under whatever screen is open       |
| `WorldMixin`                | `World.method_243`              | invalidate the overlay when a block inside it changes           |
| `TranslationStorageAccessor`| `TranslationStorage`            | merge this mod's language file into the vanilla table           |
| `ClientNetworkHandlerMixin` | transaction, chunk and multi-block packets | hear whether the server took an inventory swap; invalidate the overlay for changes the world never reports block by block |
| `ConnectScreenMixin`        | `ConnectScreen` constructor     | note which server is being dialled, so its world can be told apart |

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
- **It can build it for you.** The original only ever drew a schematic. In a creative single player
  world the move screen has a **Paste** button that writes the whole thing into the world where the
  ghost is standing, and greys itself out with a reason anywhere that would not be right. Creative
  mode is not something this version of the game has, so the answer is asked of BHCreative through
  reflection rather than compiled against - see above.
- **A material list.** The original drew a schematic and left working out what it would take to
  build entirely to you. This counts it, in the items you would go and fetch, and counts your
  inventory against it - on a screen, or in a HUD in the corner listing only what is still short.
- **Several schematics can be open at once.** The original held one: loading a second closed the
  first, so working on a build made of two files meant swapping between them and putting each one
  back by hand every time. Up to eight are open here, all drawn, each in its own place and sliced to
  its own course, with a picker on the move screen choosing which one the controls are pointed at.
- **What was open is remembered per world.** The original loaded nothing on its own: every session
  started empty, and putting a schematic back where it had been was done by hand from the numbers
  you had written down. This writes the note itself, one per save folder, server address and
  dimension, and the note holds everything that was open rather than one of them.
- **Rotate and mirror move block entities.** The original left signs and chests at their old
  coordinates after a transform.
- **A fresh dimension backs `SchematicWorld`.** The original passed the live world's dimension to
  `World`'s copy constructor, which re-pointed that dimension (and its biome source) at the
  schematic world.
- **Block ids above 255 are dropped to air with a warning** instead of throwing per block while
  rendering, so schematics from later versions load with whatever b1.7.3 can represent. Saving drops
  them the same way rather than truncating the id into some other block.
- **The overlay is invalidated by any block change inside it**, not just the local player's own
  interactions, so it stays correct on servers. That includes the two kinds a server never reports
  block by block: whole chunks as they arrive, which is what lets a schematic be put back the moment
  a server hands over the world, and the multi-block packet it sends whenever more than one block in
  a chunk moved in a tick - which is most of them once other people are building alongside you.
- **And the overlay reads the world back**, one chunk column per tick, so a change nobody announced
  at all is noticed within a second or two rather than never. A world does not always say when it
  changes: a single player world ticks and draws for seconds while it is still being handed over,
  reading as air the whole time, and anything writing into a chunk directly says nothing either.
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

What hangs on a wall gets a check of its own, built from the case that found the fault: a redstone
torch on the east face of a block, with a run of wire beside it, and a plain torch walled on both
sides. The torch is checked to be standing, on the wall it was saved against, with the item count in
the world checked to be exactly what it was before - a torch that came off is a torch lying on the
floor, and counting the floor is the only way to see that rather than infer it.

Pistons get their own check for the same reason, and from the same starting point: one held out by a
torch written after it, and one with nothing holding it out at all. The first is checked to be a
piston still, still out, with its arm on the end of it; the second to have taken its arm in with it,
since an arm left standing over a piston that has pulled in is a block no player can obtain.

Pasting is checked from both sides of the gate in front of it. The gate is asked with nothing loaded,
in a single player world and on a server, and each answer asserted by name; the refusal goes through
the same call the button makes, and the world is checked to be untouched after it. The writing
underneath is then driven directly, since a plain dev run has no creative mode in it to get past the
gate with. The schematic is parked over an emptied box of its own with one block left standing in a
cell it wants nothing in, and afterwards every block it asks for is checked to be standing and turned
the way it was saved, the sign to have arrived with its text, the schematic to have kept a sign of
its own rather than handed it over, and that one witness block to still be there - which is the whole
difference between pasting a build and pasting a box of air, and is not something a count of blocks
written could show. The button itself is rendered for real from a position over it, which is what
both greys it out and draws the tooltip.

Drop BHCreative's jar into `run/mods` and add `-PstationApi`, which it needs, and the rest of it runs
too: the player is stepped into creative mode through the mod's own setter, the gate is asked again,
the button is rendered again and checked to be live, and the paste is made through the same call the
button makes rather than through the writing under it. That is the only check there is that the
bridge to BHCreative still reaches - it is a class name and a method name held in two strings, and
nothing about a build would ever notice either of them going stale.

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
is still short, and part of a row is gathered and every row checked to be exactly where it was, with
the row now needing the least still at the top. The buttons under the material list are then clicked
for real and the HUD asked what it would draw, which is what joins the two halves. Scene 8 is a
screenshot of it over the world and scene 9 one of it behind an open screen, since whether it draws
is not something the checks can say.

Several schematics at once is checked as a set rather than as a screen. A second is opened and the
first asserted to be still loaded and still where it was; each is asked which of them answers for a
given position, which is the question easy place puts to the set; and turning one, slicing one and
hiding one are each checked to have reached that one and nothing else. The show/hide key is checked
to take all of them and bring all of them back, including one that had been hidden on its own. Then
the cap is filled to the last slot, the one past it refused, and the set closed down one at a time
back to the single empty slot a session starts with - which is the state the rest of the run needs,
so the check and the tidying up are the same thing. Scene 16 is a screenshot of the move screen with
two open and the picker dropped over it, since whether the list draws over the buttons it covers or
under them is not something a check can say.

Remembering what was open is checked from both ends. The name the mod gave the test world on its own
is asserted to be the folder that world was started from, and on a server the address the connect
screen was handed, since only a real join can answer for either. The rest is driven by hand, because
one run of the game cannot really log off and come back: a schematic is left open in one world and
not found in another, the file is read back off disk the way a freshly started game reads it, a
schematic moved while playing is checked to have been written down where it stopped, a hand-written
file says the overworld gets the overworld note and not the Nether one, and closing the schematic is
checked to forget it. A world left holding three of them is checked to come back holding all three,
in the order they were opened, each in its own place and slice, with the picker on the one it was
on, and closing one is checked to forget that one and no more. The shape of the file gets a check of
its own against a server address with four numbers in it, since each schematic is written under the
number of its place in the world and a world named by an address is already full of dots and ends in
a number: both the old one-schematic line and a pair of new ones are read back from that address by
hand. One world in the set is left deliberately silent - named and then never
announced as ready, the way a world arrives when another mod has got to the swap first - and it is
checked to be worked out anyway, to give up waiting rather than wait forever, and to be written down
like any other; every other check says the announcement out loud, which is how a pack that swallowed
it went unnoticed. Then the run does log off for real - it leaves the world with a schematic
parked at coordinates of its own and starts the same world again, with nothing driven by hand from
there on, and asks for the file, the place, the turn and the layer back.

The multiplayer run does the same in the small: it leaves the world and picks it straight back up
without waiting out a second handshake, which is the same three calls a real return makes, and asks
for the schematic back into a world the client does not own.

The turn is checked on its own, since the note holds a flip and a count of quarter turns rather than
the clicks that got there: eighteen runs of rotations and mirrors, some long enough to come back on
themselves, are each worked through, and the pair each lands on is handed to a fresh copy of that
schematic, and the two compared block for block.

Putting a schematic back is checked once more with the game actually closed and opened again, which
is the only way to meet the order a cold start does things in. `-Psmoketest` leaves a note pointing
at the structure it built; `-Psmoketest=restore` starts into that world, and rather than trusting a
count it asks the overlay what it is drawing, makes it work the comparison out again now everything
has arrived, and compares. The two can only differ if the first answer was drawn against a world
that was not there yet. Then the world is taken out from under the overlay by writing into the chunk
directly - no block change, nothing announced, nothing for any hook to hear - and the overlay has to
have noticed on its own by the time it is asked again. `-Psmoketest=mprestore` is the same pair
against a real server, coming back to a lump of the server's own world saved and parked exactly
where it came from, so an overlay that is right has nothing at all to draw.

The two kinds of change a server does not report block by block are checked against a schematic that
has just been drawn, so anything marked was marked by the thing under test. A chunk landing on the
overlay marks the part of it the chunk covers and one landing anywhere else marks nothing; a
multi-block packet marks the block it names and nothing for one aimed at another chunk, at a
position over the top of the overlay, or carrying no changes at all - which is what says the packed
positions in it are being read as the positions they mean.

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
clicked into and applied by clicking away, and a `[+]` press on top. Backspace gets its own run
through: one digit off a number just clicked into, the digit typed back on, the whole number rubbed
out and a new one typed into the empty field, and Enter on an empty field leaving the value alone.
One scene is left with a number half typed, which is both a screenshot of that state and a check
that nothing moved until it was applied.

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
