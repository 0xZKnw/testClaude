class_name CombatSim
extends RefCounted
## Simulation de raid, déterministe et pas à pas.
##
## CONTRAT DE DÉTERMINISME (docs/05 §4) — à ne jamais rompre :
##   * aucun float dans la boucle de simulation, uniquement du Q16.16 (Fix)
##   * aucun appel à randi()/randf(), uniquement DetRandom seedé
##   * aucune itération sur un Dictionary dont l'ordre compte
##   * pas fixe de 1/20 s, indépendant du framerate
##
## Conséquence : (seed + village + liste d'inputs) -> résultat identique bit à
## bit partout. C'est ce qui permet au serveur de rejouer un combat PvP pour le
## valider, et à la CI de détecter toute régression via des replays enregistrés.
##
## La vue (raid_view.gd) ne fait qu'appeler step() et lire les positions ; elle
## n'influence jamais la simulation.

const TICK_HZ := 20
const DT := Fix.ONE / TICK_HZ           # 3276 en Q16.16
const MAX_TICKS := 180 * TICK_HZ        # 180 secondes
const STAR_MULT := [100, 100, 135, 180] # butin selon 0/1/2/3 étoiles, en %
const DEFENSE_RETARGET_TICKS := 10

enum Pref { ANY, DEFENSE, BUILDING, WALL, ALLY }

# --- bâtiments (tableaux parallèles, orientés donnée) ---
var b_uid: Array[int] = []
var b_type: Array[String] = []
var b_x: Array[int] = []          # centre, Fix
var b_y: Array[int] = []
var b_half: Array[int] = []       # demi-taille, Fix
var b_hp: Array[int] = []         # Fix
var b_maxhp: Array[int] = []      # Fix
var b_weight: Array[int] = []     # entier, pour le pourcentage de destruction
var b_dps: Array[int] = []        # Fix / seconde
var b_range: Array[int] = []      # Fix
var b_alive: Array[bool] = []
var b_is_def: Array[bool] = []
var b_is_wall: Array[bool] = []
var b_is_deco: Array[bool] = []
var b_target: Array[int] = []
var b_cx: Array[int] = []         # case d'origine
var b_cy: Array[int] = []
var b_size: Array[int] = []

# --- unités ---
var u_type: Array[String] = []
var u_x: Array[int] = []
var u_y: Array[int] = []
var u_hp: Array[int] = []
var u_maxhp: Array[int] = []
var u_dps: Array[int] = []
var u_speed: Array[int] = []
var u_range: Array[int] = []
var u_pref: Array[int] = []
var u_target: Array[int] = []
var u_alive: Array[bool] = []
var u_is_healer: Array[bool] = []
var u_deploy_tick: Array[int] = []

var tick: int = 0
var started: bool = false
var finished: bool = false
var checksum: int = 0

var _rng: DetRandom
var _wall_cells: Dictionary = {}    # cell_key -> index de bâtiment
var _total_weight: int = 0
var _destroyed_weight: int = 0
var _bastion_index: int = -1
var _deploy_queue: Array[Dictionary] = []
var _deploy_cursor: int = 0
var _loot_pool: Dictionary = {}
var _tables: DataTables

# Événements produits pendant le tick, consommés par la vue (jamais relus par
# la simulation elle-même — ils n'ont aucun effet sur le déterminisme).
var events: Array[Dictionary] = []


func setup(village: Village, tables: DataTables, seed_value: int, loot_pool: Dictionary = {}) -> void:
	_tables = tables
	_rng = DetRandom.new(seed_value)
	_loot_pool = loot_pool
	for b: Dictionary in village.buildings:
		_add_building(b, tables)
	_total_weight = 0
	for i in range(b_weight.size()):
		if not b_is_deco[i]:
			_total_weight += b_weight[i]
	if _total_weight <= 0:
		_total_weight = 1


