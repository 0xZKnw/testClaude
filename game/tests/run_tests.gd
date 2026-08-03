extends SceneTree
## Suite de tests headless.
##
##     godot --headless --path game --script res://tests/run_tests.gd
##
## Elle tourne en CI avant chaque build. Le test qui compte le plus est
## `test_combat_determinism` : si le combat cesse d'être reproductible, le PvP
## asynchrone n'est plus validable côté serveur et tout l'édifice s'écroule.

var _failures: int = 0
var _checks: int = 0
var _current: String = ""


func _initialize() -> void:
	print("=== Valdris — tests ===")
	var tables := DataTables.new()
	if not tables.load_all("res://data"):
		printerr("ÉCHEC CRITIQUE : données introuvables")
		quit(1)
		return

	_run("Fix — virgule fixe", func() -> void: test_fix())
	_run("DetRandom — reproductibilité", func() -> void: test_random())
	_run("DataTables — chargement et courbes", func() -> void: test_tables(tables))
	_run("Village — grille et collisions", func() -> void: test_village())
	_run("Village — sérialisation", func() -> void: test_village_serialization())
	_run("Économie — plafonds et paiements", func() -> void: test_economy(tables))
	_run("Progression — verrous du Bastion", func() -> void: test_progression(tables))
	_run("VillageGen — génération procédurale", func() -> void: test_village_gen(tables))
	_run("Campagne — 72 niveaux + 8 Épreuves", func() -> void: test_campaign(tables))
	_run("Combat — déroulement", func() -> void: test_combat_runs(tables))
	_run("Combat — DÉTERMINISME", func() -> void: test_combat_determinism(tables))
	_run("Combat — replay depuis inputs", func() -> void: test_combat_replay(tables))
	_run("Sauvegarde — aller-retour", func() -> void: test_save())
	_run("Contremaître — plan cohérent", func() -> void: test_foreman(tables))

	print("")
	if _failures == 0:
		print("✔ %d vérifications, 0 échec." % _checks)
		quit(0)
	else:
		printerr("✘ %d échec(s) sur %d vérifications." % [_failures, _checks])
		quit(1)


func _run(name: String, fn: Callable) -> void:
	_current = name
	var before := _failures
	fn.call()
	var mark := "  ok  " if _failures == before else " FAIL "
	print("[%s] %s" % [mark, name])


func _check(cond: bool, message: String) -> void:
	_checks += 1
	if not cond:
		_failures += 1
		printerr("   ↳ %s : %s" % [_current, message])


func _eq(a: Variant, b: Variant, message: String) -> void:
	_check(a == b, "%s (obtenu %s, attendu %s)" % [message, str(a), str(b)])


# ------------------------------------------------------------------- tests

func test_fix() -> void:
	_eq(Fix.to_int(Fix.from_int(7)), 7, "aller-retour entier")
	_eq(Fix.to_int(Fix.mul(Fix.from_int(6), Fix.from_int(7))), 42, "6*7")
	_eq(Fix.to_int(Fix.div(Fix.from_int(84), Fix.from_int(4))), 21, "84/4")
	_eq(Fix.isqrt(144), 12, "isqrt(144)")
	_eq(Fix.isqrt(0), 0, "isqrt(0)")
	_eq(Fix.to_int(Fix.sqrt_fix(Fix.from_int(81))), 9, "sqrt(81)")
	# La distance doit rester exacte sur un triangle 3-4-5.
	var d := Fix.dist(0, 0, Fix.from_int(3), Fix.from_int(4))
	_check(absi(d - Fix.from_int(5)) <= 8, "distance 3-4-5 (obtenu %f)" % Fix.to_float(d))
	_eq(Fix.div(Fix.from_int(1), 0), 0, "division par zéro sûre")


func test_random() -> void:
	var a := DetRandom.new(12345)
	var b := DetRandom.new(12345)
	var same := true
	for i in range(500):
		if a.next_u32() != b.next_u32():
			same = false
			break
	_check(same, "deux générateurs de même graine divergent")

	var c := DetRandom.new(999)
	var seen := {}
	for i in range(200):
		seen[c.next_range(0, 10)] = true
	_check(seen.size() >= 8, "next_range couvre mal l'intervalle")

	var d := DetRandom.new(7)
	for i in range(100):
		var v := d.next_range(5, 9)
		if v < 5 or v >= 9:
			_check(false, "next_range hors bornes: %d" % v)
			break
	_check(true, "bornes respectées")


