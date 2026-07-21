package exposed.thunder.thunderLBs.commands;

import exposed.thunder.thunderLBs.ThunderLBs;
import exposed.thunder.thunderLBs.animation.EasingType;
import exposed.thunder.thunderLBs.leaderboard.BarMode;
import exposed.thunder.thunderLBs.leaderboard.Leaderboard;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardAnimations;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardDefinition;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardManager;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardPage;
import exposed.thunder.thunderLBs.leaderboard.LeaderboardSettings;
import exposed.thunder.thunderLBs.leaderboard.ValidationIssue;
import exposed.thunder.thunderLBs.leaderboard.ValueFormat;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LeaderboardEditDialogs implements LeaderboardEditor {
    private static final String ADMIN_PERMISSION = "thunderlbs.admin";
    private static final int INPUT_WIDTH = 320;
    private static final int BUTTON_WIDTH = 150;
    private static final long MAX_TIMING_TICKS = 20L * 60L * 60L;
    private static final TextColor ACCENT = TextColor.color(0x38BDF8);
    private static final TextColor SUCCESS = TextColor.color(0x5EECA5);
    private static final TextColor DANGER = TextColor.color(0xFF6B81);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ThunderLBs plugin;
    private final LeaderboardManager manager;

    public LeaderboardEditDialogs(ThunderLBs plugin, LeaderboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void openSelector(Player player) {
        if (!canEdit(player)) {
            return;
        }
        List<Leaderboard> boards = manager.all().stream()
                .sorted(Comparator.comparing(board -> board.definition().id()))
                .toList();
        if (boards.isEmpty()) {
            sendPrefixed(player, "&cThere are no leaderboards to edit.");
            return;
        }

        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (int i = 0; i < boards.size(); i++) {
            LeaderboardDefinition definition = boards.get(i).definition();
            String display = definition.id() + " (" + definition.pages().size() + " holders)";
            options.add(option(definition.id(), display, i == 0));
        }

        Dialog dialog = createDialog(
                "Edit a leaderboard",
                List.of(DialogBody.plainMessage(Component.text(
                        "Choose a leaderboard to open its editor.", NamedTextColor.GRAY))),
                List.of(DialogInput.singleOption("leaderboard", Component.text("Leaderboard", ACCENT), options)
                        .width(INPUT_WIDTH)
                        .build()),
                DialogType.confirmation(
                        button("Open editor", "Open the editor for the selected leaderboard.", SUCCESS,
                                (response, editor) -> {
                                    String id = text(response, "leaderboard");
                                    if (id.isEmpty()) {
                                        error(editor, "Select a leaderboard first.");
                                        openSelector(editor);
                                        return;
                                    }
                                    openBoard(editor, id);
                                }),
                        closeButton()
                )
        );
        player.showDialog(dialog);
    }

    @Override
    public void openBoard(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(button("Layout",
                "Configure positions, viewing distance, row placement, and the viewer's relative rank line.", ACCENT,
                (response, editor) -> openLayout(editor, id)));
        actions.add(button("Appearance",
                "Customize board scale, the page bar, text shadow, holder colors, and facing direction.", ACCENT,
                (response, editor) -> openAppearance(editor, id)));
        actions.add(button(holderMenuLabel(definition.pages().size()),
                "Manage the holder pages displayed by this leaderboard. You can add, edit, remove, or reorder them.",
                (response, editor) -> openHolders(editor, id)));
        actions.add(button("Timing",
                "Adjust page duration, refresh frequency, row entrance delay, and title typing speed.", ACCENT,
                (response, editor) -> openTiming(editor, id)));
        actions.add(button("Animations",
                "Choose the easing styles used by title and row entrances and exits, plus page progress.", ACCENT,
                (response, editor) -> openAnimations(editor, id)));
        actions.add(button("Location",
                "Change the board's coordinates and rotation, or position it using your current location.", ACCENT,
                (response, editor) -> openLocation(editor, id)));
        actions.add(button("Reset Animations",
                "Restore every animation option on this board to the defaults from config.yml.", DANGER,
                (response, editor) -> resetAnimations(editor, id)));
        actions.add(button(
                definition.enabled() ? "Disable board" : "Enable board",
                definition.enabled()
                        ? "Disable this leaderboard and stop rendering it until it is enabled again."
                        : "Enable this leaderboard and begin rendering it for nearby players.",
                definition.enabled() ? DANGER : SUCCESS,
                (response, editor) -> toggleBoard(editor, id)
        ));

        Component summary = boardSummary(definition);

        player.showDialog(createDialog(
                "Editing " + definition.id(),
                List.of(DialogBody.plainMessage(summary)),
                List.of(),
                DialogType.multiAction(actions, closeButton(), 2)
        ));
    }

    private void openLayout(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        LeaderboardSettings settings = definition.settings();
        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("positions", Component.text("Visible positions", ACCENT), 1.0F, 100.0F)
                        .step(1.0F)
                        .initial((float) settings.positions())
                        .width(INPUT_WIDTH)
                        .build(),
                DialogInput.numberRange("view_distance", Component.text("View distance", ACCENT), 4.0F, 256.0F)
                        .step(1.0F)
                        .initial((float) definition.viewDistance())
                        .width(INPUT_WIDTH)
                        .build(),
                DialogInput.numberRange("row_y_offset", Component.text("Row y offset", ACCENT), -5.0F, 5.0F)
                        .step(0.05F)
                        .initial((float) settings.rowYOffset())
                        .width(INPUT_WIDTH)
                        .build(),
                DialogInput.bool("relative", Component.text("Show viewer rank line", ACCENT),
                        settings.showRelativePosition(), "true", "false")
        );

        player.showDialog(createForm(
                "Layout • " + id,
                "Configure how much of the leaderboard players can see.",
                inputs,
                (response, editor) -> {
                    LeaderboardDefinition current = definition(editor, id);
                    if (current == null) {
                        return;
                    }
                    current.settings().setPositions(Math.round(number(response, "positions", 1.0F)));
                    current.setViewDistance(Math.round(number(response, "view_distance", 4.0F)));
                    current.settings().setRowYOffset(number(response, "row_y_offset",
                            (float) current.settings().rowYOffset()));
                    current.settings().setShowRelativePosition(bool(response, "relative", false));
                    finishSave(editor, current, () -> openBoard(editor, id), () -> openLayout(editor, id));
                },
                (response, editor) -> openBoard(editor, id)
        ));
    }

    private void openAppearance(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("board_scale", Component.text("Board scale", ACCENT), 0.1F, 5.0F)
                        .step(0.1F)
                        .initial((float) definition.settings().boardScale())
                        .width(INPUT_WIDTH)
                        .build(),
                select("bar", "Page bar", enumOptions(BarMode.values(), definition.barMode().name())),
                DialogInput.bool("holder_color", Component.text("Use holder color for the bar", ACCENT),
                        definition.barUseHolderColor(), "true", "false"),
                DialogInput.bool("text_shadow", Component.text("Text shadow", ACCENT),
                        definition.textShadow(), "true", "false"),
                select("billboard", "Billboard", List.of(
                        option("fixed", "Fixed", definition.billboard() == Display.Billboard.FIXED),
                        option("vertical", "Vertical", definition.billboard() == Display.Billboard.VERTICAL)
                ))
        );

        player.showDialog(createForm(
                "Appearance • " + id,
                "Configure the board's visual presentation.",
                inputs,
                (response, editor) -> {
                    LeaderboardDefinition current = definition(editor, id);
                    if (current == null) {
                        return;
                    }
                    current.settings().setBoardScale(number(response, "board_scale",
                            (float) current.settings().boardScale()));
                    BarMode barMode = BarMode.fromString(text(response, "bar"), current.barMode());
                    current.setBarSettings(barMode, bool(response, "holder_color", current.barUseHolderColor()));
                    current.setTextShadow(bool(response, "text_shadow", current.textShadow()));
                    Display.Billboard billboard = text(response, "billboard").equalsIgnoreCase("vertical")
                            ? Display.Billboard.VERTICAL
                            : Display.Billboard.FIXED;
                    current.setBillboard(billboard);
                    finishSave(editor, current, () -> openBoard(editor, id), () -> openAppearance(editor, id));
                },
                (response, editor) -> openBoard(editor, id)
        ));
    }

    private void openTiming(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        LeaderboardSettings settings = definition.settings();
        List<DialogInput> inputs = List.of(
                textInput("page", "Page duration (ticks)", Long.toString(settings.pageDurationTicks()), 12),
                textInput("interval", "Refresh interval (ticks)", Long.toString(settings.intervalTicks()), 12),
                textInput("row_delay", "Row delay (ticks)", Long.toString(settings.rowDelayTicks()), 12),
                textInput("typing", "Typing interval (ticks)", Long.toString(settings.typingIntervalTicks()), 12)
        );

        player.showDialog(createForm(
                "Timing • " + id,
                "20 ticks equal one second. Row delay and typing interval may be zero.",
                inputs,
                (response, editor) -> {
                    Long page = parseTicks(editor, text(response, "page"), "Page duration", 1L);
                    Long interval = parseTicks(editor, text(response, "interval"), "Refresh interval", 1L);
                    Long rowDelay = parseTicks(editor, text(response, "row_delay"), "Row delay", 0L);
                    Long typing = parseTicks(editor, text(response, "typing"), "Typing interval", 0L);
                    if (page == null || interval == null || rowDelay == null || typing == null) {
                        openTiming(editor, id);
                        return;
                    }
                    LeaderboardDefinition current = definition(editor, id);
                    if (current == null) {
                        return;
                    }
                    current.settings().setPageDurationTicks(page);
                    current.settings().setIntervalTicks(interval);
                    current.settings().setRowDelayTicks(rowDelay);
                    current.settings().setTypingIntervalTicks(typing);
                    finishSave(editor, current, () -> openBoard(editor, id), () -> openTiming(editor, id));
                },
                (response, editor) -> openBoard(editor, id)
        ));
    }

    private void openAnimations(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        LeaderboardAnimations animations = definition.animations();
        List<DialogInput> inputs = List.of(
                select("title_in", "Title in animation", easingOptions(
                        animations.title().inEnabled(), animations.title().inCurve())),
                select("title_out", "Title out animation", easingOptions(
                        animations.title().outEnabled(), animations.title().outCurve())),
                select("rows_in", "Row in animation", easingOptions(
                        animations.row().inEnabled(), animations.row().inCurve())),
                select("rows_out", "Row out animation", easingOptions(
                        animations.row().outEnabled(), animations.row().outCurve())),
                select("progress", "Progress animation", easingOptions(
                        animations.bar().enabled(), animations.bar().curve()))
        );

        player.showDialog(createForm(
                "Animations • " + id,
                "Choose an easing curve or disable each animation.",
                inputs,
                (response, editor) -> {
                    LeaderboardDefinition current = definition(editor, id);
                    if (current == null) {
                        return;
                    }
                    applyAnimation(current.animations(), "title_in", text(response, "title_in"));
                    applyAnimation(current.animations(), "title_out", text(response, "title_out"));
                    applyAnimation(current.animations(), "rows_in", text(response, "rows_in"));
                    applyAnimation(current.animations(), "rows_out", text(response, "rows_out"));
                    applyAnimation(current.animations(), "progress", text(response, "progress"));
                    finishSave(editor, current, () -> openBoard(editor, id), () -> openAnimations(editor, id));
                },
                (response, editor) -> openBoard(editor, id)
        ));
    }

    private void openLocation(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        Location origin = definition.origin();
        List<DialogInput> inputs = List.of(
                textInput("x", "X", formatCoordinate(origin.getX()), 32),
                textInput("y", "Y", formatCoordinate(origin.getY()), 32),
                textInput("z", "Z", formatCoordinate(origin.getZ()), 32),
                textInput("yaw", "Yaw", Float.toString(origin.getYaw()), 32)
        );
        String worldName = origin.getWorld() == null ? "unloaded world" : origin.getWorld().getName();
        List<ActionButton> actions = List.of(
                button("Save coordinates", "Save the entered coordinates and rotation in the board's current world.", SUCCESS,
                        (response, editor) -> saveCoordinates(editor, id, response)),
                button("Use my location", "Move the board to your current position, world, and horizontal rotation.", ACCENT,
                        (response, editor) -> usePlayerLocation(editor, id)),
                button("Face me", "Rotate the board horizontally so that its front faces your current position.", ACCENT,
                        (response, editor) -> facePlayer(editor, id))
        );

        player.showDialog(createDialog(
                "Location • " + id,
                List.of(DialogBody.plainMessage(Component.text("World: " + worldName, NamedTextColor.GRAY))),
                inputs,
                DialogType.multiAction(actions, button("Back", "Return to the main editor without moving the board.", NamedTextColor.GRAY,
                        (response, editor) -> openBoard(editor, id)), 2)
        ));
    }

    private void resetAnimations(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        definition.setAnimations(LeaderboardAnimations.defaults(plugin.getPluginConfig().animation()));
        finishSave(player, definition, () -> openBoard(player, id), () -> openBoard(player, id));
    }

    private void saveCoordinates(Player player, String id, DialogResponseView response) {
        Double x = parseDouble(player, text(response, "x"), "X");
        Double y = parseDouble(player, text(response, "y"), "Y");
        Double z = parseDouble(player, text(response, "z"), "Z");
        Float yaw = parseFloat(player, text(response, "yaw"), "Yaw");
        if (x == null || y == null || z == null || yaw == null) {
            openLocation(player, id);
            return;
        }
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        World world = definition.origin().getWorld();
        if (world == null) {
            world = player.getWorld();
        }
        definition.setOrigin(new Location(world, x, y, z, yaw, 0.0F));
        finishSave(player, definition, () -> openBoard(player, id), () -> openLocation(player, id));
    }

    private void usePlayerLocation(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        Location location = player.getLocation().clone();
        location.setPitch(0.0F);
        definition.setOrigin(location);
        finishSave(player, definition, () -> openBoard(player, id), () -> openLocation(player, id));
    }

    private void facePlayer(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        Location origin = definition.origin();
        if (origin.getWorld() == null || !origin.getWorld().equals(player.getWorld())) {
            error(player, "You must be in the same world as the board.");
            openLocation(player, id);
            return;
        }
        Vector direction = player.getEyeLocation().toVector().subtract(origin.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            error(player, "Move away from the board before using Face me.");
            openLocation(player, id);
            return;
        }
        origin.setDirection(direction);
        origin.setPitch(0.0F);
        definition.setOrigin(origin);
        finishSave(player, definition, () -> openLocation(player, id), () -> openLocation(player, id));
    }

    private void toggleBoard(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        definition.setEnabled(!definition.enabled());
        finishSave(player, definition, () -> openBoard(player, id), () -> openBoard(player, id));
    }

    private void openHolders(Player player, String id) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < definition.pages().size(); i++) {
            int index = i;
            LeaderboardPage page = definition.pages().get(i);
            actions.add(button(
                    holderLabel(page),
                    holderTooltip(page),
                    (response, editor) -> openHolder(editor, id, index)
            ));
        }
        actions.add(button("+ Add holder", "Create a new holder and add it as another rotating leaderboard page.", SUCCESS,
                (response, editor) -> openHolder(editor, id, -1)));

        player.showDialog(createDialog(
                "Holders • " + id,
                List.of(DialogBody.plainMessage(Component.text(
                        "Each holder is displayed as its own rotating page.", NamedTextColor.GRAY))),
                List.of(),
                DialogType.multiAction(actions, button("Back", "Return to the main editor without changing the holder list.", NamedTextColor.GRAY,
                        (response, editor) -> openBoard(editor, id)), 2)
        ));
    }

    private void openHolder(Player player, String id, int index) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        boolean adding = index < 0;
        if (!adding && index >= definition.pages().size()) {
            error(player, "That holder no longer exists.");
            openHolders(player, id);
            return;
        }
        LeaderboardPage page = adding
                ? new LeaderboardPage("", "", "#FFFFFF", "*", ValueFormat.SHORT_NUMBER, "", "")
                : definition.pages().get(index);
        String providerName = plugin.getPluginConfig().providerName();
        boolean nativeProvider = plugin.getPlaceholderBridge().isNativeProvider(providerName);
        List<String> holderChoices = nativeProvider
                ? plugin.getPlaceholderBridge().holderChoices(providerName, definition.type())
                : List.of();
        List<String> intervalChoices = nativeProvider
                ? plugin.getPlaceholderBridge().intervalChoices(providerName, definition.type())
                : List.of();
        boolean intervalUnavailable = nativeProvider && intervalChoices.isEmpty();
        if (nativeProvider && adding && holderChoices.isEmpty()) {
            error(player, "No registered holders were reported by " + providerName + " for "
                    + definition.type().name().toLowerCase(Locale.ROOT) + " leaderboards.");
            openHolders(player, id);
            return;
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(nativeProvider
                ? select("holder", "Holder ID", choiceOptions(holderChoices, page.holderId(), false))
                : textInput("holder", "Holder ID", page.holderId(), 128));
        inputs.add(textInput("title", "Title", page.title(), 256));
        inputs.add(textInput("color", "Color", page.color(), 64));
        inputs.add(textInput("icon", "Icon", page.icon(), 64));
        inputs.add(select("format", "Value format", enumOptions(ValueFormat.values(), page.valueFormat().name())));
        inputs.add(textInput("prefix", "Value prefix (optional)", page.prefix(), 128));
        inputs.add(textInput("suffix", "Value suffix (optional)", page.suffix(), 128));
        if (nativeProvider) {
            if (intervalUnavailable) {
                inputs.add(unavailableIntervalInput(
                        page.interval(),
                        plugin.getServer().getPluginManager().isPluginEnabled("Topper")
                                ? "Use TimedTopper to set intervals!"
                                : "No interval provider usable"));
            } else {
                inputs.add(select(
                        "interval", "Interval", choiceOptions(intervalChoices, page.interval(), true)));
            }
        } else {
            inputs.add(textInput(
                    "interval", "Interval (e.g. alltime, daily, weekly)", page.interval(), 32));
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(button(adding ? "Add holder" : "Save holder",
                adding
                        ? "Validate these details and add the holder as a new leaderboard page."
                        : "Validate these details and save the changes to this holder page.", SUCCESS,
                (response, editor) -> saveHolder(editor, id, index, response)));
        if (!adding) {
            if (index > 0) {
                actions.add(button("Move earlier", "Move this holder one position earlier in the page rotation order.", ACCENT,
                        (response, editor) -> moveHolder(editor, id, index, -1, page.holderId())));
            }
            if (index + 1 < definition.pages().size()) {
                actions.add(button("Move later", "Move this holder one position later in the page rotation order.", ACCENT,
                        (response, editor) -> moveHolder(editor, id, index, 1, page.holderId())));
            }
            actions.add(button("Remove", "Open a confirmation screen before permanently removing this holder page.", DANGER,
                    (response, editor) -> confirmRemoveHolder(editor, id, index)));
        }

        player.showDialog(createDialog(
                (adding ? "Add holder" : "Edit " + page.title()) + " • " + id,
                List.of(),
                inputs,
                DialogType.multiAction(actions, button("Back", "Return to the holder list without saving the fields on this screen.", NamedTextColor.GRAY,
                        (response, editor) -> openHolders(editor, id)), 2)
        ));
    }

    private void saveHolder(Player player, String id, int index, DialogResponseView response) {
        String holder = text(response, "holder");
        String title = text(response, "title");
        if (holder.isBlank() || title.isBlank()) {
            error(player, "Holder ID and title cannot be empty.");
            openHolder(player, id, index);
            return;
        }
        ValueFormat format;
        try {
            format = ValueFormat.valueOf(text(response, "format").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            error(player, "Choose a valid value format.");
            openHolder(player, id, index);
            return;
        }
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        boolean adding = index < 0;
        if (!adding && index >= definition.pages().size()) {
            error(player, "That holder changed while the editor was open.");
            openHolders(player, id);
            return;
        }
        String interval = text(response, "interval");
        String providerName = plugin.getPluginConfig().providerName();
        if (interval.isBlank()
                && plugin.getPlaceholderBridge().isNativeProvider(providerName)
                && plugin.getPlaceholderBridge().intervalChoices(providerName, definition.type()).isEmpty()) {
            interval = adding
                    ? LeaderboardPage.DEFAULT_INTERVAL
                    : definition.pages().get(index).interval();
        }
        LeaderboardPage replacement = new LeaderboardPage(
                holder,
                title,
                text(response, "color"),
                text(response, "icon"),
                format,
                text(response, "prefix"),
                text(response, "suffix"),
                interval
        );
        if (adding) {
            definition.pages().add(replacement);
        } else {
            definition.pages().set(index, replacement);
        }
        finishSave(player, definition, () -> openHolders(player, id), () -> openHolder(player, id, index));
    }

    private void moveHolder(Player player, String id, int index, int offset, String expectedHolder) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        int destination = index + offset;
        if (index < 0 || index >= definition.pages().size()
                || destination < 0 || destination >= definition.pages().size()
                || !definition.pages().get(index).holderId().equals(expectedHolder)) {
            error(player, "The holder list changed while the editor was open.");
            openHolders(player, id);
            return;
        }
        LeaderboardPage moved = definition.pages().remove(index);
        definition.pages().add(destination, moved);
        finishSave(player, definition, () -> openHolders(player, id), () -> openHolders(player, id));
    }

    private void confirmRemoveHolder(Player player, String id, int index) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        if (index < 0 || index >= definition.pages().size()) {
            openHolders(player, id);
            return;
        }
        LeaderboardPage page = definition.pages().get(index);
        player.showDialog(createDialog(
                "Remove " + page.holderId() + "?",
                List.of(DialogBody.plainMessage(Component.text(
                        "This removes the holder page from " + id + ".", NamedTextColor.GRAY))),
                List.of(),
                DialogType.confirmation(
                        button("Remove", "Permanently remove this holder page from the leaderboard.", DANGER,
                                (response, editor) -> removeHolder(editor, id, index, page.holderId())),
                        button("Cancel", "Keep this holder page and return to its editor.", NamedTextColor.GRAY,
                                (response, editor) -> openHolder(editor, id, index))
                )
        ));
    }

    private void removeHolder(Player player, String id, int index, String expectedHolder) {
        LeaderboardDefinition definition = definition(player, id);
        if (definition == null) {
            return;
        }
        if (definition.pages().size() <= 1) {
            error(player, "A leaderboard must keep at least one holder.");
            openHolders(player, id);
            return;
        }
        if (index < 0 || index >= definition.pages().size()
                || !definition.pages().get(index).holderId().equals(expectedHolder)) {
            error(player, "The holder list changed while the confirmation was open.");
            openHolders(player, id);
            return;
        }
        definition.pages().remove(index);
        finishSave(player, definition, () -> openHolders(player, id), () -> openHolders(player, id));
    }

    private Dialog createForm(String title,
                              String description,
                              List<? extends DialogInput> inputs,
                              DialogHandler save,
                              DialogHandler back) {
        return createDialog(
                title,
                List.of(DialogBody.plainMessage(Component.text(description, NamedTextColor.GRAY))),
                inputs,
                DialogType.confirmation(
                        button("Save", "Validate the fields on this screen and apply the changes to the leaderboard.", SUCCESS, save),
                        button("Back", "Discard the fields on this screen and return to the previous menu.", NamedTextColor.GRAY, back)
                )
        );
    }

    private Dialog createDialog(String title,
                                List<? extends DialogBody> body,
                                List<? extends DialogInput> inputs,
                                DialogType type) {
        DialogBase base = DialogBase.builder(Component.text(title, ACCENT))
                .externalTitle(Component.text(title, ACCENT))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
        return Dialog.create(builder -> builder.empty().base(base).type(type));
    }

    private ActionButton button(String label, String tooltip, TextColor color, DialogHandler handler) {
        return button(Component.text(label, color).decoration(TextDecoration.BOLD, false), tooltip, handler);
    }

    private ActionButton button(Component label, String tooltip, DialogHandler handler) {
        return button(label, Component.text(tooltip, NamedTextColor.GRAY), handler);
    }

    private ActionButton button(Component label, Component tooltip, DialogHandler handler) {
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (response, audience) -> {
                            if (!(audience instanceof Player player)) {
                                audience.closeDialog();
                                return;
                            }
                            if (!canEdit(player)) {
                                player.closeDialog();
                                return;
                            }
                            handler.handle(response, player);
                        },
                        ClickCallback.Options.builder()
                                .uses(1)
                                .lifetime(Duration.ofMinutes(10))
                                .build()
                ))
                .build();
    }

    private Component holderMenuLabel(int count) {
        return Component.text()
                .append(Component.text("Holders ", ACCENT))
                .append(Component.text("(" + count + ")", NamedTextColor.GRAY))
                .decoration(TextDecoration.BOLD, false)
                .build();
    }

    private Component holderTooltip(LeaderboardPage page) {
        return Component.text()
                .append(Component.text("Holder ID: ", ACCENT))
                .append(Component.text(page.holderId(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Display color: ", ACCENT))
                .append(Component.text(page.color(), holderColor(page.color())))
                .append(Component.newline())
                .append(Component.text("Value format: ", ACCENT))
                .append(Component.text(friendly(page.valueFormat().name()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Click to edit this holder page.", NamedTextColor.GRAY))
                .build();
    }

    private Component boardSummary(LeaderboardDefinition definition) {
        var summary = Component.text()
                .append(Component.text(
                        definition.enabled() ? "Enabled" : "Disabled",
                        definition.enabled() ? SUCCESS : DANGER
                ))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Holders:", ACCENT));

        for (LeaderboardPage page : definition.pages()) {
            summary.append(Component.newline())
                    .append(holderLabel(page));
        }

        return summary
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Click a category below to edit.", NamedTextColor.GRAY))
                .build();
    }

    private Component holderLabel(LeaderboardPage page) {
        return Component.text(page.icon() + " " + page.title(), holderColor(page.color()))
                .decoration(TextDecoration.BOLD, false);
    }

    private TextColor holderColor(String value) {
        if (value != null) {
            TextColor hex = TextColor.fromHexString(value.trim());
            if (hex != null) {
                return hex;
            }
            NamedTextColor named = NamedTextColor.NAMES.value(value.trim().toLowerCase(Locale.ROOT));
            if (named != null) {
                return named;
            }
        }
        return ACCENT;
    }

    private ActionButton closeButton() {
        return button("Close", "Close the leaderboard editor without opening another menu.", NamedTextColor.GRAY,
                (response, player) -> player.closeDialog());
    }

    private DialogInput select(String key, String label, List<SingleOptionDialogInput.OptionEntry> options) {
        return DialogInput.singleOption(key, Component.text(label, ACCENT), options)
                .width(INPUT_WIDTH)
                .build();
    }

    private DialogInput textInput(String key, String label, String initial, int maxLength) {
        return DialogInput.text(key, Component.text(label, ACCENT))
                .width(INPUT_WIDTH)
                .initial(initial == null ? "" : initial)
                .maxLength(maxLength)
                .build();
    }

    private DialogInput unavailableIntervalInput(String current, String message) {
        String interval = current == null || current.isBlank() ? LeaderboardPage.DEFAULT_INTERVAL : current;
        return DialogInput.singleOption(
                        "interval",
                        Component.text("Interval", ACCENT),
                        List.of(SingleOptionDialogInput.OptionEntry.create(
                                interval,
                                Component.text(message, NamedTextColor.GRAY),
                                true)))
                .width(INPUT_WIDTH)
                .build();
    }

    private List<SingleOptionDialogInput.OptionEntry> enumOptions(Enum<?>[] values, String selected) {
        return Arrays.stream(values)
                .map(value -> option(value.name().toLowerCase(Locale.ROOT), friendly(value.name()),
                        value.name().equalsIgnoreCase(selected)))
                .toList();
    }

    private List<SingleOptionDialogInput.OptionEntry> choiceOptions(
            List<String> choices,
            String selected,
            boolean friendlyLabels) {
        List<String> values = new ArrayList<>(choices);
        if (selected != null && !selected.isBlank()
                && values.stream().noneMatch(value -> value.equalsIgnoreCase(selected))) {
            values.add(0, selected);
        }
        if (values.isEmpty()) {
            values.add("alltime");
        }
        String initial = values.stream()
                .filter(value -> selected != null && value.equalsIgnoreCase(selected))
                .findFirst()
                .orElse(values.get(0));
        return values.stream()
                .map(value -> option(value, friendlyLabels ? friendly(value) : value,
                        value.equalsIgnoreCase(initial)))
                .toList();
    }

    private List<SingleOptionDialogInput.OptionEntry> easingOptions(boolean enabled, EasingType selected) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        options.add(option("none", "None", !enabled));
        for (EasingType easing : EasingType.values()) {
            options.add(option(easing.name().toLowerCase(Locale.ROOT), friendly(easing.name()),
                    enabled && easing == selected));
        }
        return options;
    }

    private SingleOptionDialogInput.OptionEntry option(String id, String display, boolean selected) {
        return SingleOptionDialogInput.OptionEntry.create(id, Component.text(display), selected);
    }

    private void applyAnimation(LeaderboardAnimations animations, String part, String value) {
        boolean enabled = !value.equalsIgnoreCase("none");
        EasingType easing = enabled ? EasingType.fromFriendly(value, EasingType.LINEAR) : EasingType.LINEAR;
        switch (part) {
            case "title_in" -> {
                animations.title().setInEnabled(enabled);
                if (enabled) {
                    animations.title().setInCurve(easing);
                }
            }
            case "title_out" -> {
                animations.title().setOutEnabled(enabled);
                if (enabled) {
                    animations.title().setOutCurve(easing);
                }
            }
            case "rows_in" -> {
                animations.row().setInEnabled(enabled);
                if (enabled) {
                    animations.row().setInCurve(easing);
                }
            }
            case "rows_out" -> {
                animations.row().setOutEnabled(enabled);
                if (enabled) {
                    animations.row().setOutCurve(easing);
                }
            }
            case "progress" -> {
                animations.bar().setEnabled(enabled);
                if (enabled) {
                    animations.bar().setCurve(easing);
                }
            }
            default -> throw new IllegalArgumentException("Unknown animation part: " + part);
        }
    }

    private LeaderboardDefinition definition(Player player, String id) {
        if (!canEdit(player)) {
            return null;
        }
        Leaderboard leaderboard = manager.get(id);
        if (leaderboard == null) {
            send(player, plugin.getPluginConfig().prefixedMessage("missing", Map.of("id", id)));
            player.closeDialog();
            return null;
        }
        return leaderboard.definition();
    }

    private boolean canEdit(Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        error(player, "You no longer have permission to edit leaderboards.");
        return false;
    }

    private void finishSave(Player player,
                            LeaderboardDefinition definition,
                            Runnable onSuccess,
                            Runnable onFailure) {
        LeaderboardManager.OperationResult result = manager.saveAndReload(definition);
        if (!result.success()) {
            error(player, result.message());
            for (ValidationIssue issue : result.issues()) {
                send(player, "&c" + issue.severity().name() + " &f" + issue.path() + "&7: " + issue.message());
            }
            onFailure.run();
            return;
        }
        for (ValidationIssue issue : result.issues()) {
            if (issue.severity() == ValidationIssue.Severity.WARNING) {
                send(player, "&eWARNING &f" + issue.path() + "&7: " + issue.message());
            }
        }
        send(player, plugin.getPluginConfig().prefixedMessage("saved", Map.of("id", definition.id())));
        onSuccess.run();
    }

    private Long parseTicks(Player player, String raw, String label, long minimum) {
        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            error(player, label + " must be a whole number.");
            return null;
        }
        if (value < minimum || value > MAX_TIMING_TICKS) {
            error(player, label + " must be between " + minimum + " and " + MAX_TIMING_TICKS + " ticks.");
            return null;
        }
        return value;
    }

    private Double parseDouble(Player player, String raw, String label) {
        try {
            double value = Double.parseDouble(raw);
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        error(player, label + " must be a finite number.");
        return null;
    }

    private Float parseFloat(Player player, String raw, String label) {
        try {
            float value = Float.parseFloat(raw);
            if (Float.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        error(player, label + " must be a finite number.");
        return null;
    }

    private static String text(DialogResponseView response, String key) {
        String value = response.getText(key);
        return value == null ? "" : value.trim();
    }

    private static float number(DialogResponseView response, String key, float fallback) {
        Float value = response.getFloat(key);
        return value == null || !Float.isFinite(value) ? fallback : value;
    }

    private static boolean bool(DialogResponseView response, String key, boolean fallback) {
        Boolean value = response.getBoolean(key);
        return value == null ? fallback : value;
    }

    private static String friendly(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (normalized.equals("alltime")) {
            return "All time";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void error(Player player, String message) {
        player.sendActionBar(Component.text(message, DANGER));
        sendPrefixed(player, "&c" + message);
    }

    private void sendPrefixed(Player player, String message) {
        send(player, plugin.getPluginConfig().messages().prefix() + message);
    }

    private void send(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(LEGACY.deserialize(message));
        }
    }

    @FunctionalInterface
    private interface DialogHandler {
        void handle(DialogResponseView response, Player player);
    }
}