func _add_building(b: Dictionary, tables: DataTables) -> void:
	var d: Dictionary = tables.buildings.get(b["type"], {})
	if d.is_empty():
		return
	var size: int = int(b["size"])
	var level: int = int(b["level"])
	var hp := Fix.from_int(tables.building_hp(b["type"], level))
	var idx := b_uid.size()

	b_uid.append(int(b["uid"]))
	b_type.append(String(b["type"]))
	b_x.append(Fix.from_int(int(b["x"])) + Fix.from_int(size) / 2)
	b_y.append(Fix.from_int(int(b["y"])) + Fix.from_int(size) / 2)
	b_half.append(Fix.from_int(size) / 2)
	b_hp.append(hp)
	b_maxhp.append(hp)
	b_weight.append(tables.building_hp(b["type"], level))
	b_dps.append(Fix.from_int(tables.building_dps(b["type"], level)))
	b_range.append(Fix.from_float(float(d["range"])))
	b_alive.append(true)
	b_is_def.append(bool(d["is_defense"]))
	b_is_wall.append(bool(d["is_wall"]))
	b_is_deco.append(String(d["category"]) == "deco")
	b_target.append(-1)
	b_cx.append(int(b["x"]))
	b_cy.append(int(b["y"]))
	b_size.append(size)

	if bool(d["is_wall"]):
		for dy in range(size):
			for dx in range(size):
				_wall_cells[Village.cell_key(int(b["x"]) + dx, int(b["y"]) + dy)] = idx
	if String(b["type"]) == "bastion":
		_bastion_index = idx


# ------------------------------------------------------------------ entrées

func queue_deploy(at_tick: int, unit_id: String, cell_x: int, cell_y: int) -> void:
	## Enregistre un déploiement. C'est le SEUL input du joueur, et c'est
	## exactement ce qui part au serveur pour revalidation.
	_deploy_queue.append({
		"tick": at_tick, "unit": unit_id,
		"x": Fix.from_int(cell_x) + Fix.HALF, "y": Fix.from_int(cell_y) + Fix.HALF,
	})
	_deploy_queue.sort_custom(func(a: Dictionary, c: Dictionary) -> bool:
		if a["tick"] != c["tick"]:
			return a["tick"] < c["tick"]
		return false)


func deploy_now(unit_id: String, cell_x: int, cell_y: int) -> void:
	queue_deploy(tick, unit_id, cell_x, cell_y)


func get_inputs() -> Array[Dictionary]:
	## Le replay complet : à stocker avec la seed pour rejouer le combat.
	return _deploy_queue.duplicate(true)


# ---------------------------------------------------------------- simulation

func step() -> void:
	if finished:
		return
	events.clear()
	_process_deploys()
	if not started:
		return
	_step_units()
	_step_defenses()
	tick += 1
	_update_checksum()
	_check_end()


func run_to_end(max_ticks: int = MAX_TICKS) -> Dictionary:
	## Exécution headless complète : utilisé par les tests, l'équilibrage et la
	## validation serveur.
	var guard := 0
	while not finished and guard < max_ticks + 8:
		step()
		guard += 1
	if not finished:
		finished = true
	return result()


func _process_deploys() -> void:
	while _deploy_cursor < _deploy_queue.size() and int(_deploy_queue[_deploy_cursor]["tick"]) <= tick:
		var cmd: Dictionary = _deploy_queue[_deploy_cursor]
		_deploy_cursor += 1
		_spawn_unit(String(cmd["unit"]), int(cmd["x"]), int(cmd["y"]))
		started = true


func _spawn_unit(unit_id: String, fx: int, fy: int) -> void:
	var d: Dictionary = _tables.units.get(unit_id, {})
	if d.is_empty():
		return
	var hp := Fix.from_int(int(d["hp"]))
	u_type.append(unit_id)
	u_x.append(fx)
	u_y.append(fy)
	u_hp.append(hp)
	u_maxhp.append(hp)
	u_dps.append(Fix.from_int(int(d["dps"])))
	u_speed.append(Fix.from_float(float(d["speed"])))
	u_range.append(Fix.from_float(float(d["range"])))
	u_pref.append(int(d["target_pref"]))
	u_target.append(-1)
	u_alive.append(true)
	u_is_healer.append(bool(d["is_healer"]))
	u_deploy_tick.append(tick)
	events.append({"kind": "spawn", "unit": u_type.size() - 1})


func _step_units() -> void:
	for i in range(u_type.size()):
		if not u_alive[i]:
			continue
		if u_is_healer[i]:
			_step_healer(i)
			continue

		var t := u_target[i]
		if t < 0 or t >= b_alive.size() or not b_alive[t]:
			t = _find_building_target(i)
			u_target[i] = t
		if t < 0:
			continue

		var reach := u_range[i] + b_half[t]
		var dx := b_x[t] - u_x[i]
		var dy := b_y[t] - u_y[i]
		var dist := Fix.sqrt_fix(Fix.mul(dx, dx) + Fix.mul(dy, dy))

		if dist <= reach:
			_damage_building(t, Fix.mul(u_dps[i], DT), i)
		else:
			_move_unit(i, dx, dy, dist)