func test_tables(tables: DataTables) -> void:
	_check(tables.buildings.size() >= 15, "trop peu de bâtiments chargés")
	_check(tables.units.size() >= 8, "trop peu d'unités chargées")
	_check(tables.buildings.has("bastion"), "bastion absent")
	_check(tables.buildings.has("wall"), "mur absent")

	# Le coût doit croître plus vite que la production : c'est ce qui empêche
	# le jeu de devenir un idle qui se joue tout seul (docs/03 §2).
	var c1 := tables.building_cost("sawmill", 1)
	var c5 := tables.building_cost("sawmill", 5)
	_check(int(c5["wood"]) > int(c1["wood"]) * 2, "les coûts ne croissent pas assez")
	var p1 := tables.building_production("sawmill", 1)
	var p5 := tables.building_production("sawmill", 5)
	_check(p5 > p1, "la production ne croît pas")
	var cost_ratio := float(c5["wood"]) / float(c1["wood"])
	var prod_ratio := float(p5) / float(p1)
	_check(cost_ratio > prod_ratio, "le passif ne décroche pas (coût x%.2f vs prod x%.2f)" % [cost_ratio, prod_ratio])

	_check(tables.building_dps("archer_tower", 1) > 0, "la tour d'archers ne tire pas")
	_check(tables.building_dps("sawmill", 1) == 0, "la scierie tire (?)")
	_check(int(tables.units["healer"]["dps"]) < 0, "le guérisseur devrait soigner")
	# Le verrou structurel : rien n'est disponible avant son palier.
	_eq(tables.building_max_count("wall", 1), 0, "les murs ne sont pas verrouillés au Bastion 1")
	_check(tables.building_max_count("wall", 3) >= 25, "murs indisponibles au Bastion 3")
	_check(tables.building_max_count("wall", 12) > tables.building_max_count("wall", 3),
			"le quota de murs ne grandit pas")
	_eq(tables.building_max_count("tesla", 1), 0, "la tour à foudre devrait être verrouillée")
	_check(tables.building_max_count("tesla", 12) > 0, "la tour à foudre ne s'ouvre jamais")
	_eq(tables.unlocked_buildings(1).size(), 4, "bâtiments disponibles au démarrage")
	_check(tables.unlocked_buildings(12).size() > tables.unlocked_buildings(1).size(),
			"rien de neuf ne se débloque en montant")


func test_village() -> void:
	var v := Village.new(20, 20)
	var uid := v.place("bastion", 1, 8, 8, 4)
	_check(uid > 0, "placement initial refusé")
	_check(not v.can_place(9, 9, 2), "collision non détectée")
	_check(v.can_place(0, 0, 3), "placement libre refusé")
	_check(not v.can_place(19, 19, 3), "débordement non détecté")
	_eq(v.count_of("bastion"), 1, "comptage")
	_check(not v.building_at(9, 9).is_empty(), "building_at ne trouve rien")
	_check(v.building_at(0, 0).is_empty(), "building_at trouve un fantôme")

	_check(v.move(uid, 2, 2), "déplacement refusé")
	_check(v.can_place(8, 8, 4), "l'ancienne place n'a pas été libérée")
	var uid2 := v.place("sawmill", 1, 10, 10, 3)
	_check(not v.move(uid2, 2, 2), "déplacement sur une case occupée accepté")
	_check(v.remove(uid2), "suppression refusée")
	_eq(v.buildings.size(), 1, "taille après suppression")
	_check(v.can_place(10, 10, 3), "la case n'a pas été libérée")


func test_village_serialization() -> void:
	var v := Village.new(30, 30)
	v.place("bastion", 3, 10, 10, 4)
	v.place("wall", 2, 5, 5, 1)
	v.place("sawmill", 1, 20, 20, 3)
	var round_trip := Village.from_dict(v.to_dict())
	_eq(round_trip.buildings.size(), 3, "nombre de bâtiments")
	_eq(round_trip.width, 30, "largeur")
	_check(not round_trip.can_place(10, 10, 1), "index d'occupation non reconstruit")
	_check(round_trip.can_place(0, 0, 2), "index d'occupation trop large")


