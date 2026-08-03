extends Node
## Point d'entrée : monte le monde 3D, l'interface, et pilote les écrans.
##
## Toute la scène est construite par code — il n'y a qu'un seul .tscn dans le
## projet. C'est délibéré : les scènes Godot fusionnent mal en Git, et une
## interface générée reste lisible en diff.

enum Screen { VILLAGE, RAID }

var world: Node3D
var camera: IsoCamera
var village_view: VillageView
var raid_view: RaidView

var ui_layer: CanvasLayer
var hud: HUD
var raid_hud: RaidHUD
var overlay: Control
var village_overlay: VillageOverlay
var combat_overlay: CombatOverlay
var quest_bar: QuestBar

var screen: int = Screen.VILLAGE


func _ready() -> void:
	_build_world()
	_build_village_screen()
	_build_hud()
	get_window().min_size = Vector2i(360, 640)


# --------------------------------------------------------------------- monde

func _build_world() -> void:
	world = Node3D.new()
	add_child(world)

	var env := WorldEnvironment.new()
	var e := Environment.new()
	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color("5b8fc9")
	sky_mat.sky_horizon_color = Color("cfe4f0")
	sky_mat.ground_bottom_color = Color("6b6e63")
	sky_mat.ground_horizon_color = Color("aeb3a5")
	sky_mat.sun_angle_max = 24.0
	var sky := Sky.new()
	sky.sky_material = sky_mat
	e.background_mode = Environment.BG_SKY
	e.sky = sky
	e.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	e.ambient_light_energy = 0.55
	# Brouillard volontairement très léger : il adoucit l'horizon, il ne doit
	# pas délaver le village. Trop dense, il tue la saturation qui fait tout
	# l'intérêt du low poly.
	e.fog_enabled = true
	e.fog_light_color = Color("c3d8e4")
	e.fog_density = 0.0012
	e.fog_sky_affect = 0.2
	e.tonemap_mode = Environment.TONE_MAPPER_LINEAR
	e.adjustment_enabled = true
	e.adjustment_saturation = 1.0
	e.adjustment_contrast = 1.02
	env.environment = e
	world.add_child(env)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-52, -128, 0)
	sun.light_energy = 1.45
	sun.light_color = Color("fff2dc")
	sun.shadow_enabled = true
	sun.directional_shadow_max_distance = 60.0
	sun.shadow_bias = 0.03
	world.add_child(sun)

	camera = IsoCamera.new()
	world.add_child(camera)


func _build_village_screen() -> void:
	village_view = VillageView.new()
	world.add_child(village_view)
	village_view.setup(Game.state.village, Game.tables, camera)
	village_view.building_tapped.connect(_on_building_tapped)
	Game.village_changed.connect(func() -> void:
		if screen == Screen.VILLAGE:
			village_view.refresh())


func _build_hud() -> void:
	ui_layer = CanvasLayer.new()
	add_child(ui_layer)
	hud = HUD.new()
	ui_layer.add_child(hud)
	hud.build_pressed.connect(_open_build)
	hud.foreman_pressed.connect(_run_foreman)
	hud.army_pressed.connect(_open_army)
	hud.attack_pressed.connect(_open_campaign)
	hud.bastion_pressed.connect(_open_bastion)
	hud.shop_pressed.connect(_open_shop)
	hud.collect_pressed.connect(func() -> void: Game.collect_all())
	hud.hint_pressed.connect(_on_hint)

	quest_bar = QuestBar.new()
	ui_layer.add_child(quest_bar)
	quest_bar.claim_pressed.connect(func() -> void:
		Game.claim_quest()
		hud.refresh())
	quest_bar.details_pressed.connect(_open_quests)

	# Badges de récolte : posés sous le HUD pour ne jamais masquer les boutons.
	village_overlay = VillageOverlay.new(camera)
	ui_layer.add_child(village_overlay)
	ui_layer.move_child(village_overlay, 0)


# ------------------------------------------------------------------- entrées

func _unhandled_input(event: InputEvent) -> void:
	## L'interface consomme ses propres événements (mouse_filter STOP), donc
	## tout ce qui arrive ici est un geste sur le terrain.
	if overlay != null and is_instance_valid(overlay) and overlay.visible:
		return
	if screen == Screen.VILLAGE:
		village_view.handle_input(event)
	else:
		_raid_input(event)


func _on_hint(action: String) -> void:
	match action:
		"build": _open_build()
		"foreman": _run_foreman()
		"army": _open_army()
		"attack": _open_campaign()
		"bastion": _open_bastion()
		"shop": _open_shop()
		"collect": Game.collect_all()


# ------------------------------------------------------------------ panneaux

func _show_overlay(o: Control) -> void:
	if overlay != null and is_instance_valid(overlay):
		overlay.queue_free()
	overlay = o
	ui_layer.add_child(o)
	if o.has_method("open"):
		o.call("open")


func _open_build() -> void:
	if screen != Screen.VILLAGE:
		return
	var p := BuildPanel.new()
	p.chosen.connect(func(type_id: String) -> void: village_view.begin_place(type_id))
	_show_overlay(p)


