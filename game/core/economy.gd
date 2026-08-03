class_name Economy
extends RefCounted
## Production, stockage, coûts.
##
## Point de design important (docs/03) : la croissance des coûts (1.34) est
## supérieure à celle de la production (1.22). Le passif décroche donc
## volontairement au fil des niveaux, ce qui force le retour au raid. Sans ça,
## un jeu sans timers devient un idle game qui se joue tout seul.

const RESERVE_MAX_HOURS := 24     # Réserve du Régent : au-delà, plus rien ne s'accumule
const OFFLINE_DECAY_AFTER_H := 6  # rendement dégressif au-delà de 6 h hors-ligne


static func storage_caps(state: PlayerState, tables: DataTables) -> Dictionary:
	var caps := {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 999999999}
	var mult := Tycoon.storage_multiplier(state)
	for b: Dictionary in state.village.buildings:
		var d: Dictionary = tables.buildings.get(b["type"], {})
		if d.is_empty():
			continue
		var store_res: Array = d["store_res"]
		if store_res.is_empty():
			continue
		var amount := tables.building_storage(b["type"], int(b["level"]))
		for res: String in store_res:
			caps[res] = int(caps.get(res, 0)) + amount
	for res: String in ["wood", "stone", "iron", "gold"]:
		caps[res] = int(float(caps[res]) * mult)
	return caps


static func building_output(state: PlayerState, tables: DataTables, b: Dictionary) -> Dictionary:
	## Production d'UN bâtiment, par minute, tous multiplicateurs appliqués.
	## C'est l'unité de base de la couche tycoon : chaque bâtiment accumule
	## sa propre réserve, visible au-dessus de lui.
	var d: Dictionary = tables.buildings.get(b["type"], {})
	if d.is_empty() or String(d["prod_res"]).is_empty():
		return {}
	var res: String = d["prod_res"]
	var rate := float(tables.building_production(b["type"], int(b["level"])))
	rate = rate * float(_adjacency_bonus(state, b)) / 100.0
	rate = rate * Tycoon.production_multiplier(state)
	if rate <= 0.0:
		return {}
	return {res: rate}


static func production_per_minute(state: PlayerState, tables: DataTables) -> Dictionary:
	var prod := {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 0}
	for b: Dictionary in state.village.buildings:
		for res: String in building_output(state, tables, b).keys():
			prod[res] = int(prod.get(res, 0)) + int(building_output(state, tables, b)[res])
	return prod


static func _adjacency_bonus(state: PlayerState, b: Dictionary) -> int:
	## Bonus d'adjacence, en pourcentage (100 = neutre). Voir docs/04 §3.
	## Version réduite : les règles complètes arrivent avec l'overlay d'adjacence.
	var bonus := 100
	var t: String = b["type"]
	var neighbours := _neighbours_of(state, b)
	var counts := {}
	for n: Dictionary in neighbours:
		counts[n["type"]] = int(counts.get(n["type"], 0)) + 1

	match t:
		"sawmill":
			bonus += 5 * int(counts.get("tree", 0))          # scierie près des arbres
		"quarry":
			bonus += 8 * int(counts.get("quarry", 0))
		"house":
			bonus += 10 * int(counts.get("mill", 0))
			bonus -= 15 * int(counts.get("ironmine", 0))     # personne n'aime le bruit
		"mill":
			bonus += 6 * int(counts.get("sawmill", 0))
		"ironmine":
			bonus += 5 * int(counts.get("quarry", 0))
	if counts.has("statue"):
		bonus += 5 * int(counts["statue"])
	return clampi(bonus, 50, 220)


static func _neighbours_of(state: PlayerState, b: Dictionary) -> Array[Dictionary]:
	## Bâtiments occupant une case adjacente au périmètre de `b`.
	var out: Array[Dictionary] = []
	var seen := {}
	var x0: int = b["x"]
	var y0: int = b["y"]
	var s: int = b["size"]
	for i in range(-1, s + 1):
		var probes := [
			Vector2i(x0 + i, y0 - 1), Vector2i(x0 + i, y0 + s),
			Vector2i(x0 - 1, y0 + i), Vector2i(x0 + s, y0 + i),
		]
		for p: Vector2i in probes:
			var other := state.village.building_at(p.x, p.y)
			if other.is_empty() or other["uid"] == b["uid"] or seen.has(other["uid"]):
				continue
			seen[other["uid"]] = true
			out.append(other)
	return out


static func can_afford(state: PlayerState, cost: Dictionary) -> bool:
	for res: String in cost.keys():
		if int(state.resources.get(res, 0)) < int(cost[res]):
			return false
	return true


static func missing_for(state: PlayerState, cost: Dictionary) -> Dictionary:
	var miss := {}
	for res: String in cost.keys():
		var lack := int(cost[res]) - int(state.resources.get(res, 0))
		if lack > 0:
			miss[res] = lack
	return miss


