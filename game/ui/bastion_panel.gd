class_name BastionPanel
extends Overlay
## Écran du Bastion : l'endroit où le joueur voit exactement ce qui le sépare
## du palier suivant.
##
## Aucun de ces verrous n'est un délai. S'il manque quelque chose, c'est une
## chose à FAIRE, et le bouton correspondant est juste à côté.

signal trial_requested(trial_index: int)
signal changed


func _init() -> void:
	super("Bastion")
	set_height_ratio(0.72)
	_populate()


func _populate() -> void:
	for c: Node in body.get_children():
		c.queue_free()

	var s := Game.state
	var current := s.bastion_level()
	set_title("Bastion  ·  niveau %d / %d" % [current, Progression.MAX_BASTION])

	body.add_child(UIKit.label(Progression.progress_summary(s), UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	body.add_child(UIKit.separator())

	if current >= Progression.MAX_BASTION:
		body.add_child(UIKit.label("Niveau maximum atteint. La vallée est à toi.",
				UIKit.FONT_MD, Palette.UI_ACCENT))
		return

	var target := current + 1
	body.add_child(UIKit.label("Passage au niveau %d" % target, UIKit.FONT_MD))
	body.add_child(UIKit.cost_line(Progression.bastion_cost(target),
			func(res: String, amount: int) -> bool:
				return int(Game.state.resources.get(res, 0)) >= amount))

	var blockers := Progression.blockers(s, Game.tables)
	if blockers.is_empty():
		body.add_child(UIKit.label("Tout est réuni.", UIKit.FONT_SM, Palette.UI_OK))
		body.add_child(UIKit.button("Améliorer le Bastion", func() -> void:
			if Game.upgrade_bastion():
				emit_signal("changed")
				_populate(), true))
	else:
		var list := UIKit.vbox(6)
		for b: String in blockers:
			var row := UIKit.hbox(8)
			row.add_child(UIKit.label("•", UIKit.FONT_SM, Palette.UI_DANGER))
			var l := UIKit.label(b, UIKit.FONT_SM, Palette.UI_TEXT_DIM)
			l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			row.add_child(l)
			list.add_child(row)
		body.add_child(list)

	# L'Épreuve : le verrou de compétence, jouable immédiatement et autant de
	# fois que voulu, sans coût.
	var trial := Campaign.pending_trial(s)
	if trial > 0:
		var info := Campaign.trial_info(trial)
		body.add_child(UIKit.separator())
		var card := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 16)
		var col := UIKit.vbox(8)
		card.add_child(col)
		col.add_child(UIKit.label("Épreuve %d — %s" % [trial, String(info["name"])],
				UIKit.FONT_MD, Palette.UI_ACCENT))
		col.add_child(UIKit.label(String(info["rule"]), UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		col.add_child(UIKit.label("Réussite : 2 étoiles minimum. Aucun coût, autant d'essais que nécessaire.",
				UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		col.add_child(UIKit.button("Tenter l'Épreuve", func() -> void:
			emit_signal("trial_requested", trial)
			close(), true))
		body.add_child(card)

	body.add_child(UIKit.separator())
	var unlocks := Progression.newly_unlocked(Game.tables, target)
	var parts: Array[String] = []
	for b: String in unlocks["buildings"]:
		parts.append(b)
	for u: String in unlocks["units"]:
		parts.append(u)
	if not parts.is_empty():
		body.add_child(UIKit.label("Le niveau %d débloque : %s" % [target, ", ".join(parts)],
				UIKit.FONT_SM, Palette.UI_OK))
