class_name ResultPanel
extends Control
## Écran de fin de raid : étoiles qui tombent une par une, butin qui compte.
##
## C'est le moment de récompense "moyenne" de la boucle (docs/03 §4) : il doit
## être court (moins de 4 s avant que le bouton soit atteignable) et généreux
## en retour visuel.

signal continue_pressed

var _stars_labels: Array[Label] = []


func _init(result: Dictionary, summary: Dictionary, is_trial: bool) -> void:
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_STOP

	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.72)
	UIKit.fill_screen(dim)
	add_child(dim)

	var center := CenterContainer.new()
	UIKit.fill_screen(center)
	add_child(center)

	var card := UIKit.panel(Palette.UI_PANEL, 26, 30)
	card.custom_minimum_size = Vector2(880, 0)
	center.add_child(card)

	var col := UIKit.vbox(18)
	card.add_child(col)

	var stars := int(result["stars"])
	var headline := _headline(stars, is_trial, summary)
	var title := UIKit.label(headline, UIKit.FONT_XL,
			Palette.UI_ACCENT if stars > 0 else Palette.UI_TEXT_DIM)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(title)

	var stars_row := UIKit.hbox(16)
	stars_row.alignment = BoxContainer.ALIGNMENT_CENTER
	for i in range(3):
		var l := UIKit.label("☆", 96, Palette.UI_TEXT_DIM)
		_stars_labels.append(l)
		stars_row.add_child(l)
	col.add_child(stars_row)

	var pct := UIKit.label("%d %% détruit  ·  %d s  ·  %d/%d troupes perdues" % [
			int(result["percent"]), int(result["duration_sec"]),
			int(result["units_lost"]), int(result["units_total"])],
			UIKit.FONT_SM, Palette.UI_TEXT_DIM)
	pct.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(pct)

	col.add_child(UIKit.separator())

	if is_trial:
		var passed := bool(summary.get("trial_passed", false))
		var msg := "Épreuve réussie — le palier est ouvert." if passed \
				else "Épreuve échouée. Réessaie autant de fois que tu veux, c'est gratuit."
		var l := UIKit.label(msg, UIKit.FONT_MD, Palette.UI_OK if passed else Palette.UI_TEXT_DIM)
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		col.add_child(l)
	else:
		var loot: Dictionary = summary.get("loot", {})
		if loot.is_empty():
			col.add_child(_centered("Aucun butin rapporté.", Palette.UI_TEXT_DIM))
		else:
			col.add_child(_centered("Butin", Palette.UI_TEXT))
			var loot_row := UIKit.hbox(20)
			loot_row.alignment = BoxContainer.ALIGNMENT_CENTER
			for res: String in loot.keys():
				var item := UIKit.hbox(8)
				item.add_child(UIKit.icon_swatch(Palette.resource_color(res), 30))
				item.add_child(UIKit.label("+%s" % Game.format_number(int(loot[res])),
						UIKit.FONT_MD, Palette.UI_OK))
				loot_row.add_child(item)
			col.add_child(loot_row)
		if bool(summary.get("new_best", false)):
			col.add_child(_centered("Nouveau record sur ce village !", Palette.UI_ACCENT))

	if stars < 3 and not is_trial:
		col.add_child(_centered("3 étoiles = ×1,8 sur le butin. Ça vaut le coup de recommencer.",
				Palette.UI_TEXT_DIM))

	col.add_child(UIKit.button("Retour au village",
			func() -> void: emit_signal("continue_pressed"), true))

	_animate_stars(stars)


func _centered(text: String, color: Color) -> Label:
	var l := UIKit.label(text, UIKit.FONT_SM, color)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return l


func _headline(stars: int, is_trial: bool, summary: Dictionary) -> String:
	if is_trial:
		return "Épreuve réussie" if bool(summary.get("trial_passed", false)) else "Épreuve manquée"
	match stars:
		3: return "Village rasé"
		2: return "Victoire"
		1: return "Victoire courte"
	return "Assaut repoussé"


func _animate_stars(stars: int) -> void:
	## Une étoile à la fois, avec un petit rebond : c'est trois pics de plaisir
	## au lieu d'un seul.
	for i in range(stars):
		var l := _stars_labels[i]
		var delay := 0.18 + float(i) * 0.26
		var tw := create_tween()
		tw.tween_interval(delay)
		tw.tween_callback(func() -> void:
			l.text = "★"
			l.add_theme_color_override("font_color", Palette.UI_ACCENT)
			l.pivot_offset = l.size * 0.5)
		tw.tween_property(l, "scale", Vector2(1.45, 1.45), 0.09)
		tw.tween_property(l, "scale", Vector2(1.0, 1.0), 0.16).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
