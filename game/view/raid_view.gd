class_name RaidView
extends Node3D
## Vue du raid : pilote la simulation et l'affiche.
##
## Règle d'or : la vue LIT la simulation, elle ne la modifie jamais autrement
## que par `deploy()`, qui passe par la file d'inputs. C'est ce découplage qui
## garantit qu'un combat rejoué headless donne exactement le même résultat que
## celui vu à l'écran.

signal finished(result: Dictionary)
signal army_changed
signal building_destroyed(world_pos: Vector3)

const DAMAGE_REFRESH := 0.2

var sim: CombatSim
var tables: DataTables
var village: Village
var renderer: VillageRenderer

var remaining_army: Dictionary = {}    # unit_id -> restants
var deployed_army: Dictionary = {}     # unit_id -> déployés
var running := false

var _accum := 0.0
var _damage_accum := 0.0
var _unit_groups: Dictionary = {}      # unit_id -> {mmi, used}
var _unit_group_of: Array[String] = []
var _unit_slot: Array[int] = []
var _prev_x: Array[float] = []
var _prev_y: Array[float] = []
var _cur_x: Array[float] = []
var _cur_y: Array[float] = []


func setup(v: Village, t: DataTables, seed_value: int, loot: Dictionary, army: Dictionary) -> void:
	village = v
	tables = t
	remaining_army = army.duplicate()
	deployed_army = {}

	renderer = VillageRenderer.new()
	add_child(renderer)
	renderer.setup(v, t)

	sim = CombatSim.new()
	sim.setup(v, t, seed_value, loot)

	_allocate_unit_groups()
	running = true


func _allocate_unit_groups() -> void:
	for id: String in remaining_army.keys():
		var d: Dictionary = tables.units.get(id, {})
		if d.is_empty():
			continue
		var mesh := BuildingMesh.get_unit_mesh(String(d["shape"]), String(d["color"]))
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = mesh
		mm.instance_count = int(remaining_army[id])
		var mmi := MultiMeshInstance3D.new()
		mmi.multimesh = mm
		mmi.material_override = Palette.material()
		add_child(mmi)
		# Toutes les instances hors champ tant qu'elles ne sont pas déployées.
		for i in range(mm.instance_count):
			mm.set_instance_transform(i, Transform3D(Basis.IDENTITY, Vector3(0, -50, 0)))
		_unit_groups[id] = {"mmi": mmi, "used": 0}


# ---------------------------------------------------------------- déploiement

func can_deploy(unit_id: String, cell: Vector2i) -> bool:
	if not running or sim.finished:
		return false
	if int(remaining_army.get(unit_id, 0)) <= 0:
		return false
	if cell.x < 0 or cell.y < 0 or cell.x >= village.width or cell.y >= village.height:
		return false
	# On ne pose pas une troupe sur un bâtiment.
	return village.building_at(cell.x, cell.y).is_empty()


func deploy(unit_id: String, cell: Vector2i) -> bool:
	if not can_deploy(unit_id, cell):
		return false
	remaining_army[unit_id] = int(remaining_army[unit_id]) - 1
	deployed_army[unit_id] = int(deployed_army.get(unit_id, 0)) + 1
	sim.deploy_now(unit_id, cell.x, cell.y)
	_register_new_units()
	emit_signal("army_changed")
	return true


func _register_new_units() -> void:
	while _unit_group_of.size() < sim.u_type.size():
		var i := _unit_group_of.size()
		var id := sim.u_type[i]
		var group: Dictionary = _unit_groups.get(id, {})
		if group.is_empty():
			_unit_group_of.append("")
			_unit_slot.append(-1)
		else:
			var slot := int(group["used"])
			group["used"] = slot + 1
			_unit_group_of.append(id)
			_unit_slot.append(slot)
		var fx := Fix.to_float(sim.u_x[i])
		var fy := Fix.to_float(sim.u_y[i])
		_prev_x.append(fx)
		_prev_y.append(fy)
		_cur_x.append(fx)
		_cur_y.append(fy)


# ---------------------------------------------------------------- simulation

func _process(delta: float) -> void:
	if not running:
		return

	var step_time := 1.0 / float(CombatSim.TICK_HZ)
	_accum += delta
	var guard := 0
	while _accum >= step_time and not sim.finished and guard < 6:
		_accum -= step_time
		guard += 1
		for i in range(_cur_x.size()):
			_prev_x[i] = _cur_x[i]
			_prev_y[i] = _cur_y[i]
		sim.step()
		_register_new_units()
		for i in range(sim.u_type.size()):
			if i < _cur_x.size():
				_cur_x[i] = Fix.to_float(sim.u_x[i])
				_cur_y[i] = Fix.to_float(sim.u_y[i])
		_consume_events()

	var alpha := clampf(_accum / step_time, 0.0, 1.0)
	_draw_units(alpha)

	_damage_accum += delta
	if _damage_accum >= DAMAGE_REFRESH:
		_damage_accum = 0.0
		_refresh_damage()

	if sim.finished and running:
		running = false
		emit_signal("finished", sim.result())


func _consume_events() -> void:
	for e: Dictionary in sim.events:
		if String(e["kind"]) == "building_destroyed":
			var j := int(e["building"])
			var uid := sim.b_uid[j]
			renderer.set_destroyed(uid, true)
			emit_signal("building_destroyed", renderer.world_position_of(uid))


func _draw_units(alpha: float) -> void:
	for i in range(sim.u_type.size()):
		if i >= _unit_group_of.size():
			break
		var id := _unit_group_of[i]
		if id.is_empty():
			continue
		var group: Dictionary = _unit_groups[id]
		var mmi: MultiMeshInstance3D = group["mmi"]
		var slot := _unit_slot[i]
		if slot < 0 or slot >= mmi.multimesh.instance_count:
			continue
		if not sim.u_alive[i]:
			mmi.multimesh.set_instance_transform(slot, Transform3D(Basis.IDENTITY, Vector3(0, -50, 0)))
			continue
		var px := lerpf(_prev_x[i], _cur_x[i], alpha)
		var py := lerpf(_prev_y[i], _cur_y[i], alpha)
		var dx := _cur_x[i] - _prev_x[i]
		var dy := _cur_y[i] - _prev_y[i]
		var basis := Basis.IDENTITY
		if absf(dx) + absf(dy) > 0.0001:
			basis = Basis(Vector3.UP, atan2(dx, dy))
		# Petit dandinement : rend la marche lisible sans squelette animé.
		var bob := sin(float(sim.tick + i * 7) * 0.55) * 0.035
		mmi.multimesh.set_instance_transform(slot,
				Transform3D(basis, Vector3(px, maxf(0.0, bob), py)))


func _refresh_damage() -> void:
	for j in range(sim.b_alive.size()):
		if not sim.b_alive[j]:
			continue
		if sim.b_maxhp[j] <= 0:
			continue
		var ratio := float(sim.b_hp[j]) / float(sim.b_maxhp[j])
		if ratio < 0.999:
			renderer.set_damage_ratio(sim.b_uid[j], ratio)


# ---------------------------------------------------------------------- infos

func percent() -> int:
	return sim.destruction_percent() if sim != null else 0


func stars() -> int:
	return sim.stars() if sim != null else 0


func time_left() -> float:
	return sim.time_left_sec() if sim != null else 0.0


func units_left() -> int:
	var n := 0
	for v: Variant in remaining_army.values():
		n += int(v)
	return n


func force_finish() -> void:
	if sim != null and not sim.finished:
		sim.finished = true