func _step_healer(i: int) -> void:
	## Le soigneur suit l'allié blessé le plus proche et le régénère.
	var best := -1
	var best_d := 0
	for j in range(u_type.size()):
		if j == i or not u_alive[j] or u_is_healer[j]:
			continue
		if u_hp[j] >= u_maxhp[j]:
			continue
		var d := Fix.dist_sq(u_x[i], u_y[i], u_x[j], u_y[j])
		if best < 0 or d < best_d:
			best = j
			best_d = d
	if best < 0:
		# Personne à soigner : on suit le premier allié vivant.
		for j in range(u_type.size()):
			if j != i and u_alive[j] and not u_is_healer[j]:
				best = j
				break
		if best < 0:
			return
	var dx := u_x[best] - u_x[i]
	var dy := u_y[best] - u_y[i]
	var dist := Fix.sqrt_fix(Fix.mul(dx, dx) + Fix.mul(dy, dy))
	if dist <= u_range[i]:
		var heal := Fix.mul(-u_dps[i], DT)
		u_hp[best] = Fix.min_fix(u_maxhp[best], u_hp[best] + heal)
	else:
		_move_unit(i, dx, dy, dist)


func _move_unit(i: int, dx: int, dy: int, dist: int) -> void:
	if dist <= 0:
		return
	var stepv := Fix.mul(u_speed[i], DT)
	var nx := u_x[i] + Fix.div(Fix.mul(dx, stepv), dist)
	var ny := u_y[i] + Fix.div(Fix.mul(dy, stepv), dist)

	# Un mur sur le chemin devient la cible : c'est ce qui donne du sens aux
	# layouts défensifs sans nécessiter de pathfinding complet.
	if not u_is_healer[i] and u_pref[i] != Pref.WALL:
		var cell := Village.cell_key(Fix.to_int(nx), Fix.to_int(ny))
		if _wall_cells.has(cell):
			var wi: int = _wall_cells[cell]
			if b_alive[wi]:
				u_target[i] = wi
				return
	u_x[i] = nx
	u_y[i] = ny


func _find_building_target(i: int) -> int:
	var pref := u_pref[i]
	var best := _nearest_building(u_x[i], u_y[i], pref)
	if best < 0 and pref != Pref.ANY:
		best = _nearest_building(u_x[i], u_y[i], Pref.ANY)
	return best


func _nearest_building(fx: int, fy: int, pref: int) -> int:
	var best := -1
	var best_d := 0
	for j in range(b_alive.size()):
		if not b_alive[j] or b_is_deco[j]:
			continue
		match pref:
			Pref.DEFENSE:
				if not b_is_def[j]:
					continue
			Pref.WALL:
				if not b_is_wall[j]:
					continue
			Pref.BUILDING:
				if b_is_wall[j]:
					continue
			_:
				pass
		var d := Fix.dist_sq(fx, fy, b_x[j], b_y[j])
		if best < 0 or d < best_d:
			best = j
			best_d = d
	return best


func _step_defenses() -> void:
	for j in range(b_alive.size()):
		if not b_alive[j] or not b_is_def[j]:
			continue
		var t := b_target[j]
		var need_retarget := t < 0 or t >= u_alive.size() or not u_alive[t] or (tick % DEFENSE_RETARGET_TICKS == 0)
		if need_retarget:
			t = _nearest_unit(b_x[j], b_y[j], b_range[j])
			b_target[j] = t
		if t < 0:
			continue
		if Fix.dist_sq(b_x[j], b_y[j], u_x[t], u_y[t]) > Fix.mul(b_range[j], b_range[j]):
			b_target[j] = -1
			continue
		_damage_unit(t, Fix.mul(b_dps[j], DT), j)


func _nearest_unit(fx: int, fy: int, rng_fix: int) -> int:
	var best := -1
	var best_d := 0
	var max_d := Fix.mul(rng_fix, rng_fix)
	for i in range(u_alive.size()):
		if not u_alive[i]:
			continue
		var d := Fix.dist_sq(fx, fy, u_x[i], u_y[i])
		if d > max_d:
			continue
		if best < 0 or d < best_d:
			best = i
			best_d = d
	return best


