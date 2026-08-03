extends Node
## Autoload `Game` : l'orchestrateur.
##
## Toute mutation de l'état passe par ici, jamais par la vue. Les écrans
## s'abonnent aux signaux et se contentent de refléter l'état — c'est ce qui
## permettra plus tard de rejouer, valider côté serveur, ou tester sans UI.

signal resources_changed
signal village_changed
signal bastion_upgraded(level: int)
signal notice(text: String)
signal army_changed
signal pending_changed
signal collected(uid: int, gains: Dictionary)
signal prestiged(points: int)
signal quest_changed

const ECONOMY_STEP := 0.25
const AUTOSAVE_STEP := 30.0

var state: PlayerState
var tables: DataTables
var raid_context: Dictionary = {}

var _econ_accum := 0.0
var _save_accum := 0.0
var _auto_accum := 0.0
var _session_start_unix := 0


func _ready() -> void:
	tables = DB.tables
	_session_start_unix = int(Time.get_unix_time_from_system())
	load_or_create()


func load_or_create() -> void:
	var loaded := SaveSystem.load_state()
	if loaded == null:
		state = PlayerState.create_new(tables)
		emit_signal("notice", "Bienvenue dans la vallée, Régent.")
	else:
		state = loaded
		_apply_offline_progress()
	emit_signal("resources_changed")
	emit_signal("village_changed")


func new_game() -> void:
	SaveSystem.wipe()
	state = PlayerState.create_new(tables)
	emit_signal("resources_changed")
	emit_signal("village_changed")
	emit_signal("notice", "Nouvelle vallée.")


func save_now() -> void:
	if state != null:
		SaveSystem.save(state)


func _apply_offline_progress() -> void:
	var now := int(Time.get_unix_time_from_system())
	var elapsed := maxi(0, now - state.last_seen_unix)
	if elapsed < 30:
		return
	# Rien n'est crédité automatiquement : la production d'absence attend au
	# dessus des bâtiments. Revenir dans le jeu, c'est une moisson à ramasser.
	Economy.tick(state, tables, float(elapsed))
	var waiting := Economy.pending_summary(state)
	if not waiting.is_empty():
		emit_signal("notice", "Pendant ton absence : %s à ramasser" % format_resources(waiting))


func _process(delta: float) -> void:
	if state == null:
		return
	_econ_accum += delta
	if _econ_accum >= ECONOMY_STEP:
		Economy.tick(state, tables, _econ_accum)
		_econ_accum = 0.0
		emit_signal("pending_changed")

	# Récolte automatique : une amélioration tycoon, pas un réglage par défaut.
	# Le joueur commence en ramassant lui-même — c'est ce geste qui accroche —
	# puis achète le confort quand la répétition devient une corvée.
	var auto_interval := Tycoon.auto_collect_interval(state)
	if auto_interval > 0.0:
		_auto_accum += delta
		if _auto_accum >= auto_interval:
			_auto_accum = 0.0
			var got := Economy.collect_all(state, tables)
			if not got.is_empty():
				emit_signal("resources_changed")
				emit_signal("pending_changed")
	_save_accum += delta
	if _save_accum >= AUTOSAVE_STEP:
		_save_accum = 0.0
		state.stats["playtime_sec"] = int(state.stats.get("playtime_sec", 0)) + int(AUTOSAVE_STEP)
		save_now()


func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_WM_CLOSE_REQUEST:
		save_now()


# ------------------------------------------------------------- construction

func build_limit(type_id: String) -> int:
	return tables.building_max_count(type_id, state.bastion_level())


func can_build(type_id: String) -> Dictionary:
	## Retourne {ok: bool, reason: String, cost: Dictionary}.
	var d: Dictionary = tables.buildings.get(type_id, {})
	if d.is_empty():
		return {"ok": false, "reason": "Bâtiment inconnu", "cost": {}}
	var cost := tables.building_cost(type_id, 1)
	if int(d["unlock"]) > state.bastion_level():
		return {"ok": false, "reason": "Bastion %d requis" % int(d["unlock"]), "cost": cost}
	var limit := build_limit(type_id)
	if state.village.count_of(type_id) >= limit:
		return {"ok": false, "reason": "Limite atteinte (%d)" % limit, "cost": cost}
	if not Economy.can_afford(state, cost):
		return {"ok": false, "reason": "Ressources insuffisantes", "cost": cost}
	return {"ok": true, "reason": "", "cost": cost}


