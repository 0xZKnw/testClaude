class_name Advisor
extends RefCounted
## Le garde-fou du pilier « zéro attente » (docs/02 §4).
##
## À tout instant, le jeu doit pouvoir répondre à « qu'est-ce que je fais
## maintenant ? ». Si le meilleur score tombe sous SILENCE_THRESHOLD, c'est que
## le joueur n'a rien à faire — ce qui, dans un jeu sans minuteurs, est un
## défaut de conception à corriger, pas une fatalité à subir.
##
## Le même scoreur servira plus tard à piloter la rotation entre verbes et les
## notifications, et à repérer en télémétrie les trous de contenu.

const SILENCE_THRESHOLD := 10

enum Verb { BUILD, RAID, MANAGE, PROGRESS }


static func suggestions(state: PlayerState, tables: DataTables) -> Array[Dictionary]:
	## Retourne les actions possibles, triées par intérêt décroissant.
	## Chaque entrée : {text, action, score, verb}
	var out: Array[Dictionary] = []

	# --- progression : le palier suivant est la meilleure nouvelle possible
	if Progression.can_upgrade_bastion(state, tables):
		out.append({"text": "Le Bastion peut monter d'un niveau", "action": "bastion",
				"score": 100, "verb": Verb.PROGRESS})

	var trial := Campaign.pending_trial(state)
	if trial > 0:
		var info := Campaign.trial_info(trial)
		out.append({"text": "Épreuve %d — %s" % [trial, String(info["name"])],
				"action": "bastion", "score": 82, "verb": Verb.RAID})

	# --- armée vide : c'est le blocage le plus fréquent, il passe devant
	var army := state.army_size(tables)
	if army <= 0:
		out.append({"text": "Entraîne des troupes", "action": "army", "score": 90, "verb": Verb.MANAGE})
	elif army < Progression.army_housing(state.bastion_level()) / 2:
		out.append({"text": "Ton camp est à moitié vide", "action": "army", "score": 46,
				"verb": Verb.MANAGE})

	# --- entrepôts pleins : signal fort de « va dépenser »
	var caps := Economy.storage_caps(state, tables)
	var full_count := 0
	for res: String in ["wood", "stone", "iron", "gold"]:
		var cap := int(caps.get(res, 0))
		if cap > 0 and int(state.resources.get(res, 0)) >= cap:
			full_count += 1
	if full_count >= 2:
		out.append({"text": "Tes entrepôts débordent — dépense", "action": "foreman",
				"score": 78, "verb": Verb.BUILD})

	# --- améliorations payables tout de suite
	var plan_size := _affordable_upgrades(state, tables)
	if plan_size > 0:
		out.append({"text": "%d amélioration%s payable%s" % [plan_size,
				"s" if plan_size > 1 else "", "s" if plan_size > 1 else ""],
				"action": "foreman", "score": 60 + mini(plan_size, 12),
				"verb": Verb.BUILD})

	# --- nouveau bâtiment posable
	var buildable := _first_buildable(state, tables)
	if not buildable.is_empty():
		out.append({"text": "Tu peux poser : %s" % tables.building_name(buildable),
				"action": "build", "score": 64, "verb": Verb.BUILD})

	# --- raid : le moteur économique du jeu
	if army > 0:
		var next_level := _next_campaign_level(state)
		if next_level >= 0:
			var info := Campaign.level_info(next_level)
			var replay := state.stars_for(next_level) > 0
			out.append({
				"text": "Attaque %s" % String(info["name"]),
				"action": "attack",
				"score": 70 if not replay else 52,
				"verb": Verb.RAID,
			})

	out.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		return int(a["score"]) > int(b["score"]))
	return out


static func best(state: PlayerState, tables: DataTables) -> Dictionary:
	var list := suggestions(state, tables)
	if list.is_empty():
		return {"text": "Explore ton village", "action": "", "score": 0, "verb": Verb.MANAGE}
	return list[0]


static func is_player_stuck(state: PlayerState, tables: DataTables) -> bool:
	## Utilisé par les tests : dans un jeu sans minuteurs, ceci doit TOUJOURS
	## être faux, quel que soit l'état de la partie.
	return int(best(state, tables)["score"]) < SILENCE_THRESHOLD


static func _affordable_upgrades(state: PlayerState, tables: DataTables) -> int:
	var budget := state.resources.duplicate()
	var count := 0
	for b: Dictionary in state.village.buildings:
		var type_id := String(b["type"])
		var d: Dictionary = tables.buildings.get(type_id, {})
		if d.is_empty() or type_id == "bastion" or String(d["category"]) == "deco":
			continue
		var lvl := int(b["level"])
		if lvl >= int(d["max_level"]) or lvl >= state.bastion_level():
			continue
		var cost := tables.building_cost(type_id, lvl + 1)
		var ok := true
		for res: String in cost.keys():
			if int(budget.get(res, 0)) < int(cost[res]):
				ok = false
				break
		if ok:
			for res: String in cost.keys():
				budget[res] = int(budget.get(res, 0)) - int(cost[res])
			count += 1
	return count


static func _first_buildable(state: PlayerState, tables: DataTables) -> String:
	for id: String in tables.unlocked_buildings(state.bastion_level()):
		var d: Dictionary = tables.buildings[id]
		if String(d["category"]) == "deco":
			continue
		if state.village.count_of(id) >= tables.building_max_count(id, state.bastion_level()):
			continue
		if Economy.can_afford(state, tables.building_cost(id, 1)):
			return id
	return ""


static func _next_campaign_level(state: PlayerState) -> int:
	## Le premier niveau non parfait et accessible.
	var fallback := -1
	for i in range(Campaign.level_count()):
		if not Campaign.is_unlocked(state, i):
			continue
		if state.stars_for(i) < 3:
			return i
		fallback = i
	return fallback
