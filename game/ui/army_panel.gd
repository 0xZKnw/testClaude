class_name ArmyPanel
extends Overlay
## Entraînement des troupes.
##
## Instantané, comme tout le reste : perdre une armée coûte des ressources,
## jamais une file d'attente de 20 minutes.

signal changed


func _init() -> void:
	super("Armée")
	set_height_ratio(0.8)
	_populate()


func _populate() -> void:
	for c: Node in body.get_children():
		c.queue_free()

	var head := UIKit.hbox(12)
	head.add_child(UIKit.label("Camp : %d / %d places" % [Game.army_used(), Game.army_cap()],
			UIKit.FONT_MD, Palette.UI_ACCENT))
	head.add_child(UIKit.spacer())
	head.add_child(UIKit.button("Remplir", func() -> void:
		Game.fill_army()
		emit_signal("changed")
		_populate(), true, 80))
	body.add_child(head)
	body.add_child(UIKit.separator())

	var list := UIKit.vbox(10)
	var bastion := Game.state.bastion_level()
	for id: String in Game.tables.unit_order:
		var d: Dictionary = Game.tables.units[id]
		if int(d["unlock"]) > bastion + 4:
			continue
		list.add_child(_row(id, d, int(d["unlock"]) <= bastion))
	add_scrollable(list)


func _row(id: String, d: Dictionary, unlocked: bool) -> Control:
	var card := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 14)
	var row := UIKit.hbox(12)
	card.add_child(row)
	row.add_child(UIKit.icon_swatch(Palette.get_color(String(d["color"])), 46))

	var info := UIKit.vbox(4)
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var stock := int(Game.state.army.get(id, 0))
	info.add_child(UIKit.label("%s  ×%d" % [String(d["name"]), stock], UIKit.FONT_MD,
			Palette.UI_TEXT if unlocked else Palette.UI_TEXT_DIM))
	if unlocked:
		info.add_child(UIKit.label(_describe(d), UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		info.add_child(UIKit.cost_line(Game.unit_cost(id, 1), func(res: String, amount: int) -> bool:
			return int(Game.state.resources.get(res, 0)) >= amount))
	else:
		info.add_child(UIKit.label("Bastion %d requis" % int(d["unlock"]),
				UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	row.add_child(info)

	if unlocked:
		var buttons := UIKit.hbox(8)
		buttons.add_child(UIKit.button("+1", func() -> void:
			Game.train(id, 1)
			emit_signal("changed")
			_populate(), false, 78))
		buttons.add_child(UIKit.button("+5", func() -> void:
			Game.train(id, 5)
			emit_signal("changed")
			_populate(), true, 78))
		row.add_child(buttons)
	return card


func _describe(d: Dictionary) -> String:
	var role := "cible tout"
	match int(d["target_pref"]):
		DataTables.TargetPref.DEFENSE: role = "chasse les défenses"
		DataTables.TargetPref.WALL: role = "brise les murs"
		DataTables.TargetPref.BUILDING: role = "vise les bâtiments"
		DataTables.TargetPref.ALLY: role = "soigne les alliés"
	var dps := int(d["dps"])
	var dmg := "soin %d/s" % absi(dps) if dps < 0 else "%d dégâts/s" % dps
	return "%d PV · %s · %s · %d place(s)" % [int(d["hp"]), dmg, role, int(d["housing"])]
