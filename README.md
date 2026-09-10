# Schematica (Babric)

A port of Lunatrius' Schematica 1.2.0.10 for Minecraft Beta 1.7.3 from Risugami's ModLoader to
[Babric](https://babric.github.io/) (Fabric Loader for b1.7.3), using the
[Barn](https://github.com/babric/barn) mappings.

Load an MCEdit `.schematic` file and it is drawn over the world as a ghost you can build against,
with colour-coded boxes showing what is missing or wrong. You can also select a region of the world
and save it back out as a schematic. A material list says what the whole thing will take and counts
down as you gather it, on a screen or in the corner of the screen while you build. Verify holds the
build up against the schematic and says what is not placed, what was placed wrongly, and what is
standing in the way of the rest. Any block in a schematic can be swapped for another that leaves the
build standing, keeping which way it faces. In a creative single player world, one button builds the
whole thing where it stands. Several can be open at once, each standing in its own place. What you
had open is still open when you next log in, in the place you left it, for each world and server
separately.

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

The settings - transparency, the comparison boxes, how far they are inset, and whether pasting
clears the cells its schematic leaves empty - live on their own screen, reachable from the settings
key above, the **Settings** button on the move screen, and Mod Menu's **Configure** button. All
three open the same screen, so the key works with or without Mod Menu installed.

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

What names a world is known before the world turns up - a save folder as one is started, an address
as it is dialled - so it is a statement about where you were going rather than about what arrived. A
world that turns up from a direction nothing announced would otherwise be taken for the last one
that was, and a house pinned to a corner of your survival world would come back standing in the
middle of somebody's spawn, at the coordinates it had at home. So the two are held up against each
other: a world that belongs to a server can only be named by an address, and one that does not can
only be named by a save folder. Where they disagree nothing is remembered there at all and the log
says why, that being the only answer that cannot put a build into a world it was never in. The
address itself is taken off the connection rather than off the screen that dialled it, so a pack
that brings its own server list is still a server the mod can tell from every other one.

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

The swap itself is not taken on trust either. A world can be changed without the mod hearing about
it, the method that says so being another popular place, and the mod would then go on believing it
was in the world it was last told about - writing one world's note under another world's name, and
leaving a ghost standing where it has no business being. So the world in hand is compared against
the one last seen on every tick, and a change nobody announced is taken up a tick late rather than
never. That costs one rebuild and is also logged.

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

Under what a row needs is the same number written in stacks: seventy cobblestone is a stack and six,
which is what you are counting in while you fill a chest rather than what the list would otherwise
have you work out at every row. It is only there where it says something the count does not, so a
row under a full stack has none. The stack size is the item's own rather than sixty-four, which is
how a door, a bed, a sign and a bucket come to say nothing about stacks: none of them stack at all.

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

The number on a row is what is left of it, in the stacks you would carry it in: a hundred and
forty-eight cobblestone still to find reads `2x64 + 20`. The corner of the screen has room for one
number a row, so that number is written the way the pile would be packed rather than said twice, and
it comes down as you gather - back to a plain count once what is left of a row fits in one stack.
The material list has the room to do both, and puts the stacks under the count instead.

**HUD Sort** puts either the most or the fewest the schematic takes at the top. That is what it
takes, not what is left of it, so a row holds its place while you work on it: collect eleven of the
thirty redstone a build wants and it stays above the twenty cobblestone you have not touched instead
of dropping under it. A row only ever leaves this list by being finished.

The HUD shows as many rows as fit in half the screen, up to ten, with a count of what did not fit
underneath. It sits in the very corner when it is narrow enough to stay clear of the hotbar and
above the hearts when it is not, and F1 hides it along with the rest of the interface.

### Replacing blocks

**Replace Blocks** lists every kind of block the schematic is made of, with a **Replace** button on
each row. Press one and the next screen is everything that block could be instead; pick one of those
and every block of that kind becomes it, at once. The button is on the move screen, where it works
on the schematic the picker is pointed at, and on the load screen, where it works on the file
highlighted in the list - opening that file first if it is not open already.

The rows are not the material list's. That one counts the items you would go and fetch, so a door is
one door rather than its two halves and a pool of water is a bucket; this one counts what is in the
file, because a block has to be named as the block it is before it can be swapped for another. Red
wool and white wool are two rows, as they are in both lists. Stairs facing four different ways are
one row, since which way a stair turns is not what a stair is.

#### What can stand in for what

A schematic is a shape, and most of what makes it that shape is in the metadata: which way a stair
turns, which wall a torch hangs on, which half of a door this is. So every block belongs to a
family, and a swap only ever happens inside one - full cubes for full cubes, stairs for stairs,
slabs for slabs, rails for rails, doors for doors, plants for plants, pressure plates for pressure
plates. A family is not "looks similar": it is a promise that the metadata means the same thing to
everything in it, which is what lets the old value simply be carried over. Cobblestone stairs put
where wooden stairs were face the way the wooden ones did, each of them its own way, and an iron
door put where a wooden one was is still the top half of a door hinged the way that one was.

Glass and ice are in with stone and cobblestone, being the same shape - what the light does
afterwards is usually the point of making the swap. Sand and gravel are there too, and will fall
when the build goes up exactly as they would have if you had drawn them there in the first place.
The families are about a block's own shape and nothing else: replacing what a torch is hanging on
does not ask whether a torch will still hang on it.

Where the new block has no use for part of the old value it is dropped rather than carried - red
wool becoming stone is stone, not stone with a colour in it - and where the old block has nothing to
give, the new one gets its own default. Leaves are the case that keeps something invisible: the flag
that stops them rotting away survives becoming another kind of leaf.

Some swaps are refused rather than fudged. A rail with a bend in it cannot become a powered rail,
because a powered rail has no bend to be, and quietly straightening the track would be a worse
answer than not offering the swap. That is decided against the metadata your schematic is actually
holding, so a line of straight rails is offered the powered rail that a line with a corner in it is
not.

Blocks that carry a block entity - chests, furnaces, dispensers, signs, note blocks, jukeboxes,
spawners - are in no family at all, in either direction: the entity would be left behind at a
position that is no longer its block, a chest's contents hanging inside a wall of stone. They are
still listed, with the button greyed out, as is water, which you can carry in a bucket but cannot
sensibly swap for a wall.

Torches are the one pair of blocks that pass every test a family sets and are still kept apart. A
torch and a redstone torch are the same shape, hang on the same walls and mean the same thing by
their metadata - and one of them is a light while the other is a signal. A wall of torches swapped
for redstone torches is not the same build in another material, it is a different circuit, so
neither is ever offered the other. Nothing else in the game is torch-shaped, which leaves both rows
with the button greyed out.

Blocks nobody puts anywhere are not listed at all: the head of an extended piston, the block a
piston is halfway through pushing, fire, a portal. They are what came of building the thing rather
than what it was built out of, and there is nothing to be done with a row for one - the same reason
they have no material list row. So a piston in a schematic is one row, the piston, whether it was
saved holding its arm out or not.

Two blocks that are one block in two states are counted as one. A redstone torch is a redstone
torch whether the file caught it lit or not, a furnace is a furnace whether or not it was smelting,
and lit redstone ore is redstone ore. Which half a schematic holds is a circuit caught mid-tick
rather than anything you chose, and a list with two rows reading "Redstone Torch" in it is one you
cannot choose from. Replacing that one row reaches every block counted under it, whichever way the
circuit had them.

#### Saving

Replacing changes the schematic that is open, and the overlay redraws with it: the wall you are
stood in front of is cobblestone the moment you pick cobblestone. The file is untouched until **Save
to file**, which writes it back over the one it came from. That is deliberate - trying a colour
against a build in the world is the point, and most of what gets tried is not kept - but it does
mean an unsaved change is gone when the game closes, since a schematic comes back from its file and
not from the note. The line under the list says which of the two you are looking at. There is no
undo: the way back from a colour that looked better in the list is to pick the old one again, or to
leave without saving.

A schematic that has been rotated or mirrored saves the way it is standing and stops counting the
turn afterwards. The file is now what is on the screen, and saying otherwise would leave the note
this world keeps holding a rotation that has already been applied, and turn the build again on the
way back in.

### Deleting a schematic

**Delete** on the load screen takes the highlighted file away, on the second press rather than the
first. The first press turns the button into a warning and names the file under the list, and
pressing it again is the answer - there is no dialogue to click through and nothing to type. Choosing
another file is as good an answer as walking away, since the question was about the file that was
highlighted when it was asked, and so is leaving it alone for five seconds. A press landing within
half a second of the first is ignored: the second half of a double click cannot be an answer to a
question that was not on the screen when the first half was pressed.

Where the platform keeps deleted files somewhere, that is where the file goes - a schematic is hours
of somebody's building, and this is the one button in the mod that ends one. Where it does not, the
file is deleted outright. The log says which of the two happened.

A schematic that is already open stays open. The blocks are in memory and the ghost is standing in
the world, and deleting the file they came from is not an instruction to take the build off the
screen. It does mean nothing will bring it back next session, since what this world remembers is a
file name and there is no longer a file - and that **Save to file** on the replace screen would
write it back out again, which is the only way there is to undo this.

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

What gets written is what the ghost draws: the blocks the schematic asks for. What becomes of the
cells it asks for nothing in is the **Paste Air** setting, and it is a real question rather than an
oversight, because an empty cell can be read two ways. It can mean "no part of this build", which is
how the rest of the mod reads it - the overlay draws nothing there, the material list counts nothing
for it, easy place will not put anything in it. Or it can mean "and there is air here", which is how
the schematic was in fact standing in the world it was cut out of.

With the setting on, which is how the mod ships, the box the schematic covers is emptied before
anything is written into it, so what is left standing inside it afterwards is the build and nothing
else. A schematic dropped underground comes out with its rooms rather than with the hill they were
cut into still filling them. With it off, whatever is already there is left where it is, which is
what a build saved with room around it wants: dropped onto a hillside, the room around it is not a
box carved out of the hill.

Either way the clearing stops at the schematic's own edges. A paste covers the box the ghost was
drawing and not one cell more.

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

### Verifying a build

The **Verify** button on the move screen holds the schematic up against the world it is standing in
and lists everywhere the two disagree: what has not been placed, what has been placed wrongly, and
what is standing where the schematic wants nothing at all. It checks the one the picker is on, like
everything else on that screen.

The overlay has been drawing this comparison all along - the coloured boxes are the same question,
asked one cell at a time as the geometry is built. What it cannot do is count. At the end of a build
what is left is a handful of blocks somewhere in a hundred thousand, and a box you have to find
before you can see it is no help at all. So this is the same question asked of every cell at once,
with the answer written down rather than painted into the world.

Rows are grouped by the block and the way it is wrong, in the same order the material list uses -
most of it first, alphabetical between rows of the same size. Under each row is what is wrong with
it and where the nearest one of them is, so a row is somewhere to walk to rather than only something
to know. Each is in the colour of the box the overlay draws on it, so the row and the thing it is
about are the same colour before you have read either.

| Row              | What it means                                                    |
| ---------------- | ---------------------------------------------------------------- |
| Not placed       | The schematic asks for a block here and nothing is standing      |
| Wrong block      | Something else is standing here; the row says what               |
| Wrong way round  | The right block, turned or set differently from the schematic    |
| In the way       | The schematic asks for nothing here and something is standing    |

The last of those is the one the overlay has no way of showing you. The other three are drawn on the
block that ought to be there; a cell that should be empty has nothing to hang a box on, because what
is wrong with it is that it is full. It is also the row that decides whether a paste will land
cleanly - see **Paste Air** above - and the one that says a build is standing in the hill rather
than in the space cut for it.

**Check again** takes the reading afresh. The list is a snapshot on purpose: it is a walk of every
cell of the schematic against every cell of the world under it, which for a large schematic is a
million reads, and it is meant to hold still while you read it, note what is missing and go and fix
it. Nothing about it keeps up on its own.

Verify reads and never writes, which makes it the one thing next to Paste on that screen that works
on a server exactly as it does in single player.

#### What counts as wrong

What is compared is what somebody chose, not everything the game happens to be storing. A block the
game switches between two ids on its own is compared as the one that was put down - a lit furnace is
a furnace, a redstone torch is a redstone torch whichever way the circuit has it - and the parts of a
block's metadata that are the game's own business are masked off before the two are held against
each other.

So a door standing open is the same door, a lever thrown is the same lever, a button pressed is the
same button, a wire carrying power is the same wire, and leaves are the leaves of whichever tree
they came off however far along their decay check is. What is kept is everything that says something
about the build: which way a stair turns, what colour the wool is, how long a repeater waits, which
half of a door this is. Without that line a finished build reports itself broken the moment somebody
opens a door, and with it drawn too far a stair facing the wrong way goes unreported - which is the
thing the check exists to catch.

Water and lava are compared as the substance rather than as the flow. A pool saved into a schematic
and the same pool in the world will not agree about which cells of it are running, and a report
saying "wrong block: water, found water" would be telling the truth uselessly.

A block nobody places is read as an empty cell wherever it turns up, on either side of the
comparison. The head of an extended piston comes out of the piston, fire is lit, a portal is struck,
and the block a piston is halfway through pushing is a moment rather than a thing - none of them is
something to go and do or to undo, which is the same answer the replace screen gives when it leaves
them off the list of what a schematic is made of. So an extended piston that has not been built yet
is one thing to go and place rather than two, and an arm the circuit has pushed out into a cell the
schematic wants nothing in is no more a fault than the piston being out is. A block that somebody
really did put where the arm goes is still reported, as a block in the way, which is what it is.

Two kinds of cell are not judged at all, and are counted under the list rather than folded into it,
because a cell that was not checked is not a cell that was found to be right. One is a cell wanting a
block this version of the game does not have, which is a schematic written by a later one. The other
is a cell whose chunk the client has not been sent - reading it anyway gives air, and air read out of
a chunk nobody has been sent would report a finished build as one that had never been started. That
is what you get verifying a build from across the map, and the line under the list is what says so.

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
             BlockSwap          which blocks can stand in for which, and what a swap does to metadata
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
             BlockListScreen       what the three screens below share: rows, icons and scrolling
             BlockReplaceScreen    every kind of block in the schematic, each with a Replace button
             BlockChoiceScreen     what one of them could be swapped for
             SchematicVerifyScreen what is wrong with a build, held against its schematic
             four more Screens and a slider widget
compat/      ModMenuIntegration  optional, loaded only when Mod Menu asks for it
             CreativeMode        optional, asks BHCreative whether the player is in creative mode
mixin/       eight small hooks (see below)
SchematicaState    everything about the current session: the open schematics, and which is picked
OpenSchematic      one of them: what it is, where it stands, how it is turned and what is drawn
SchematicaConfig   the settings, and the keybinds it hands to vanilla
EasyPlace          what a right click is allowed to do while easy place is on
HotbarRestock      brings a block onto the hotbar, and waits for the server to agree
Sightline          walks the blocks along the line of sight, nearest first
SchematicPaste     writing a schematic into the world at once, and who is allowed to
SchematicVerify    a build held up against its schematic, and everywhere the two disagree
MaterialList       what a schematic is built out of, counted against an inventory
BlockPalette       what it is made of block by block, and the swapping of one of them for another
SchematicMemory    what was open in each world, kept between sessions
BlockItems         which item puts a block down, what to fetch for it, and what of it was chosen
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
| `ClientNetworkHandlerMixin` | constructor, transaction, chunk and multi-block packets | note which server is being dialled, so its world can be told apart; hear whether the server took an inventory swap; invalidate the overlay for changes the world never reports block by block |

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
  inventory against it - on a screen, or in a HUD in the corner listing only what is still short,
  with what each row comes to in the stacks you would carry it in as well as in blocks.
- **A build can be checked against its schematic.** The original drew the same comparison the
  overlay still draws, and could only ever draw it: to find what was left you had to go and look
  for a coloured box. **Verify** counts it instead - what is not placed, what is the wrong block,
  what is turned the wrong way, and what is standing where the schematic wants nothing, which is
  the one of the four the overlay has no way of showing at all.
- **Schematics can be deleted from the game.** The original could only ever add to the folder, so
  tidying one up meant leaving the game and finding it on disk. **Delete** on the load screen takes
  the highlighted file away on a second press, into the platform's wastebasket where there is one.
- **Several schematics can be open at once.** The original held one: loading a second closed the
  first, so working on a build made of two files meant swapping between them and putting each one
  back by hand every time. Up to eight are open here, all drawn, each in its own place and sliced to
  its own course, with a picker on the move screen choosing which one the controls are pointed at.
- **Blocks can be swapped for other blocks.** The original could move a schematic and nothing else,
  so changing what a build was made of meant editing the file in something that was not the game and
  loading it again. Any block in one can be swapped here for anything that would leave the build
  standing - the stairs keep facing the way they faced - and written back over the file.
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
its own rather than handed it over, and that one witness block to still be standing, the paste
having been made with **Paste Air** off. The paste is then made again with the setting on, over an
emptied box with the witness put back: this time the witness has to be gone, while a block just
outside the schematic's edges is still there. The count of blocks written is the same either way, so
nothing else in the run would notice which of the two had happened. The button itself is rendered
for real from a position over it, which is what both greys it out and draws the tooltip.

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

Replacing blocks is checked on a schematic written for the checking, since the cases that matter are
the awkward blocks and a structure holding all of them is easier to write down than to build. The
palette is asked to have a row per kind of block, to count each of them, to put two colours of one
block in two rows and one block facing two ways in one, and to have no row at all for air. It is
also asked for the rows it should not have: a redstone torch saved lit and one saved unlit counted
as two redstone torches on one row with none of its own for the second, lit redstone ore counted the
same way, and no row whatever for a piston head or for a block a piston is halfway through pushing,
with the piston that owns them both still a row of its own. Then each family is asked what it will
and will not offer: a full cube offered another full cube, one the light comes through and one of a
colour, and refused a stair, a chest and itself; stairs offered nothing but the other stairs and a
door nothing but the other door; a chest offered nothing whatever; a torch refused the redstone
torch, the unlit one behind it and everything else, and the redstone torch refused in its turn. The
rail is the one that has to be asked twice - a schematic with a bend in it is refused the powered
rail, and one of nothing but straight and sloped rails is offered it, which is the whole of what
deciding against the metadata a schematic is holding comes to. Then the metadata carried across a
swap is checked one case at a time: the way a stair faces and that it is still upside down, a door's
half and its hinge, a slab still laid upside down, the flag on a leaf, the current dropped from a
powered rail becoming a plain one, and a colour dropped going one way and given going the other.
Last the swap is applied to the schematic and the blocks read back - two stairs each still facing
their own way, the cobblestone beside them untouched, one colour of wool changed with the other left
as it was, and a row that counted two blocks under one name changing both of them.

The two screens are driven the way a player drives them, on a schematic and a file of their own so
that saving cannot write over the one the rest of the run is watching. A click on a row away from
its button does nothing, a click on the button opens the list of what could go there instead, and a
click on a row of that list swaps the blocks, marks the overlay for a rebuild, marks the schematic
as changed, and comes back to a list counted again. Save is checked to clear the change, and the
file read back off disk to hold the block that was put there and the stair it was not asked about,
still facing the way it did. Then the schematic is turned and saved again, and the file has to come
back the shape it was turned into with no turn left to apply - which is what stops the note this
world keeps from turning it a second time. Scene 17 is the replace screen and scene 18 the list of
what a block of gold could be, sixty rows and the only shot of one of these lists scrolling.

Deleting is checked on a file written to be deleted, and mostly by what does not happen to it: it is
still there after one press, after a press landing too soon after that one to be an answer to it,
after the warning has been walked away from by highlighting something else, and after it has been
left alone long enough to expire. Only two presses that were meant take it, after which the list is
checked to have been taken again without it and nothing to be left highlighted. Scene 19 is the
screen with a warning up, which is the only shot there is of one.

Stacks are arithmetic and are checked as arithmetic - a pile over a stack, exactly a stack, two of
them, one that does not fill one, something that does not stack at all, and a number big enough to
be grouped - and then once more through a row, where the stack size is the item's own rather than
one the test chose. The HUD says the same thing with the one number it has room for, so it is asked
what a row reads with more than a stack left, with less, and for a pile that does not stack at all,
and checked to come back down to a plain count as the row is gathered. Scene 20 is a material list
built to hold every way a row can be written at once, and scene 21 the corner of the screen counting
the same schematic.

Scene 22 is the replace screen over a schematic written to be mostly rows that should not be there:
a piston with its arm out, another block halfway through being pushed, a redstone torch saved twice
with the circuit each way, and a plain torch. Six kinds of block go in and four rows come out, with
Replace greyed on both kinds of torch - the shot is of what is missing from it.

Verifying is checked on a schematic built for it, because the four ways a cell can be wrong have to
be arranged one at a time. An emptied box is asked about first and checked to come back as the whole
schematic missing under one heading, with the floor's row counting every block of it and pointing at
the nearest one of them in all three axes. The schematic is then pasted into that box and the check
asked again, where nothing at all may be wrong with a build written straight out of the file it is
being held against. Then it is taken apart one fault at a time: a block removed is a row saying it is
not there, the wrong block in its place is a different row naming what is standing there instead, a
stair turned is a row about metadata rather than about the block, and something dropped into a cell
the schematic wants nothing in is the fourth kind. Each is checked to be the only fault in the box,
so a rule that started firing twice would be named rather than absorbed.

Two things are checked not to be faults at all - a redstone torch the circuit has switched off, and a
wire with power running through it - since a check that called either of them wrong would hand back a
list of nothing but them. Which of a block's metadata anybody chose is then checked as a table on its
own, both halves of it: a stair keeps its facing and wool its colour, while an open door, a thrown
lever, a pushed piston, a decaying leaf and a growing crop each come back as the block somebody put
there.

The piston gets a schematic and a box of its own, being the one thing in the game a schematic holds
two blocks of and a player places one of. An extended piston that has not been built yet has to come
back as one thing to go and find with no row at all for the arm, and pasted - held out by a torch
that goes in with it, since a piston pulling itself in spends the next couple of ticks as a block
halfway through moving - it has to come back with nothing wrong at all. Then an arm is stood in a
cell the schematic wants nothing in and checked not to be in the way of anything, and a cobblestone
in the same cell checked to be.

The screen is opened over a world with exactly one thing wrong in it, and that one thing is put right
while the screen is still up: the list has to be unchanged until **Check again** is clicked, which is
the snapshot being a snapshot on purpose. Scene 23 is that screen over a build damaged in all four
ways at once - four kinds of row, four colours and a second line under each are a layout rather than
a number, and a screenshot is the only thing that can be looked at.

Remembering what was open is checked from both ends. The name the mod gave the test world on its own
is asserted to be the folder that world was started from, and on a server the address the connection
was opened with, since only a real join can answer for either. The hook that reads that address is
the one thing here a single player run never reaches, so the class it lives in is loaded on purpose
and looked at for the handler: a mixin is applied when its target class loads, and an injection
point that has stopped matching throws at that moment - which in a game is the moment Connect is
pressed and here is a line in the log. The rest is driven by hand, because
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
hand, with the world in hand asked to read as a server's for as long as the pretence lasts, since
the mod now holds one against the other. That check has an opposite: a schematic is left in a save
folder's world, and then a world that belongs to a server arrives with nothing having said whose.
The last word on where anyone was going is the save folder just left, and it is checked to be worth
nothing - the world goes unnamed, nothing is put into it, it is still unnamed twenty ticks later,
and the note is checked to be sitting untouched for the world it was really taken in. That is the
one that used to end with a house standing in the middle of somebody's spawn. One world in the set
is left deliberately silent - named and then never
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
`options.txt` both took it, and that Escape gives the rebind up rather than binding Escape. The
settings screen gets the same treatment for its **Paste Air** toggle, which is the only way into a
setting nothing else on screen reports: it is clicked off and on again, and both the value
underneath and the label on top are read back each time.

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