func _damage_building(j: int, amount: int, by_unit: int) -> void:
	if amount <= 0 or not b_alive[j]:
		return
	b_hp[j] -= amount
	if b_hp[j] <= 0:
		b_hp[j] = 0
		b_alive[j] = false
		if not b_is_deco[j]:
			_destroyed_weight += b_weight[j]
		events.append({"kind": "building_destroyed", "building": j, "by": by_unit})


func _damage_unit(i: int, amount: int, by_building: int) -> void:
	if amount <= 0 or not u_alive[i]:
		return
	u_hp[i] -= amount
	if u_hp[i] <= 0:
		u_hp[i] = 0
		u_alive[i] = false
		events.append({"kind": "unit_died", "unit": i, "by": by_building})


func _check_end() -> void:
	if tick >= MAX_TICKS:
		finished = true
		return
	if _destroyed_weight >= _total_weight:
		finished = true
		return
	if _deploy_cursor >= _deploy_queue.size() and started:
		var any_alive := false
		for a: bool in u_alive:
			if a:
				any_alive = true
				break
		if not any_alive:
			finished = true


func _update_checksum() -> void:
	## Empreinte de l'état, mise à jour chaque tick. Deux exécutions identiques
	## doivent produire la même valeur — c'est ce que vérifie la CI.
	var h := checksum
	h = (h * 31 + tick) & 0xFFFFFFFF
	h = (h * 31 + _destroyed_weight) & 0xFFFFFFFF
	for i in range(u_alive.size()):
		h = (h * 33 + u_x[i]) & 0xFFFFFFFF
		h = (h * 33 + u_y[i]) & 0xFFFFFFFF
		h = (h * 33 + u_hp[i]) & 0xFFFFFFFF
	for j in range(b_alive.size()):
		h = (h * 33 + b_hp[j]) & 0xFFFFFFFF
	checksum = h


# ------------------------------------------------------------------ résultat

func destruction_percent() -> int:
	return mini(100, (_destroyed_weight * 100) / _total_weight)


func bastion_destroyed() -> bool:
	return _bastion_index >= 0 and not b_alive[_bastion_index]


func stars() -> int:
	var s := 0
	if destruction_percent() >= 50:
		s += 1
	if bastion_destroyed():
		s += 1
	if destruction_percent() >= 100:
		s += 1
	return mini(3, s)


func loot() -> Dictionary:
	var pct := destruction_percent()
	var mult: int = STAR_MULT[stars()]
	var out := {}
	for res: String in _loot_pool.keys():
		var v := (int(_loot_pool[res]) * pct / 100) * mult / 100
		if v > 0:
			out[res] = v
	return out


func result() -> Dictionary:
	return {
		"percent": destruction_percent(),
		"stars": stars(),
		"bastion_destroyed": bastion_destroyed(),
		"loot": loot(),
		"ticks": tick,
		"duration_sec": float(tick) / float(TICK_HZ),
		"checksum": checksum,
		"units_lost": _count_dead_units(),
		"units_total": u_type.size(),
	}


func _count_dead_units() -> int:
	var n := 0
	for a: bool in u_alive:
		if not a:
			n += 1
	return n


func time_left_sec() -> float:
	if not started:
		return float(MAX_TICKS) / float(TICK_HZ)
	return maxf(0.0, float(MAX_TICKS - tick) / float(TICK_HZ))


# ---------------------------------------------------------------- rejouabilité

static func replay(village: Village, tables: DataTables, seed_value: int,
		inputs: Array, loot_pool: Dictionary = {}) -> Dictionary:
	## Rejoue un combat à partir de (village + seed + inputs). C'est cette
	## fonction que le serveur exécuterait pour valider un rapport de combat.
	var sim := CombatSim.new()
	sim.setup(village, tables, seed_value, loot_pool)
	for cmd: Variant in inputs:
		var c: Dictionary = cmd
		sim._deploy_queue.append({
			"tick": int(c["tick"]), "unit": String(c["unit"]),
			"x": int(c["x"]), "y": int(c["y"]),
		})
	sim._deploy_queue.sort_custom(func(a: Dictionary, c: Dictionary) -> bool:
		return int(a["tick"]) < int(c["tick"]))
	return sim.run_to_end()
