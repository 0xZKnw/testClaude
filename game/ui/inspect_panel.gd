class_name InspectPanel
extends Overlay
## Fiche d'un bâtiment sélectionné : améliorer, déplacer, démolir.

signal move_requested(uid: int)
signal changed

var _uid: int


func _init(uid: int) -> void:
	_uid = uid
	super("")
	set_height_ratio(0.52)
	_populate()


func _populate() -> void:
	for c: Node in body.get_children():
		c.queue_free()

	var b := Game.state.village.get_building(_uid)
	if b.is_empty():
		close()
		return
	var type_id := String(b["type"])
	var d: Dictionary = Game.tables.buildings.get(type_id, {})
	var level := int(b["level"])
	set_title("%s  ·  niveau %d" % [String(d["name"]), level])

	# Effet actuel -> effet après amélioration, pour rendre le gain lisible.
	var stats := UIKit.vbox(6)
	stats.add_child(_stat_line(type_id, d, level))
	body.add_child(stats)
	body.add_child(UIKit.separator())

	if type_id == "bastion":
		body.add_child(UIKit.label("Le Bastion s'améliore depuis son propre écran.",
				UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	else:
		var check := Game.can_upgrade(_uid)
		if bool(check["ok"]):
			var cost: Dictionary = check["cost"]
			body.add_child(UIKit.label("Amélioration vers le niveau %d" % (level + 1), UIKit.FONT_SM))
			body.add_child(UIKit.cost_line(cost, func(res: String, amount: int) -> bool:
				return int(Game.state.resources.get(res, 0)) >= amount))
			body.add_child(UIKit.button("Améliorer maintenant", _do_upgrade, true))
		else:
			body.add_child(UIKit.label(String(check["reason"]), UIKit.FONT_SM, Palette.UI_TEXT_DIM))
			var cost2 := Game.upgrade_cost_of(_uid)
			if not cost2.is_empty():
				body.add_child(UIKit.cost_line(cost2, func(res: String, amount: int) -> bool:
					return int(Game.state.resources.get(res, 0)) >= amount))
			var disabled := UIKit.button("Améliorer", Callable(), false)
			disabled.disabled = true
			body.add_child(disabled)

	var actions := UIKit.hbox(12)
	var move_btn := UIKit.button("Déplacer (gratuit)", func() -> void:
		emit_signal("move_requested", _uid)
		close())
	move_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	actions.add_child(move_btn)

	if type_id != "bastion":
		var sell_btn := UIKit.button("Démolir", _do_sell)
		sell_btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		sell_btn.add_theme_color_override("font_color", Palette.UI_DANGER)
		actions.add_child(sell_btn)
	body.add_child(actions)


func _stat_line(type_id: String, d: Dictionary, level: int) -> Control:
	var tables := Game.tables
	var col := UIKit.vbox(4)
	var next := level + 1
	var capped := next > int(d["max_level"])

	if not String(d["prod_res"]).is_empty():
		var res_name: String = DataTables.RESOURCE_NAMES.get(String(d["prod_res"]), "")
		var now := tables.building_production(type_id, level)
		var then := tables.building_production(type_id, next)
		col.add_child(_delta("Production", "%d %s/min" % [now, res_name],
				"" if capped else "%d %s/min" % [then, res_name]))
	if not (d["store_res"] as Array).is_empty():
		var now2 := tables.building_storage(type_id, level)
		var then2 := tables.building_storage(type_id, next)
		col.add_child(_delta("Stockage", str(now2), "" if capped else str(then2)))
	if int(d["base_dps"]) > 0:
		var now3 := tables.building_dps(type_id, level)
		var then3 := tables.building_dps(type_id, next)
		col.add_child(_delta("Dégâts/s", str(now3), "" if capped else str(then3)))
		col.add_child(_delta("Portée", "%.1f" % float(d["range"]), ""))
	var hp_now := tables.building_hp(type_id, level)
	var hp_next := tables.building_hp(type_id, next)
	col.add_child(_delta("Résistance", str(hp_now), "" if capped else str(hp_next)))
	return col


func _delta(name: String, current: String, next: String) -> Control:
	var row := UIKit.hbox(10)
	var l := UIKit.label(name, UIKit.FONT_SM, Palette.UI_TEXT_DIM)
	l.custom_minimum_size.x = 260
	row.add_child(l)
	row.add_child(UIKit.label(current, UIKit.FONT_SM))
	if not next.is_empty():
		row.add_child(UIKit.label("→", UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		row.add_child(UIKit.label(next, UIKit.FONT_SM, Palette.UI_OK))
	return row


func _do_upgrade() -> void:
	if Game.upgrade(_uid):
		emit_signal("changed")
		_populate()


func _do_sell() -> void:
	if Game.sell_building(_uid):
		emit_signal("changed")
		close()