func _open_army() -> void:
	var p := ArmyPanel.new()
	p.changed.connect(hud.refresh)
	_show_overlay(p)


func _open_bastion() -> void:
	var p := BastionPanel.new()
	p.changed.connect(func() -> void:
		hud.refresh()
		village_view.refresh())
	p.trial_requested.connect(_start_trial)
	_show_overlay(p)


func _open_quests() -> void:
	var p := QuestPanel.new()
	p.claimed.connect(func() -> void:
		hud.refresh()
		quest_bar.refresh())
	_show_overlay(p)


func _open_shop() -> void:
	var p := ShopPanel.new()
	p.changed.connect(func() -> void:
		hud.refresh()
		village_view.refresh())
	_show_overlay(p)


func _open_campaign() -> void:
	var p := CampaignPanel.new()
	p.level_chosen.connect(_start_campaign)
	_show_overlay(p)


func _on_building_tapped(b: Dictionary) -> void:
	var p := InspectPanel.new(int(b["uid"]))
	p.changed.connect(func() -> void:
		hud.refresh()
		village_view.refresh())
	p.move_requested.connect(func(uid: int) -> void: village_view.begin_move(uid))
	p.closed.connect(func() -> void: village_view.clear_selection())
	_show_overlay(p)


func _run_foreman() -> void:
	var n := Game.foreman_apply()
	if n > 0:
		village_view.refresh()
		camera.shake(0.05, 0.12)
	hud.refresh()


# ---------------------------------------------------------------------- raid

func _start_campaign(index: int) -> void:
	if Game.start_campaign_raid(index):
		_enter_raid()


func _start_trial(trial_index: int) -> void:
	if Game.start_trial(trial_index):
		_enter_raid()


func _enter_raid() -> void:
	var ctx := Game.raid_context
	if ctx.is_empty():
		return
	screen = Screen.RAID
	village_view.cancel_mode()
	village_view.visible = false
	hud.visible = false
	village_overlay.visible = false
	quest_bar.visible = false

	raid_view = RaidView.new()
	world.add_child(raid_view)
	raid_view.setup(ctx["village"], Game.tables, int(ctx["seed"]), ctx["loot"], ctx["army"])
	raid_view.finished.connect(_on_raid_finished)
	raid_view.building_destroyed.connect(func(_p: Vector3) -> void: camera.shake(0.05, 0.1))

	var v: Village = ctx["village"]
	camera.setup_bounds(v.width, v.height)
	camera.focus_on(Vector3(float(v.width) * 0.5, 0, float(v.height) * 0.5),
			float(maxi(v.width, v.height)) * 1.05)

	var info: Dictionary = ctx["info"]
	var title := String(info["name"])
	if bool(info.get("is_trial", false)):
		title = "Épreuve — %s" % String(info["name"])
	combat_overlay = CombatOverlay.new(raid_view, camera)
	ui_layer.add_child(combat_overlay)

	raid_hud = RaidHUD.new(title, raid_view)
	ui_layer.add_child(raid_hud)
	raid_hud.finish_pressed.connect(func() -> void: raid_view.force_finish())
	raid_hud.quit_pressed.connect(_abandon_raid)
	raid_view.army_changed.connect(raid_hud.refresh_counts)


func _raid_input(event: InputEvent) -> void:
	if raid_view == null or raid_hud == null:
		return
	if camera.handle_input(event):
		return
	if event is InputEventScreenTouch:
		var t := event as InputEventScreenTouch
		if not t.pressed:
			var cell := camera.screen_to_cell(t.position)
			if not raid_hud.selected_unit.is_empty():
				if not raid_view.deploy(raid_hud.selected_unit, cell):
					Game.emit_signal("notice", "Impossible de déployer ici")


func _on_raid_finished(result: Dictionary) -> void:
	var ctx := Game.raid_context
	var is_trial := String(ctx.get("kind", "")) == "trial"
	var summary := Game.finish_raid(result, raid_view.deployed_army)
	if raid_hud != null:
		raid_hud.queue_free()
		raid_hud = null
	var panel := ResultPanel.new(result, summary, is_trial)
	panel.continue_pressed.connect(_leave_raid)
	ui_layer.add_child(panel)
	overlay = panel


func _abandon_raid() -> void:
	## Quitter en cours de raid : les troupes déployées sont perdues, le reste
	## revient au camp. Pas de pénalité supplémentaire.
	if raid_view != null:
		raid_view.force_finish()


func _leave_raid() -> void:
	if overlay != null and is_instance_valid(overlay):
		overlay.queue_free()
		overlay = null
	if raid_view != null:
		raid_view.queue_free()
		raid_view = null
	if combat_overlay != null:
		combat_overlay.queue_free()
		combat_overlay = null
	screen = Screen.VILLAGE
	village_view.visible = true
	hud.visible = true
	village_overlay.visible = true
	quest_bar.visible = true
	quest_bar.refresh()
	var v := Game.state.village
	camera.setup_bounds(v.width, v.height)
	village_view.refresh()
	hud.refresh()
	Game.save_now()