func test_economy(tables: DataTables) -> void:
	var s := PlayerState.create_new(tables)
	var caps := Economy.storage_caps(s, tables)
	_check(int(caps["wood"]) > 0, "aucun stockage de bois")

	s.resources = {"wood": 100, "stone": 100, "iron": 0, "gold": 0, "essence": 0}
	_check(Economy.can_afford(s, {"wood": 50}), "paiement possible refusé")
	_check(not Economy.can_afford(s, {"wood": 500}), "paiement impossible accepté")
	_check(Economy.pay(s, {"wood": 40}), "paiement échoué")
	_eq(int(s.resources["wood"]), 60, "solde après paiement")
	_check(not Economy.pay(s, {"wood": 9999}), "paiement à découvert accepté")
	_eq(int(s.resources["wood"]), 60, "solde modifié par un paiement refusé")

	# Le plafond de stockage doit vraiment plafonner.
	var granted := Economy.grant(s, tables, {"wood": 999999})
	_check(int(s.resources["wood"]) <= int(caps["wood"]), "le plafond de stockage est ignoré")
	_check(int(granted.get("wood", 0)) < 999999, "le butin annoncé ignore le plafond")

	# Production : une heure de scierie doit rapporter quelque chose.
	var s2 := PlayerState.create_new(tables)
	s2.resources = {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 0}
	var gains := Economy.tick(s2, tables, 3600.0)
	_check(int(gains.get("wood", 0)) > 0, "aucune production sur une heure")

	var missing := Economy.missing_for(s, {"iron": 30})
	_eq(int(missing.get("iron", 0)), 30, "calcul du manque")


func test_progression(tables: DataTables) -> void:
	var s := PlayerState.create_new(tables)
	_eq(s.bastion_level(), 1, "niveau de Bastion initial")

	var cost2 := Progression.bastion_cost(2)
	_check(int(cost2["gold"]) > 0, "le Bastion 2 est gratuit (?)")
	var cost10 := Progression.bastion_cost(10)
	_check(int(cost10["gold"]) > int(cost2["gold"]) * 5, "coûts trop plats")
	_check(cost10.has("essence"), "l'Essence devrait être exigée à partir du Bastion 10")

	# Sans étoiles ni ressources, la montée doit être bloquée — et le jeu doit
	# dire pourquoi.
	var why := Progression.blockers(s, tables)
	_check(not why.is_empty(), "le Bastion monte sans rien")
	_check(not Progression.can_upgrade_bastion(s, tables), "montée autorisée à tort")

	_eq(Progression.required_stars(2), 0, "aucune étoile requise au premier palier")
	_check(Progression.required_stars(10) > 0, "aucune étoile requise au Bastion 10")
	_check(Progression.requires_trial(5), "le Bastion 5 devrait exiger une Épreuve")
	_check(not Progression.requires_trial(6), "le Bastion 6 ne devrait pas exiger d'Épreuve")
	_eq(Progression.trial_index(15), 3, "index d'Épreuve")
	_check(Progression.army_housing(10) > Progression.army_housing(1), "le camp ne grandit pas")


func test_village_gen(tables: DataTables) -> void:
	var easy := VillageGen.generate(1234, 1, tables)
	var hard := VillageGen.generate(1234, 40, tables)
	_check(easy.buildings.size() > 3, "village facile trop vide")
	_check(hard.buildings.size() > easy.buildings.size(), "la difficulté n'ajoute rien")
	_eq(easy.count_of("bastion"), 1, "un seul Bastion par village")
	_check(hard.count_of("wall") > 0, "pas d'enceinte à haute difficulté")

	# Même graine -> même village, sinon aucun replay n'est possible.
	var a := VillageGen.generate(777, 12, tables)
	var b := VillageGen.generate(777, 12, tables)
	_eq(a.buildings.size(), b.buildings.size(), "génération non déterministe (taille)")
	var identical := true
	for i in range(a.buildings.size()):
		if a.buildings[i]["type"] != b.buildings[i]["type"] \
				or a.buildings[i]["x"] != b.buildings[i]["x"] \
				or a.buildings[i]["y"] != b.buildings[i]["y"]:
			identical = false
			break
	_check(identical, "génération non déterministe (contenu)")

	var pool := VillageGen.loot_pool(10)
	_check(int(pool["wood"]) > 0, "butin vide")
	_check(int(VillageGen.loot_pool(30)["wood"]) > int(pool["wood"]), "le butin ne monte pas")


func test_campaign(tables: DataTables) -> void:
	_eq(Campaign.level_count(), 72, "nombre de niveaux de campagne")
	_eq(Campaign.trial_count(), 8, "nombre d'Épreuves")
	var first := Campaign.level_info(0)
	var last := Campaign.level_info(71)
	_eq(int(first["chapter"]), 0, "chapitre du premier niveau")
	_eq(int(last["chapter"]), 7, "chapitre du dernier niveau")
	_check(int(last["difficulty"]) > int(first["difficulty"]), "difficulté non croissante")
	_check(int(last["bastion_req"]) > int(first["bastion_req"]), "prérequis non croissant")

	var v := Campaign.generate_level(3, tables)
	_check(v.buildings.size() > 0, "niveau vide")

	var s := PlayerState.create_new(tables)
	_check(Campaign.is_unlocked(s, 0), "le premier niveau est verrouillé")
	_check(not Campaign.is_unlocked(s, 1), "le second niveau est ouvert sans étoile")
	s.campaign_stars[0] = 2
	_check(Campaign.is_unlocked(s, 1), "le second niveau reste fermé après victoire")
	_eq(Campaign.pending_trial(s), 0, "Épreuve exigée trop tôt")
	s.set_bastion_level(4)
	_eq(Campaign.pending_trial(s), 1, "l'Épreuve 1 devrait être attendue au Bastion 5")


