class_name ShopPanel
extends Overlay
## Boutique d'améliorations permanentes + rebirth.
##
## C'est l'écran « encore un niveau » : des coûts exponentiels, des effets
## immédiatement lisibles, et un bouton qu'on peut réappuyer tout de suite.
## Rien ne s'y achète avec de l'argent réel.

signal changed


func _init() -> void:
	super("Boutique")
	set_height_ratio(0.86)
	_populate()


func _populate() -> void:
	for c: Node in body.get_children():
		c.queue_free()

	var s := Game.state
	var head := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 14)
	var head_col := UIKit.vbox(4)
	head.add_child(head_col)
	head_col.add_child(UIKit.label(
			"Production ×%.2f   ·   Butin ×%.2f" % [
				Tycoon.production_multiplier(s), Tycoon.loot_multiplier(s)],
			UIKit.FONT_MD, Palette.UI_ACCENT))
	if s.prestige_points > 0:
		head_col.add_child(UIKit.label(
				"%d point(s) de renaissance  ·  bonus permanent ×%.2f" % [
					s.prestige_points, Tycoon.prestige_multiplier(s)],
				UIKit.FONT_SM, Palette.UI_XP))
	body.add_child(head)

	var list := UIKit.vbox(10)
	for id: String in Tycoon.UPGRADE_ORDER:
		list.add_child(_upgrade_row(id))
	list.add_child(UIKit.separator())
	list.add_child(_prestige_card())
	add_scrollable(list)


func _upgrade_row(id: String) -> Control:
	var s := Game.state
	var d: Dictionary = Tycoon.UPGRADES[id]
	var lvl := Tycoon.level_of(s, id)
	var maxed := Tycoon.is_maxed(s, id)

	var card := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 14)
	var row := UIKit.hbox(12)
	card.add_child(row)

	var info := UIKit.vbox(4)
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	info.add_child(UIKit.label("%s  ·  niv. %d/%d" % [String(d["name"]), lvl, int(d["max"])],
			UIKit.FONT_MD))
	var desc := UIKit.label(String(d["desc"]), UIKit.FONT_SM, Palette.UI_TEXT_DIM)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	info.add_child(desc)
	info.add_child(UIKit.label(_effect_now(id, lvl), UIKit.FONT_SM, Palette.UI_OK))
	row.add_child(info)

	if maxed:
		var m := UIKit.button("MAX", Callable(), false, 88)
		m.disabled = true
		m.custom_minimum_size.x = 200
		row.add_child(m)
		return card

	var cost := Tycoon.cost_of(s, id)
	var can := Tycoon.can_buy(s, id)
	var buy := UIKit.button("%s or" % Game.format_number(cost), func() -> void:
		if Game.buy_upgrade(id):
			emit_signal("changed")
			_populate(), can, 88)
	buy.disabled = not can
	buy.custom_minimum_size.x = 200
	row.add_child(buy)
	return card


func _effect_now(id: String, lvl: int) -> String:
	match id:
		"yield": return "Actuellement : +%d %% de production" % (12 * lvl)
		"loot": return "Actuellement : +%d %% de butin" % (6 * lvl)
		"capacity": return "Actuellement : +%d %% de stockage" % (10 * lvl)
		"logistics": return "Actuellement : +%d places d'armée" % (2 * lvl)
		"haste": return "Actuellement : réserve de %.0f min par bâtiment" % \
				Tycoon.pending_capacity_minutes(Game.state)
		"auto":
			var itv := Tycoon.auto_collect_interval(Game.state)
			if itv <= 0.0:
				return "Pas encore de récolte automatique"
			return "Actuellement : récolte toutes les %.1f s" % itv
	return ""


func _prestige_card() -> Control:
	var s := Game.state
	var card := UIKit.panel(Palette.UI_PANEL, 16, 18)
	var col := UIKit.vbox(10)
	card.add_child(col)

	col.add_child(UIKit.label("Renaissance", UIKit.FONT_LG, Palette.UI_XP))
	var explain := UIKit.label(
			"Ton village et tes améliorations repartent de zéro. Tu gardes ta "
			+ "campagne, tes Épreuves, et tu gagnes un bonus DÉFINITIF de +10 %% "
			+ "de production et de butin par point.",
			UIKit.FONT_SM, Palette.UI_TEXT_DIM)
	explain.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(explain)

	var available := Tycoon.prestige_points_available(s)
	if available > 0:
		col.add_child(UIKit.label("%d point(s) à récupérer maintenant" % available,
				UIKit.FONT_MD, Palette.UI_OK))
		col.add_child(UIKit.button("Renaître (+%d)" % available, _confirm_prestige, true))
	else:
		for reason: String in Tycoon.prestige_blockers(s):
			col.add_child(UIKit.label("• %s" % reason, UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		var disabled := UIKit.button("Renaître", Callable(), false)
		disabled.disabled = true
		col.add_child(disabled)
	return card


func _confirm_prestige() -> void:
	## Une action irréversible mérite une confirmation explicite.
	var dialog := ConfirmationDialog.new()
	dialog.dialog_text = "Ton village et tes améliorations seront remis à zéro.\n" \
			+ "Tu gardes ta campagne et tu gagnes un bonus permanent.\n\nContinuer ?"
	dialog.title = "Renaissance"
	dialog.ok_button_text = "Renaître"
	dialog.cancel_button_text = "Annuler"
	add_child(dialog)
	dialog.confirmed.connect(func() -> void:
		var gained := Game.do_prestige()
		if gained > 0:
			emit_signal("changed")
			close())
	dialog.popup_centered()
