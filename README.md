# DemoBox
DemoBox is a tool for creating interactive demos for mods, originally created for BlanketCon '23.
It uses plasmid, the library behind nuceleoid games, to manage virtual worlds safely,
allowing users to experience mods in survival mode without risking them breaking things in the overworld.

## Usage
To create a demo using demobox, first build a structure and store it with a structure block. 
This structure will spawn in your demo worlds centered at 0, 0.

As of 26.1, demos are first created using the `/demo create` command. This command opens a dialog letting your configure the demo.
You can then use `/demo open` to open it, or `/demo edit` to modify it. 

Editing demos requires the `demobox.edit` command. This should not be given to untrusted players,
as it permits them to run arbitrary commands with gamemaster permissions. Opening demos requires the `demobox.open` permission.
This one can generally be given to untrusted players unless you want to keep some demos inaccessible.

Demos can also be attached to signs using the `/demobox sign` command. It overwrites the content the targeted sign with a link to the demo.
Using the command requires the `demobox.sign` permission, but interacting with the sign itself doesn't require anything.

All commands except `/demobox leave` default to gamemaster/level 2 permissions unless their specific nodes are set.

<details>
<summary>Pre 26.1 instructions</summary>

Then you can run the `/demobox open <structure>` command to visit a demo world.
You can leave using the `/demobox leave` command or by using the `/game` command from plasmid.

The open command requires a permission level of 2 or the `demobox.open` permission node
because it can be used to create dimensions with arbitrary structures and that could cause problems.
The recommended way to give players access to demos is by using signs with click events.

The open command also accepts a position for where players spawn and a function to run during setup if you need to do something more advanced.

</details>