static func pay(state: PlayerState, cost: Dictionary) -> bool:
	if not can_afford(state, cost):
		return false
	for res: String in cost.keys():
		state.resources[res] = int(state.resources.get(res, 0)) - int(cost[res])
	return true


static func grant(state: PlayerState, tables: DataTables, gains: Dictionary) -> Dictionary:
	## Ajoute des ressources en respectant les plafonds. Retourne ce qui a
	## réellement été crédité (pour l'affichage honnête du butin).
	var caps := storage_caps(state, tables)
	var actual := {}
	for res: String in gains.keys():
		var amount := int(gains[res])
		if amount <= 0:
			continue
		var cap := int(caps.get(res, 0))
		var cur := int(state.resources.get(res, 0))
		var added: int = amount if res == "essence" else mini(amount, maxi(0, cap - cur))
		if added > 0:
			state.resources[res] = cur + added
			actual[res] = added
			# Total gagné sur toute la partie : c'est lui qui détermine les
			# points de rebirth (docs — couche tycoon).
			state.stats["total_earned"] = int(state.stats.get("total_earned", 0)) + added
	return actual


static func tick(state: PlayerState, tables: DataTables, delta_seconds: float) -> void:
	## La production ne va PAS directement au trésor : elle s'accumule au-dessus
	## de chaque bâtiment, jusqu'à un plafond, en attendant d'être ramassée.
	##
	## C'est le cœur de la greffe tycoon. Deux conséquences voulues :
	##  * le joueur a en permanence quelque chose à faire au doigt ;
	##  * revenir après une pause offre une belle moisson à récolter, au lieu
	##    d'un compteur qui a monté tout seul pendant son absence.
	if delta_seconds <= 0.0:
		return
	var seconds := minf(delta_seconds, float(RESERVE_MAX_HOURS * 3600))
	var decay_start := float(OFFLINE_DECAY_AFTER_H * 3600)
	if seconds > decay_start:
		# Au-delà de 6 h : rendement dégressif, mais jamais nul.
		seconds = decay_start + (seconds - decay_start) * 0.35

	var cap_minutes := Tycoon.pending_capacity_minutes(state)
	for b: Dictionary in state.village.buildings:
		var out := building_output(state, tables, b)
		if out.is_empty():
			continue
		var uid := int(b["uid"])
		var slot: Dictionary = state.pending.get(uid, {})
		for res: String in out.keys():
			var per_min := float(out[res])
			var cap := per_min * cap_minutes
			var cur := float(slot.get(res, 0.0))
			slot[res] = minf(cap, cur + per_min * seconds / 60.0)
		state.pending[uid] = slot


static func collect(state: PlayerState, tables: DataTables, uid: int) -> Dictionary:
	## Ramasse la réserve d'un bâtiment. Retourne ce qui a réellement été
	## crédité (les plafonds de stockage s'appliquent).
	var slot: Dictionary = state.pending.get(uid, {})
	if slot.is_empty():
		return {}
	var gains := {}
	for res: String in slot.keys():
		var whole := int(floor(float(slot[res])))
		if whole > 0:
			gains[res] = whole
	if gains.is_empty():
		return {}
	var granted := grant(state, tables, gains)
	# On ne retire que ce qui a été effectivement encaissé : si l'entrepôt est
	# plein, la réserve reste au-dessus du bâtiment au lieu d'être perdue.
	for res: String in granted.keys():
		slot[res] = maxf(0.0, float(slot[res]) - float(granted[res]))
	state.pending[uid] = slot
	return granted


static func collect_all(state: PlayerState, tables: DataTables) -> Dictionary:
	var total := {}
	for uid: Variant in state.pending.keys():
		var got := collect(state, tables, int(uid))
		for res: String in got.keys():
			total[res] = int(total.get(res, 0)) + int(got[res])
	return total


static func pending_summary(state: PlayerState) -> Dictionary:
	var total := {}
	for uid: Variant in state.pending.keys():
		var slot: Dictionary = state.pending[uid]
		for res: String in slot.keys():
			var v := int(floor(float(slot[res])))
			if v > 0:
				total[res] = int(total.get(res, 0)) + v
	return total


static func upgrade_cost(tables: DataTables, type_id: String, current_level: int) -> Dictionary:
	return tables.building_cost(type_id, current_level + 1)


static func sell_refund(tables: DataTables, type_id: String, level: int) -> Dictionary:
	## 40 % du cumul investi. Déplacer est gratuit ; vendre coûte un peu.
	var refund := {}
	for lvl in range(1, level + 1):
		var c := tables.building_cost(type_id, lvl)
		for res: String in c.keys():
			refund[res] = int(refund.get(res, 0)) + int(c[res])
	for res: String in refund.keys():
		refund[res] = int(refund[res] * 40 / 100)
	return refund