func test_combat_runs(tables: DataTables) -> void:
	var village := VillageGen.generate(4242, 6, tables)
	var sim := CombatSim.new()
	sim.setup(village, tables, 99, {"wood": 1000, "gold": 500})
	for i in range(14):
		sim.queue_deploy(i * 2, "militia", 2 + i, 2)
	var res := sim.run_to_end()
	_check(int(res["ticks"]) > 0, "la simulation n'a pas tourné")
	_check(int(res["percent"]) >= 0 and int(res["percent"]) <= 100, "pourcentage hors bornes")
	_check(int(res["stars"]) >= 0 and int(res["stars"]) <= 3, "étoiles hors bornes")
	_check(sim.finished, "la simulation ne se termine pas")

	# Une armée écrasante doit obtenir 3 étoiles et le butin maximal.
	var weak := Village.new(20, 20)
	weak.place("bastion", 1, 8, 8, 4)
	var sim2 := CombatSim.new()
	sim2.setup(weak, tables, 1, {"gold": 1000})
	for i in range(20):
		sim2.queue_deploy(0, "knight", 4 + (i % 8), 4)
	var res2 := sim2.run_to_end()
	_eq(int(res2["percent"]), 100, "un village sans défense doit tomber")
	_eq(int(res2["stars"]), 3, "3 étoiles attendues")
	_eq(int(res2["loot"]["gold"]), 1800, "bonus de butin 3 étoiles (x1.8)")

	# Aucun déploiement -> aucun dégât.
	var sim3 := CombatSim.new()
	sim3.setup(weak, tables, 1, {"gold": 1000})
	var res3 := sim3.run_to_end(40)
	_eq(int(res3["percent"]), 0, "destruction sans attaquant")

	# Les défenses doivent tuer des unités faibles.
	var fort := Village.new(20, 20)
	fort.place("bastion", 5, 8, 8, 4)
	for i in range(6):
		fort.place("cannon", 8, 4 + i * 2, 5, 2)
	var sim4 := CombatSim.new()
	sim4.setup(fort, tables, 3, {})
	for i in range(6):
		sim4.queue_deploy(0, "militia", 3 + i, 1)
	var res4 := sim4.run_to_end()
	_check(int(res4["units_lost"]) > 0, "les défenses ne tuent personne")


func test_combat_determinism(tables: DataTables) -> void:
	## LE test critique. Deux exécutions identiques doivent produire la même
	## empreinte, sinon la validation serveur des combats PvP est impossible.
	var village := VillageGen.generate(31337, 18, tables)
	var checksums: Array[int] = []
	var results: Array[Dictionary] = []
	for run in range(3):
		var sim := CombatSim.new()
		sim.setup(village, tables, 555, {"wood": 5000, "gold": 3000, "iron": 800})
		var rng := DetRandom.new(8080)
		for i in range(24):
			var kind: String = ["militia", "archer", "sapper", "guardian"][i % 4]
			sim.queue_deploy(i * 3, kind, rng.next_range(1, 33), rng.next_range(1, 4))
		var res := sim.run_to_end()
		checksums.append(int(res["checksum"]))
		results.append(res)

	_eq(checksums[1], checksums[0], "empreinte différente entre deux exécutions")
	_eq(checksums[2], checksums[0], "empreinte différente à la 3e exécution")
	_eq(results[1]["percent"], results[0]["percent"], "pourcentage non reproductible")
	_eq(results[1]["ticks"], results[0]["ticks"], "durée non reproductible")
	_eq(results[1]["units_lost"], results[0]["units_lost"], "pertes non reproductibles")
	_check(int(checksums[0]) != 0, "empreinte nulle : la simulation n'a rien fait")