func build(type_id: String, x: int, y: int) -> int:
	## Construction INSTANTANÉE. Pas de file d'attente, pas d'ouvrier, pas de
	## minuteur : c'est la promesse centrale du jeu (docs/01, pilier P1).
	var check := can_build(type_id)
	if not check["ok"]:
		emit_signal("notice", String(check["reason"]))
		return -1
	var d: Dictionary = tables.buildings[type_id]
	var size := int(d["size"])
	if not state.village.can_place(x, y, size):
		emit_signal("notice", "Emplacement occupé")
		return -1
	if not Economy.pay(state, check["cost"]):
		return -1
	var uid := state.village.place(type_id, 1, x, y, size)
	state.stats["buildings_built"] = int(state.stats.get("buildings_built", 0)) + 1
	emit_signal("resources_changed")
	emit_signal("village_changed")
	return uid


func upgrade_cost_of(uid: int) -> Dictionary:
	var b := state.village.get_building(uid)
	if b.is_empty():
		return {}
	return Economy.upgrade_cost(tables, String(b["type"]), int(b["level"]))


func can_upgrade(uid: int) -> Dictionary:
	var b := state.village.get_building(uid)
	if b.is_empty():
		return {"ok": false, "reason": "Introuvable"}
	var type_id := String(b["type"])
	if type_id == "bastion":
		return {"ok": false, "reason": "Passe par l'écran du Bastion"}
	var d: Dictionary = tables.buildings[type_id]
	var level := int(b["level"])
	if level >= int(d["max_level"]):
		return {"ok": false, "reason": "Niveau maximum"}
	# Un bâtiment ne dépasse jamais le niveau du Bastion : verrou structurel.
	if level >= state.bastion_level():
		return {"ok": false, "reason": "Améliore d'abord le Bastion"}
	var cost := Economy.upgrade_cost(tables, type_id, level)
	if not Economy.can_afford(state, cost):
		return {"ok": false, "reason": "Ressources insuffisantes"}
	return {"ok": true, "reason": "", "cost": cost}


func upgrade(uid: int) -> bool:
	var check := can_upgrade(uid)
	if not check["ok"]:
		emit_signal("notice", String(check["reason"]))
		return false
	var b := state.village.get_building(uid)
	if not Economy.pay(state, check["cost"]):
		return false
	b["level"] = int(b["level"]) + 1
	state.stats["upgrades"] = int(state.stats.get("upgrades", 0)) + 1
	emit_signal("resources_changed")
	emit_signal("village_changed")
	return true


func move_building(uid: int, x: int, y: int) -> bool:
	## Déplacer est gratuit et illimité : rien ne justifie de le punir.
	if state.village.move(uid, x, y):
		emit_signal("village_changed")
		return true
	return false


func sell_building(uid: int) -> bool:
	var b := state.village.get_building(uid)
	if b.is_empty() or String(b["type"]) == "bastion":
		return false
	var refund := Economy.sell_refund(tables, String(b["type"]), int(b["level"]))
	if not state.village.remove(uid):
		return false
	Economy.grant(state, tables, refund)
	emit_signal("resources_changed")
	emit_signal("village_changed")
	emit_signal("notice", "Démoli. Remboursé : %s" % format_resources(refund))
	return true


# ------------------------------------------------------------- Contremaître

func foreman_plan() -> Array[Dictionary]:
	## Toutes les améliorations actuellement payables, triées par intérêt.
	## Sans ce bouton, une session longue devient 400 taps sur des murs
	## (docs/04 §7).
	var plan: Array[Dictionary] = []
	var budget := state.resources.duplicate()
	var levels := {}
	for b: Dictionary in state.village.buildings:
		levels[int(b["uid"])] = int(b["level"])

	var candidates := state.village.buildings.duplicate()
	candidates.sort_custom(func(a: Dictionary, c: Dictionary) -> bool:
		var pa := _upgrade_priority(String(a["type"]))
		var pc := _upgrade_priority(String(c["type"]))
		if pa != pc:
			return pa > pc
		return int(a["level"]) < int(c["level"]))

	for b: Dictionary in candidates:
		var type_id := String(b["type"])
		var d: Dictionary = tables.buildings.get(type_id, {})
		if d.is_empty() or type_id == "bastion" or String(d["category"]) == "deco":
			continue
		var lvl: int = levels[int(b["uid"])]
		while lvl < int(d["max_level"]) and lvl < state.bastion_level():
			var cost := tables.building_cost(type_id, lvl + 1)
			var affordable := true
			for res: String in cost.keys():
				if int(budget.get(res, 0)) < int(cost[res]):
					affordable = false
					break
			if not affordable:
				break
			for res: String in cost.keys():
				budget[res] = int(budget.get(res, 0)) - int(cost[res])
			lvl += 1
			plan.append({"uid": int(b["uid"]), "type": type_id, "to_level": lvl, "cost": cost})
	return plan


