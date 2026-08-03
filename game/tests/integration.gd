extends Node
## Test d'intégration : joue une vraie partie de bout en bout, sans interface.
##
##     godot --headless --path game res://tests/integration.tscn
##
## Il vérifie ce que les tests unitaires ne peuvent pas voir : que les systèmes
## branchés ensemble via l'autoload `Game` produisent une partie jouable —
## construire, améliorer, entraîner, attaquer, encaisser le butin, sauvegarder.

var _failures := 0
var _checks := 0


func _ready() -> void:
	print("=== Valdris — intégration ===")
	await get_tree().process_frame

	_scenario_first_minutes()
	_scenario_raid_loop()
	_scenario_never_stuck()
	_scenario_persistence()

	print("")
	if _failures == 0:
		print("✔ intégration : %d vérifications, 0 échec." % _checks)
		get_tree().quit(0)
	else:
		printerr("✘ intégration : %d échec(s) sur %d." % [_failures, _checks])
		get_tree().quit(1)


func _check(cond: bool, message: String) -> void:
	_checks += 1
	if not cond:
		_failures += 1
		printerr("   ↳ %s" % message)


# ---------------------------------------------------------------- scénarios

func _scenario_first_minutes() -> void:
	## Ce que fait un joueur dans ses cinq premières minutes.
	Game.new_game()
	var s := Game.state
	_check(s.bastion_level() == 1, "le Bastion ne démarre pas au niveau 1")
	_check(s.village.count_of("bastion") == 1, "pas exactement un Bastion")

	# Construire est instantané : le bâtiment existe dès le retour de l'appel.
	s.resources["wood"] = 5000
	s.resources["stone"] = 5000
	s.resources["gold"] = 5000
	var before := s.village.buildings.size()
	var uid := Game.build("house", 4, 4)
	_check(uid > 0, "construction refusée")
	_check(s.village.buildings.size() == before + 1, "le bâtiment n'existe pas immédiatement")
	var placed := s.village.get_building(uid)
	_check(int(placed["level"]) == 1, "le bâtiment ne démarre pas au niveau 1")

	# Aucune notion de chantier en cours nulle part dans l'état.
	_check(not placed.has("build_ends_at"), "un minuteur de construction s'est glissé dans l'état")

	# Placement invalide refusé proprement.
	var dup := Game.build("house", 4, 4)
	_check(dup == -1, "deux bâtiments sur la même case")

	# Amélioration bloquée au-delà du niveau du Bastion (verrou structurel).
	Game.state.set_bastion_level(1)
	var check := Game.can_upgrade(uid)
	_check(not bool(check["ok"]), "amélioration autorisée au-delà du Bastion")

	Game.state.set_bastion_level(6)
	_check(bool(Game.can_upgrade(uid)["ok"]), "amélioration refusée alors que tout est réuni")
	_check(Game.upgrade(uid), "amélioration échouée")
	_check(int(s.village.get_building(uid)["level"]) == 2, "le niveau n'a pas monté")

	# Le Contremaître ne doit jamais mettre le joueur à découvert.
	s.resources = {"wood": 40000, "stone": 40000, "iron": 40000, "gold": 40000, "essence": 0}
	var n := Game.foreman_apply()
	_check(n > 0, "le Contremaître ne propose rien avec 40k de chaque")
	for res: String in s.resources.keys():
		_check(int(s.resources[res]) >= 0, "solde négatif après le Contremaître (%s)" % res)

	# Déplacer est gratuit.
	var gold_before := int(s.resources["gold"])
	_check(Game.move_building(uid, 12, 12), "déplacement refusé")
	_check(int(s.resources["gold"]) == gold_before, "le déplacement a coûté quelque chose")


