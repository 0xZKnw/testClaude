class_name CampaignPanel
extends Overlay
## Carte de campagne : 72 villages répartis en 8 chapitres.

signal level_chosen(index: int)


func _init() -> void:
	super("Campagne")
	set_height_ratio(0.86)
	_populate()


func _populate() -> void:
	var s := Game.state
	var list := UIKit.vbox(10)

	var head := UIKit.hbox(10)
	head.add_child(UIKit.label("%d / %d villages · ★ %d" % [
			s.levels_cleared(), Campaign.level_count(), s.total_stars()],
			UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	head.add_child(UIKit.spacer())
	head.add_child(UIKit.label("Armée %d/%d" % [Game.army_used(), Game.army_cap()],
			UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	body.add_child(head)
	body.add_child(UIKit.separator())

	var current_chapter := -1
	var first_locked_shown := false
	for i in range(Campaign.level_count()):
		var info := Campaign.level_info(i)
		var unlocked := Campaign.is_unlocked(s, i)
		var cleared := s.stars_for(i) > 0

		# On n'affiche pas les 72 niveaux d'un coup : tout ce qui est fait, plus
		# les prochains. Le reste apparaît au fur et à mesure.
		if not unlocked and not cleared:
			if first_locked_shown and i > s.levels_cleared() + 4:
				continue
			first_locked_shown = true

		if int(info["chapter"]) != current_chapter:
			current_chapter = int(info["chapter"])
			list.add_child(_chapter_header(info))
		list.add_child(_level_row(info, unlocked, s.stars_for(i)))
	add_scrollable(list)


func _chapter_header(info: Dictionary) -> Control:
	var box := UIKit.vbox(2)
	box.add_child(UIKit.label("Chapitre %d — %s" % [int(info["chapter"]) + 1,
			String(info["chapter_name"])], UIKit.FONT_MD, Palette.UI_ACCENT))
	box.add_child(UIKit.label("Bastion %d requis" % int(info["bastion_req"]),
			UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	return box


func _level_row(info: Dictionary, unlocked: bool, stars: int) -> Control:
	var idx := int(info["index"])
	var card := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 14)
	var row := UIKit.hbox(12)
	card.add_child(row)

	var num := UIKit.label(str(idx + 1), UIKit.FONT_MD,
			Palette.UI_TEXT if unlocked else Palette.UI_TEXT_DIM)
	num.custom_minimum_size.x = 64
	row.add_child(num)

	var info_col := UIKit.vbox(4)
	info_col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	info_col.add_child(UIKit.label(String(info["name"]), UIKit.FONT_MD,
			Palette.UI_TEXT if unlocked else Palette.UI_TEXT_DIM))
	if unlocked:
		var loot: Dictionary = info["loot"]
		var loot_row := UIKit.hbox(10)
		loot_row.add_child(UIKit.stars_row(stars))
		loot_row.add_child(UIKit.label("butin", UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		for res: String in ["wood", "stone", "iron", "gold"]:
			if int(loot.get(res, 0)) <= 0:
				continue
			var item := UIKit.hbox(4)
			item.add_child(UIKit.icon_swatch(Palette.resource_color(res), 18))
			item.add_child(UIKit.label(Game.format_number(int(loot[res])), UIKit.FONT_SM,
					Palette.UI_TEXT_DIM))
			loot_row.add_child(item)
		info_col.add_child(loot_row)
	else:
		var reason := "Bastion %d requis" % int(info["bastion_req"])
		if Game.state.bastion_level() >= int(info["bastion_req"]):
			reason = "Termine le village précédent"
		info_col.add_child(UIKit.label(reason, UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	row.add_child(info_col)

	var b := UIKit.button("Attaquer" if stars < 3 else "Rejouer",
			func() -> void:
				emit_signal("level_chosen", idx)
				close(),
			unlocked and stars < 3, 84)
	b.disabled = not unlocked
	b.custom_minimum_size.x = 190
	row.add_child(b)
	return card
