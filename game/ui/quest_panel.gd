class_name QuestPanel
extends Overlay
## Liste complète des quêtes : ce qui est fait, ce qui est en cours, et un
## aperçu de ce qui attend. Voir la suite est un moteur en soi.

signal claimed


func _init() -> void:
	super("Quêtes")
	set_height_ratio(0.8)
	_populate()


func _populate() -> void:
	for c: Node in body.get_children():
		c.queue_free()

	var s := Game.state
	var active := Quests.active_index(s)
	body.add_child(UIKit.label("%d / %d terminées" % [
			Quests.completed_count(s), Quests.LIST.size()],
			UIKit.FONT_SM, Palette.UI_TEXT_DIM))

	var list := UIKit.vbox(8)
	for i in range(Quests.LIST.size()):
		var q: Dictionary = Quests.LIST[i]
		var done := s.quests_claimed.has(String(q["id"]))
		# On n'affiche pas les 24 quêtes d'un coup : les terminées, l'active, et
		# trois d'avance pour donner envie.
		if not done and active >= 0 and i > active + 3:
			break
		list.add_child(_row(q, i, done, i == active))
	add_scrollable(list)


func _row(q: Dictionary, index: int, done: bool, is_active: bool) -> Control:
	var bg := Palette.UI_PANEL_LIGHT
	if is_active:
		bg = Palette.UI_PANEL_LIGHT.lerp(Palette.UI_ACCENT, 0.12)
	var card := UIKit.panel(bg, 14, 12)
	var row := UIKit.hbox(12)
	card.add_child(row)

	var mark := UIKit.label("✓" if done else str(index + 1), UIKit.FONT_MD,
			Palette.UI_OK if done else Palette.UI_TEXT_DIM)
	mark.custom_minimum_size.x = 48
	row.add_child(mark)

	var col := UIKit.vbox(3)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	col.add_child(UIKit.label(String(q["name"]), UIKit.FONT_MD,
			Palette.UI_TEXT_DIM if done else Palette.UI_TEXT))
	var desc := UIKit.label(String(q["desc"]), UIKit.FONT_SM, Palette.UI_TEXT_DIM)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(desc)

	if not done:
		var p := Quests.progress(Game.state, Game.tables, q)
		col.add_child(UIKit.label("%s / %s" % [
				Game.format_number(int(p["current"])),
				Game.format_number(int(p["target"]))],
				UIKit.FONT_SM, Palette.UI_OK if bool(p["done"]) else Palette.UI_TEXT_DIM))
	row.add_child(col)

	var reward := UIKit.vbox(2)
	if int(q["gold"]) > 0:
		reward.add_child(UIKit.label("%s or" % Game.format_number(int(q["gold"])),
				UIKit.FONT_SM, Palette.get_color("gold")))
	if int(q["essence"]) > 0:
		reward.add_child(UIKit.label("%d essence" % int(q["essence"]),
				UIKit.FONT_SM, Palette.get_color("essence")))
	row.add_child(reward)

	if is_active and Quests.can_claim(Game.state, Game.tables):
		card.add_child(UIKit.button("Récupérer", func() -> void:
			Game.claim_quest()
			emit_signal("claimed")
			_populate(), true, 76))
	return card
