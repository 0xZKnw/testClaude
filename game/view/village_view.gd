class_name VillageView
extends Node3D
## Écran du village : sélection, placement, déplacement.
##
## Choix d'ergonomie important pour les sessions longues : déplacer un bâtiment
## est gratuit et illimité, et le mode placement neutralise le pan à un doigt
## (le pinch à deux doigts reste actif) pour qu'on ne se batte jamais avec la
## caméra en posant une tuile.

signal building_tapped(building: Dictionary)
signal placed(type_id: String, cell: Vector2i)
signal placement_ended
signal empty_tapped(cell: Vector2i)

enum Mode { NORMAL, PLACING, MOVING }

var renderer: VillageRenderer
var camera: IsoCamera
var mode: int = Mode.NORMAL
var placing_type: String = ""
var moving_uid: int = -1
var selected_uid: int = -1

var _ghost: MeshInstance3D
var _ghost_cell := Vector2i(-99, -99)
var _ghost_size := 1
var _pointer_down := false
var _pointer_moved := false
var _down_pos := Vector2.ZERO


func _ready() -> void:
	renderer = VillageRenderer.new()
	add_child(renderer)
	_ghost = MeshInstance3D.new()
	_ghost.visible = false
	_ghost.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_ghost)


func setup(v: Village, t: DataTables, cam: IsoCamera) -> void:
	camera = cam
	renderer.setup(v, t)
	camera.setup_bounds(v.width, v.height)


func refresh() -> void:
	renderer.rebuild()
	if selected_uid >= 0:
		var b := Game.state.village.get_building(selected_uid)
		if b.is_empty():
			clear_selection()
		else:
			renderer.show_selection(b)


func clear_selection() -> void:
	selected_uid = -1
	renderer.hide_selection()


# -------------------------------------------------------------------- modes

func begin_place(type_id: String) -> void:
	var d: Dictionary = Game.tables.buildings.get(type_id, {})
	if d.is_empty():
		return
	mode = Mode.PLACING
	placing_type = type_id
	_ghost_size = int(d["size"])
	_ghost.mesh = BuildingMesh.get_mesh(String(d["shape"]), _ghost_size, 1, String(d["color"]))
	_ghost.visible = true
	clear_selection()
	# On propose le centre de l'écran comme point de départ.
	_update_ghost(Vector2i(int(camera.target.x), int(camera.target.z)))


func begin_move(uid: int) -> void:
	var b := Game.state.village.get_building(uid)
	if b.is_empty():
		return
	var d: Dictionary = Game.tables.buildings.get(String(b["type"]), {})
	mode = Mode.MOVING
	moving_uid = uid
	_ghost_size = int(b["size"])
	_ghost.mesh = BuildingMesh.get_mesh(String(d["shape"]), _ghost_size,
			int(b["level"]), String(d["color"]))
	_ghost.visible = true
	_update_ghost(Vector2i(int(b["x"]) + _ghost_size / 2, int(b["y"]) + _ghost_size / 2))


func cancel_mode() -> void:
	mode = Mode.NORMAL
	placing_type = ""
	moving_uid = -1
	_ghost.visible = false
	emit_signal("placement_ended")


func is_busy() -> bool:
	return mode != Mode.NORMAL


# ------------------------------------------------------------------ entrées

func handle_input(event: InputEvent) -> void:
	if camera == null:
		return

	# En mode placement, un seul doigt sert à déplacer la pièce ; on laisse la
	# caméra gérer les gestes à deux doigts.
	var single_finger := _is_single_finger(event)
	if not (is_busy() and single_finger):
		if camera.handle_input(event):
			return
	elif event is InputEventMouseButton:
		camera.handle_input(event)

	if event is InputEventScreenTouch:
		var t := event as InputEventScreenTouch
		if t.pressed:
			_pointer_down = true
			_pointer_moved = false
			_down_pos = t.position
			if is_busy():
				_update_ghost(camera.screen_to_cell(t.position))
		else:
			if _pointer_down and not _pointer_moved:
				_on_tap(t.position)
			elif _pointer_down and is_busy():
				_confirm_ghost()
			_pointer_down = false
	elif event is InputEventScreenDrag:
		var d := event as InputEventScreenDrag
		if d.position.distance_to(_down_pos) > 8.0:
			_pointer_moved = true
		if is_busy():
			_update_ghost(camera.screen_to_cell(d.position))


func _is_single_finger(event: InputEvent) -> bool:
	if event is InputEventScreenDrag:
		return (event as InputEventScreenDrag).index == 0
	if event is InputEventScreenTouch:
		return (event as InputEventScreenTouch).index == 0
	return false


func _on_tap(screen_pos: Vector2) -> void:
	if is_busy():
		_update_ghost(camera.screen_to_cell(screen_pos))
		_confirm_ghost()
		return
	var cell := camera.screen_to_cell(screen_pos)
	var b := Game.state.village.building_at(cell.x, cell.y)
	if b.is_empty():
		clear_selection()
		emit_signal("empty_tapped", cell)
	else:
		selected_uid = int(b["uid"])
		renderer.show_selection(b)
		emit_signal("building_tapped", b)


# ------------------------------------------------------------------- fantôme

func _origin_from_cell(cell: Vector2i) -> Vector2i:
	return Vector2i(cell.x - _ghost_size / 2, cell.y - _ghost_size / 2)


func _update_ghost(cell: Vector2i) -> void:
	if not is_busy():
		return
	var origin := _origin_from_cell(cell)
	_ghost_cell = origin
	var half := float(_ghost_size) * 0.5
	_ghost.position = Vector3(float(origin.x) + half, 0.02, float(origin.y) + half)
	_ghost.material_override = Palette.ghost_material(_ghost_valid())


func _ghost_valid() -> bool:
	var ignore := moving_uid if mode == Mode.MOVING else -1
	return Game.state.village.can_place(_ghost_cell.x, _ghost_cell.y, _ghost_size, ignore)


func _confirm_ghost() -> void:
	if not _ghost_valid():
		Game.emit_signal("notice", "Emplacement invalide")
		return
	if mode == Mode.PLACING:
		var type_id := placing_type
		var uid := Game.build(type_id, _ghost_cell.x, _ghost_cell.y)
		if uid >= 0:
			refresh()
			renderer.animate_build(uid)
			camera.shake(0.06, 0.1)
			emit_signal("placed", type_id, _ghost_cell)
			# On reste en mode placement : enchaîner des murs ou des arbres est
			# le cas normal, pas l'exception.
			var again := Game.can_build(type_id)
			if bool(again["ok"]):
				_update_ghost(Vector2i(_ghost_cell.x + _ghost_size, _ghost_cell.y))
				return
		cancel_mode()
	elif mode == Mode.MOVING:
		if Game.move_building(moving_uid, _ghost_cell.x, _ghost_cell.y):
			refresh()
			var uid := moving_uid
			cancel_mode()
			renderer.animate_build(uid)
			var b := Game.state.village.get_building(uid)
			if not b.is_empty():
				selected_uid = uid
				renderer.show_selection(b)
		else:
			cancel_mode()