func _upgrade_priority(type_id: String) -> int:
	var d: Dictionary = tables.buildings.get(type_id, {})
	match String(d.get("category", "")):
		"production": return 5
		"storage": return 4
		"defense": return 3
		"military": return 2
		"special": return 2
	return 0


func foreman_apply() -> int:
	var plan := foreman_plan()
	var count := 0
	for step: Dictionary in plan:
		var b := state.village.get_building(int(step["uid"]))
		if b.is_empty():
			continue
		if not Economy.pay(state, step["cost"]):
			continue
		b["level"] = int(step["to_level"])
		count += 1
	if count > 0:
		state.stats["upgrades"] = int(state.stats.get("upgrades", 0)) + count
		emit_signal("resources_changed")
		emit_signal("village_changed")
		emit_signal("notice", "Contremaître : %d amélioration(s)" % count)
	else:
		emit_signal("notice", "Rien à améliorer pour l'instant")
	return count


# -------------------------------------------------------------------- armée

func army_cap() -> int:
	return Progression.army_housing(state.bastion_level()) + Tycoon.army_bonus(state)


# ------------------------------------------------------------------- récolte

func collect(uid: int) -> Dictionary:
	var got := Economy.collect(state, tables, uid)
	if not got.is_empty():
		state.stats["collected"] = int(state.stats.get("collected", 0)) + 1
		emit_signal("resources_changed")
		emit_signal("pending_changed")
		emit_signal("collected", uid, got)
	return got


func collect_all() -> Dictionary:
	var got := Economy.collect_all(state, tables)
	if not got.is_empty():
		emit_signal("resources_changed")
		emit_signal("pending_changed")
		emit_signal("notice", "Récolté : %s" % format_resources(got))
	return got


func pending_of(uid: int) -> int:
	return state.pending_total(uid)


# ------------------------------------------------------------------- quêtes

func claim_quest() -> Dictionary:
	var reward := Quests.claim(state, tables)
	if not reward.is_empty():
		emit_signal("resources_changed")
		emit_signal("quest_changed")
		emit_signal("notice", "Quête terminée : %s" % format_resources(reward))
		save_now()
	return reward


# ------------------------------------------------------------------- tycoon

func buy_upgrade(id: String) -> bool:
	if Tycoon.buy(state, id):
		emit_signal("resources_changed")
		emit_signal("village_changed")
		save_now()
		return true
	emit_signal("notice", "Or insuffisant")
	return false


func do_prestige() -> int:
	var gained := Tycoon.apply_prestige(state, tables)
	if gained > 0:
		emit_signal("resources_changed")
		emit_signal("village_changed")
		emit_signal("army_changed")
		emit_signal("prestiged", gained)
		save_now()
	return gained


func army_used() -> int:
	return state.army_size(tables)


func unit_cost(unit_id: String, count: int = 1) -> Dictionary:
	var d: Dictionary = tables.units.get(unit_id, {})
	if d.is_empty():
		return {}
	var out := {}
	for res: String in d["cost"].keys():
		var v := int(d["cost"][res]) * count
		if v > 0:
			out[res] = v
	return out


func train(unit_id: String, count: int = 1) -> int:
	## Entraînement instantané : perdre une armée coûte des ressources, jamais
	## du temps d'attente.
	var d: Dictionary = tables.units.get(unit_id, {})
	if d.is_empty():
		return 0
	if int(d["unlock"]) > state.bastion_level():
		emit_signal("notice", "Bastion %d requis" % int(d["unlock"]))
		return 0
	var trained := 0
	for i in range(count):
		if army_used() + int(d["housing"]) > army_cap():
			emit_signal("notice", "Camp plein (%d/%d)" % [army_used(), army_cap()])
			break
		var cost := unit_cost(unit_id, 1)
		if not Economy.pay(state, cost):
			emit_signal("notice", "Ressources insuffisantes")
			break
		state.army[unit_id] = int(state.army.get(unit_id, 0)) + 1
		trained += 1
	if trained > 0:
		emit_signal("resources_changed")
		emit_signal("army_changed")
	return trained


func fill_army() -> int:
	## Remplit le camp avec ce qu'on peut se payer — confort de session longue.
	var ids := tables.unlocked_units(state.bastion_level())
	ids.reverse()  # les unités les plus récentes d'abord
	var total := 0
	for id: String in ids:
		var guard := 0
		while army_used() < army_cap() and guard < 200:
			guard += 1
			if train(id, 1) == 0:
				break
			total += 1
	return total


# --------------------------------------------------------------------- raid