func test_combat_replay(tables: DataTables) -> void:
	## Ce que ferait le serveur : rejouer (village + seed + inputs) et comparer.
	var village := VillageGen.generate(2024, 10, tables)
	var sim := CombatSim.new()
	sim.setup(village, tables, 4321, {"gold": 2000})
	var rng := DetRandom.new(11)
	for i in range(16):
		sim.deploy_now("archer", rng.next_range(1, 33), 1)
		sim.step()
	var original := sim.run_to_end()
	var inputs := sim.get_inputs()

	var replayed := CombatSim.replay(village, tables, 4321, inputs, {"gold": 2000})
	_eq(replayed["checksum"], original["checksum"], "le replay diverge de l'original")
	_eq(replayed["percent"], original["percent"], "pourcentage du replay")
	_eq(replayed["stars"], original["stars"], "étoiles du replay")

	# Un rapport falsifié (inputs différents) doit produire une autre empreinte.
	var tampered := inputs.duplicate(true)
	if tampered.size() > 0:
		tampered.append({"tick": 0, "unit": "knight", "x": Fix.from_int(5), "y": Fix.from_int(1)})
		var cheated := CombatSim.replay(village, tables, 4321, tampered, {"gold": 2000})
		_check(int(cheated["checksum"]) != int(original["checksum"]), "une triche passe inaperçue")


func test_save() -> void:
	SaveSystem.wipe()
	var tables := DataTables.new()
	tables.load_all("res://data")
	var s := PlayerState.create_new(tables)
	s.resources = {"wood": 1234, "stone": 55, "iron": 7, "gold": 900, "essence": 3}
	s.campaign_stars[4] = 3
	s.trials_done[5] = 1
	s.army = {"militia": 9, "archer": 4}
	s.set_bastion_level(6)
	_check(SaveSystem.save(s), "écriture de la sauvegarde échouée")

	var back := SaveSystem.load_state()
	_check(back != null, "relecture impossible")
	if back == null:
		return
	_eq(int(back.resources["wood"]), 1234, "ressources restaurées")
	_eq(back.bastion_level(), 6, "niveau de Bastion restauré")
	_eq(back.stars_for(4), 3, "étoiles restaurées")
	_eq(int(back.army["militia"]), 9, "armée restaurée")
	_check(back.trials_done.has(5), "Épreuves restaurées")
	_eq(back.village.buildings.size(), s.village.buildings.size(), "village restauré")
	_check(not back.village.can_place(back.village.width / 2, back.village.height / 2, 1),
			"le Bastion n'occupe plus le centre après relecture")

	# Une sauvegarde corrompue ne doit jamais être acceptée.
	var f := FileAccess.open(SaveSystem.MAIN_PATH, FileAccess.WRITE)
	f.store_string('{"version":1,"checksum":42,"body":"{}"}')
	f.close()
	var recovered := SaveSystem.load_state()
	_check(recovered != null, "aucune restauration depuis la copie de secours")
	if recovered != null:
		_eq(int(recovered.resources["wood"]), 1234, "la copie de secours ne contient pas les données")
	SaveSystem.wipe()


func test_foreman(tables: DataTables) -> void:
	## Le Contremaître ne doit jamais proposer plus que ce que le joueur peut
	## payer — sinon le bouton ment.
	var s := PlayerState.create_new(tables)
	s.set_bastion_level(8)
	s.resources = {"wood": 50000, "stone": 50000, "iron": 50000, "gold": 50000, "essence": 0}

	var budget := s.resources.duplicate()
	var levels := {}
	for b: Dictionary in s.village.buildings:
		levels[int(b["uid"])] = int(b["level"])

	# Reproduction de la logique de Game.foreman_plan() sans l'autoload.
	var plan: Array[Dictionary] = []
	for b: Dictionary in s.village.buildings:
		var type_id := String(b["type"])
		var d: Dictionary = tables.buildings.get(type_id, {})
		if d.is_empty() or type_id == "bastion" or String(d["category"]) == "deco":
			continue
		var lvl: int = levels[int(b["uid"])]
		while lvl < int(d["max_level"]) and lvl < s.bastion_level():
			var cost := tables.building_cost(type_id, lvl + 1)
			var ok := true
			for res: String in cost.keys():
				if int(budget.get(res, 0)) < int(cost[res]):
					ok = false
					break
			if not ok:
				break
			for res: String in cost.keys():
				budget[res] = int(budget.get(res, 0)) - int(cost[res])
			lvl += 1
			plan.append({"uid": int(b["uid"]), "to_level": lvl, "cost": cost})

	_check(plan.size() > 0, "aucune amélioration proposée avec 50k de chaque")
	for res: String in budget.keys():
		_check(int(budget[res]) >= 0, "le plan dépense plus que le budget (%s)" % res)
	# Aucun bâtiment ne doit dépasser le niveau du Bastion.
	for step: Dictionary in plan:
		_check(int(step["to_level"]) <= s.bastion_level(), "amélioration au-delà du Bastion")