func _scenario_raid_loop() -> void:
	## La boucle économique : entraîner, attaquer, encaisser.
	var s := Game.state
	s.resources = {"wood": 60000, "stone": 60000, "iron": 60000, "gold": 60000, "essence": 0}
	s.set_bastion_level(6)

	var trained := Game.fill_army()
	_check(trained > 0, "impossible d'entraîner la moindre troupe")
	_check(Game.army_used() > 0, "le camp reste vide")
	_check(Game.army_used() <= Game.army_cap(), "le camp dépasse sa capacité")

	_check(Game.start_campaign_raid(0), "impossible de lancer le premier raid")
	var ctx := Game.raid_context
	_check(not ctx.is_empty(), "contexte de raid vide")

	# On rejoue le combat headless, exactement comme le ferait le serveur.
	var sim := CombatSim.new()
	sim.setup(ctx["village"], Game.tables, int(ctx["seed"]), ctx["loot"])
	var deployed := {}
	var cell := 1
	for id: String in (ctx["army"] as Dictionary).keys():
		for i in range(int(ctx["army"][id])):
			sim.queue_deploy(i * 2, id, 2 + (cell % 30), 1)
			deployed[id] = int(deployed.get(id, 0)) + 1
			cell += 1
	var result := sim.run_to_end()
	_check(int(result["percent"]) > 0, "un raid complet n'inflige aucun dégât")

	# On repart d'un stock légitime (sous les plafonds) pour pouvoir vérifier
	# que le butin lui-même ne fait jamais déborder les entrepôts.
	var caps_before := Economy.storage_caps(s, Game.tables)
	for res: String in ["wood", "stone", "iron", "gold"]:
		s.resources[res] = int(caps_before.get(res, 0)) / 2

	var wood_before := int(s.resources["wood"])
	var army_before := Game.army_used()
	var summary := Game.finish_raid(result, deployed)
	_check(Game.army_used() < army_before, "les troupes déployées n'ont pas été consommées")
	if int(result["stars"]) > 0:
		_check(s.stars_for(0) == int(result["stars"]), "les étoiles n'ont pas été enregistrées")
		var loot: Dictionary = summary.get("loot", {})
		if int(loot.get("wood", 0)) > 0:
			_check(int(s.resources["wood"]) > wood_before, "le butin n'a pas été crédité")

	# Le butin ne doit jamais faire déborder les entrepôts.
	var caps := Economy.storage_caps(s, Game.tables)
	for res: String in ["wood", "stone", "iron", "gold"]:
		_check(int(s.resources.get(res, 0)) <= int(caps.get(res, 0)),
				"dépassement du plafond de stockage (%s)" % res)


func _scenario_never_stuck() -> void:
	## LA garantie du jeu : quel que soit l'état, il y a toujours quelque chose
	## à faire. Aucun état ne doit renvoyer « attends ».
	var cases := {
		"partie neuve": func() -> void:
			Game.new_game(),
		"sans ressources ni armée": func() -> void:
			Game.new_game()
			Game.state.resources = {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 0}
			Game.state.army = {},
		"entrepôts pleins": func() -> void:
			Game.new_game()
			var caps := Economy.storage_caps(Game.state, Game.tables)
			for res: String in caps.keys():
				Game.state.resources[res] = int(caps[res]),
		"milieu de partie": func() -> void:
			Game.new_game()
			Game.state.set_bastion_level(14)
			for i in range(20):
				Game.state.campaign_stars[i] = 3
			Game.state.resources = {"wood": 9000, "stone": 9000, "iron": 9000, "gold": 9000, "essence": 40},
	}
	for name: String in cases.keys():
		(cases[name] as Callable).call()
		var best := Advisor.best(Game.state, Game.tables)
		_check(not Advisor.is_player_stuck(Game.state, Game.tables),
				"aucune action proposée — état : %s" % name)
		_check(not String(best["text"]).is_empty(), "conseil vide — état : %s" % name)


func _scenario_persistence() -> void:
	Game.new_game()
	Game.state.resources["gold"] = 4242
	Game.state.set_bastion_level(9)
	Game.state.campaign_stars[2] = 3
	Game.build("quarry", 20, 20)
	var count := Game.state.village.buildings.size()
	Game.save_now()

	Game.load_or_create()
	_check(int(Game.state.resources["gold"]) == 4242, "l'or n'a pas survécu au rechargement")
	_check(Game.state.bastion_level() == 9, "le Bastion n'a pas survécu")
	_check(Game.state.stars_for(2) == 3, "les étoiles n'ont pas survécu")
	_check(Game.state.village.buildings.size() == count, "le village n'a pas survécu")
	SaveSystem.wipe()
