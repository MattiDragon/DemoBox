# DemoBox
DemoBox is a tool for creating interactive demos for mods, originally created for blanketcon 23.
It uses plasmid, the library behind nuceleoid games, to manage virtual worlds safely,
allowing users to experience mods in survival mode without risking them breaking things in the overworld.

## Usage
To create a demo using demobox, first build a structure and store it with a structure block. 
This structure will spawn in your demo worlds centered at 0, 0.

Then you can run the `/demobox open <structure>` command to visit a demo world. 
You can leave using the `/demobox leave` command or by using the `/game` command from plasmid.

The open command requires a permission level of 2 or the `demobox.open` permission node
because it can be used to create dimensions with arbitrary structures and that could cause problems. 
The recommended way to give players access to demos is by using signs with click events.

The open command also accepts a position for where players spawn and a function to run during setup if you need to do something more advanced.