func start_campaign_raid(index: int) -> bool:
	if not Campaign.is_unlocked(state, index):
		emit_signal("notice", "Niveau verrouillé")
		return false
	if army_used() <= 0:
		emit_signal("notice", "Entraîne des troupes d'abord")
		return false
	var info := Campaign.level_info(index)
	raid_context = {
		"kind": "campaign",
		"index": index,
		"info": info,
		"village": Campaign.generate_level(index, tables),
		"loot": info["loot"],
		"seed": int(info["seed"]) + int(Time.get_unix_time_from_system()) % 1000,
		"army": state.army.duplicate(),
		"army_cap": army_cap(),
	}
	return true


func start_trial(trial_index: int) -> bool:
	var info := Campaign.trial_info(trial_index)
	if army_used() <= 0:
		emit_signal("notice", "Entraîne des troupes d'abord")
		return false
	raid_context = {
		"kind": "trial",
		"index": trial_index,
		"info": info,
		"village": Campaign.generate_trial(trial_index, tables),
		"loot": {},
		"seed": int(info["seed"]),
		"army": _capped_army(int(info["army_cap"])),
		"army_cap": int(info["army_cap"]),
	}
	return true


func _capped_army(cap: int) -> Dictionary:
	## Une Épreuve limite les places : on tronque la composition, sans jamais
	## toucher au stock réel du joueur.
	var out := {}
	var used := 0
	for id: String in state.army.keys():
		var d: Dictionary = tables.units.get(id, {})
		if d.is_empty():
			continue
		var housing := int(d["housing"])
		var n := 0
		while n < int(state.army[id]) and used + housing <= cap:
			used += housing
			n += 1
		if n > 0:
			out[id] = n
	return out


func finish_raid(result: Dictionary, used_units: Dictionary) -> Dictionary:
	## Applique les conséquences d'un raid. Retourne un résumé pour l'écran de
	## butin.
	var summary := {"stars": int(result["stars"]), "percent": int(result["percent"])}
	state.stats["raids_played"] = int(state.stats.get("raids_played", 0)) + 1

	# Les troupes déployées sont consommées, qu'elles survivent ou non.
	for id: String in used_units.keys():
		state.army[id] = maxi(0, int(state.army.get(id, 0)) - int(used_units[id]))
		if int(state.army[id]) == 0:
			state.army.erase(id)

	if String(raid_context.get("kind", "")) == "trial":
		var trial_idx := int(raid_context.get("index", 1))
		var bastion_level: int = int(Campaign.trial_info(trial_idx)["bastion_level"])
		if int(result["stars"]) >= 2:
			state.trials_done[bastion_level] = 1
			summary["trial_passed"] = true
			emit_signal("notice", "Épreuve réussie !")
		else:
			summary["trial_passed"] = false
	else:
		var idx := int(raid_context.get("index", 0))
		var stars := int(result["stars"])
		var before := state.stars_for(idx)
		if stars > before:
			state.campaign_stars[idx] = stars
			summary["new_best"] = true
		if stars > 0:
			state.stats["raids_won"] = int(state.stats.get("raids_won", 0)) + 1
		var boosted := {}
		var loot_mult := Tycoon.loot_multiplier(state)
		for res: String in (result["loot"] as Dictionary).keys():
			boosted[res] = int(float(result["loot"][res]) * loot_mult)
		var granted := Economy.grant(state, tables, boosted)
		summary["loot"] = granted
		var total := 0
		for v: Variant in granted.values():
			total += int(v)
		state.stats["loot_total"] = int(state.stats.get("loot_total", 0)) + total

	raid_context = {}
	emit_signal("resources_changed")
	emit_signal("army_changed")
	save_now()
	return summary


# -------------------------------------------------------------------- utils

func upgrade_bastion() -> bool:
	if Progression.upgrade_bastion(state, tables):
		var lvl := state.bastion_level()
		emit_signal("resources_changed")
		emit_signal("village_changed")
		emit_signal("quest_changed")
		emit_signal("bastion_upgraded", lvl)
		save_now()
		return true
	var why := Progression.blockers(state, tables)
	emit_signal("notice", why[0] if not why.is_empty() else "Impossible")
	return false


func format_resources(res: Dictionary) -> String:
	var parts: Array[String] = []
	for k: String in res.keys():
		if int(res[k]) > 0:
			parts.append("%d %s" % [int(res[k]), DataTables.RESOURCE_NAMES.get(k, k)])
	return ", ".join(parts) if not parts.is_empty() else "rien"


static func format_number(v: int) -> String:
	if v >= 1000000:
		return "%.1fM" % (float(v) / 1000000.0)
	if v >= 10000:
		return "%dk" % (v / 1000)
	if v >= 1000:
		return "%.1fk" % (float(v) / 1000.0)
	return str(v)
