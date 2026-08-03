extends Node
## Outil de développement : lance le jeu, prend des captures, quitte.
##
##     xvfb-run -a godot --path game --rendering-driver opengl3 \
##       res://tests/screenshot.tscn
##
## Sert à vérifier le rendu sans appareil ni écran — en CI comme en local. Les
## captures atterrissent dans le dossier `user://` du projet.

const WARMUP_FRAMES := 60
const SHOTS := [
	{"name": "01_village", "setup": "village"},
	{"name": "02_village_dense", "setup": "dense"},
	{"name": "03_raid", "setup": "raid"},
]

var _main: Node


func _ready() -> void:
	await get_tree().process_frame
	for shot: Dictionary in SHOTS:
		await _capture(String(shot["name"]), String(shot["setup"]))
	print("Captures écrites dans : %s" % ProjectSettings.globalize_path("user://"))
	get_tree().quit(0)


func _capture(name: String, setup: String) -> void:
	_reset_main()
	_apply_setup(setup)
	await get_tree().process_frame
	if _main != null and _main.has_method("_leave_raid"):
		pass
	for i in range(WARMUP_FRAMES):
		await get_tree().process_frame
	var img := get_viewport().get_texture().get_image()
	var path := "user://%s.png" % name
	img.save_png(path)
	print("  → %s (%dx%d)" % [path, img.get_width(), img.get_height()])


func _reset_main() -> void:
	if _main != null and is_instance_valid(_main):
		_main.queue_free()
		_main = null


func _apply_setup(setup: String) -> void:
	Game.new_game()
	var s := Game.state
	match setup:
		"village":
			pass
		"dense", "raid":
			# Un village de milieu de partie : c'est ce qu'on veut regarder,
			# pas trois cabanes.
			s.set_bastion_level(9)
			s.resources = {"wood": 300000, "stone": 300000, "iron": 300000,
					"gold": 300000, "essence": 100}
			var mid: int = s.village.width / 2
			var plan := [
				["quarry", mid + 4, mid - 6], ["sawmill", mid - 8, mid + 2],
				["ironmine", mid + 5, mid + 4], ["mill", mid - 9, mid - 5],
				["house", mid + 1, mid - 7], ["house", mid - 4, mid + 6],
				["house", mid + 7, mid + 1], ["warehouse", mid + 5, mid - 2],
				["vault", mid - 7, mid - 1], ["barracks", mid - 5, mid - 8],
				["archer_camp", mid + 2, mid + 5], ["forge", mid - 2, mid - 9],
				["library", mid + 8, mid - 5], ["archer_tower", mid - 3, mid - 3],
				["archer_tower", mid + 4, mid + 1], ["cannon", mid - 4, mid + 2],
				["cannon", mid + 3, mid - 4], ["mortar", mid, mid + 5],
				["statue", mid + 1, mid + 2],
			]
			for entry: Array in plan:
				Game.build(String(entry[0]), int(entry[1]), int(entry[2]))
			# Une enceinte, pour que les murs soient visibles.
			for d in range(-7, 8):
				Game.build("wall", mid + d, mid - 5)
				Game.build("wall", mid + d, mid + 8)
			Game.foreman_apply()
			Game.fill_army()
			# On laisse la production s'accumuler pour que les badges de récolte
			# soient visibles sur la capture.
			Economy.tick(Game.state, Game.tables, 900.0)

	_main = load("res://scenes/main.tscn").instantiate()
	add_child(_main)

	if setup == "raid":
		await get_tree().process_frame
		Game.state.set_bastion_level(9)
		if Game.start_campaign_raid(0):
			_main.call("_enter_raid")
			await get_tree().process_frame
			# On déploie quelques troupes pour que la capture montre un combat.
			var view: RaidView = _main.get("raid_view")
			if view != null:
				var placed := 0
				for id: String in view.remaining_army.keys():
					for i in range(mini(8, int(view.remaining_army[id]))):
						view.deploy(id, Vector2i(6 + placed % 22, 8 + (placed % 3)))
						placed += 1
				# On laisse le combat s'engager : sans ça, la capture montre des
				# troupes intactes et aucune barre de vie.
				for f in range(150):
					await get_tree().process_frame
