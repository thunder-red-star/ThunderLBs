package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.ThunderLBs;
import exposed.thunder.thunderLBs.animation.AnimationCache;
import exposed.thunder.thunderLBs.animation.EasingType;
import exposed.thunder.thunderLBs.config.PluginConfig;
import exposed.thunder.thunderLBs.placeholder.PlaceholderBridge;
import exposed.thunder.thunderLBs.placeholder.PlaceholderBridge.ProviderValue;
import exposed.thunder.thunderLBs.render.BoardDisplay;
import exposed.thunder.thunderLBs.render.DisplayOptions;
import exposed.thunder.thunderLBs.render.RenderBackend;
import exposed.thunder.thunderLBs.scheduler.RegionTaskScheduler;
import exposed.thunder.thunderLBs.scheduler.TaskHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PageSession {
    private static final int OPACITY_TRANSPARENT = 0;
    private static final int OPACITY_OPAQUE = 255;
    private static final int TITLE_INTERPOLATION = 1;
    private static final int ROW_INTERPOLATION = 1;
    private static final int BAR_INITIAL_INTERPOLATION = 1;
    private static final int BAR_FADE_TICKS = 10;
    private static final double BAR_DEPTH_OFFSET = 0.01D;

    private static final Pattern HEX_COLOR_TAG = Pattern.compile("<#([A-Fa-f0-9]{3,6})>");
    private static final Pattern COLOR_TAG = Pattern.compile("<color:([^>]+)>");

    private final ThunderLBs plugin;
    private final PluginConfig config;
    private final PlaceholderBridge placeholderBridge;
    private final AnimationCache animationCache;
    private final Leaderboard leaderboard;
    private final LeaderboardDefinition definition;
    private final LeaderboardPage page;
    private final RenderBackend backend;
    private final Location origin;
    private final World world;
    private final BarMode barMode;
    private final boolean barUseHolderColor;
    private final boolean debugPlaceholders;

    private final List<BoardDisplay> displays;
    private final List<TaskHandle> tasks;
    private final List<FrameAnimation> frameAnimations;
    private final RegionTaskScheduler scheduler;
    private TaskHandle frameAnimationTask;
    private boolean active;
    private boolean completed;
    private int liveDisplays;
    private final Display.Billboard billboard;
    private final int pageIndex;
    private final int totalPages;
    private final float viewRange;
    private final float boardScale;

    PageSession(ThunderLBs plugin,
            PluginConfig config,
            PlaceholderBridge placeholderBridge,
            AnimationCache animationCache,
            Leaderboard leaderboard,
            LeaderboardDefinition definition,
            LeaderboardPage page,
            int pageIndex) {
        this.plugin = plugin;
        this.config = config;
        this.placeholderBridge = placeholderBridge;
        this.animationCache = animationCache;
        this.leaderboard = leaderboard;
        this.definition = definition;
        this.page = page;
        this.backend = plugin.renderBackend();
        this.origin = leaderboard.origin();
        this.world = leaderboard.world();
        this.displays = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.frameAnimations = new ArrayList<>();
        this.scheduler = RegionTaskScheduler.create(plugin);
        this.completed = false;
        this.liveDisplays = 0;
        this.billboard = definition.billboard();
        this.totalPages = Math.max(1, definition.pages().size());
        this.pageIndex = Math.max(0, Math.min(this.totalPages - 1, pageIndex));
        this.barMode = definition.barMode();
        this.barUseHolderColor = definition.barUseHolderColor();
        this.debugPlaceholders = plugin.hasPlaceholderDebugListeners();
        this.viewRange = Math.max(0.1F, definition.viewDistance() / 64.0F);
        this.boardScale = (float) definition.settings().boardScale();
    }

    public void start() {
        World world = this.world;
        if (world == null) {
            plugin.getLogger().warning("World for leaderboard '" + definition.id() + "' is not loaded. Page skipped.");
            return;
        }
        if (!leaderboard.isRenderable()) {
            checkCompletion();
            return;
        }
        this.active = true;
        this.completed = false;
        this.liveDisplays = 0;
        List<RowEntry> entries = prepareEntries();
        spawnTitle(world);
        spawnBar(world);
        spawnRows(world, entries);
    }

    public void stop() {
        if (completed) {
            return;
        }
        this.active = false;
        for (TaskHandle task : tasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
        frameAnimations.clear();
        frameAnimationTask = null;
        while (!displays.isEmpty()) {
            despawnDisplay(displays.get(displays.size() - 1));
        }
        checkCompletion();
    }

    boolean hasLiveDisplays() {
        return active && liveDisplays > 0;
    }

    private DisplayOptions baseOptions() {
        return new DisplayOptions()
                .billboard(billboard)
                .viewRange(viewRange);
    }

    private BoardDisplay spawnDisplay(Location location, DisplayOptions options) {
        BoardDisplay display = backend.spawn(leaderboard, location, options);
        leaderboard.track(display);
        displays.add(display);
        liveDisplays++;
        return display;
    }

    private List<RowEntry> prepareEntries() {
        int positions = Math.max(1, definition.settings().positions());
        List<String> circled = config.formatting().circledNumbers();
        List<RowEntry> rows = new ArrayList<>(positions);
        PluginConfig.Provider provider = config.provider();
        boolean nativeProvider = placeholderBridge.supportsProvider(config.providerName(), definition.type());
        String namePattern;
        String valuePattern;
        if (definition.type() == LeaderboardType.GROUP) {
            namePattern = provider.groupName();
            valuePattern = provider.groupValue();
            if (!nativeProvider && !provider.supportsGroups()) {
                plugin.getLogger().warning("Provider '" + config.providerName()
                        + "' has no group placeholders configured; leaderboard '" + definition.id()
                        + "' will not resolve.");
            }
        } else {
            namePattern = provider.name();
            valuePattern = provider.value();
        }
        String holderId = page.holderId();
        String interval = page.interval();
        namePattern = PluginConfig.Provider.applyInterval(namePattern, interval).replace("%holder%", holderId);
        valuePattern = PluginConfig.Provider.applyInterval(valuePattern, interval).replace("%holder%", holderId);
        for (int index = 1; index <= positions; index++) {
            String position = Integer.toString(index);
            String namePlaceholder = namePattern.replace("%position%", position);
            String valuePlaceholder = valuePattern.replace("%position%", position);
            String nameSource = nativeProvider
                    ? nativeSource("top_name", position)
                    : namePlaceholder;
            String valueSource = nativeProvider
                    ? nativeSource("top_value_raw", position)
                    : valuePlaceholder;

            String resolvedName = placeholderBridge.resolveProvider(
                    config.providerName(),
                    definition.type(),
                    holderId,
                    interval,
                    index,
                    ProviderValue.TOP_NAME,
                    null,
                    namePlaceholder);
            logPlaceholder("name", index, nameSource, resolvedName);
            String playerName = sanitize(resolvedName, nameSource);

            String resolvedValue = placeholderBridge.resolveProvider(
                    config.providerName(),
                    definition.type(),
                    holderId,
                    interval,
                    index,
                    ProviderValue.TOP_VALUE,
                    null,
                    valuePlaceholder);
            logPlaceholder("value", index, valueSource, resolvedValue);
            String rawValue = sanitize(resolvedValue, valueSource);
            String formattedValue = page.valueFormat().format(rawValue);
            if (page.hasPrefix()) {
                formattedValue = page.prefix() + formattedValue;
            }
            if (page.hasSuffix()) {
                formattedValue = formattedValue + page.suffix();
            }
            String circledNumber = index <= circled.size() ? circled.get(index - 1) : String.valueOf(index);
            double offsetY = scaledRowOffset(
                    definition.settings().rowYOffset(), config.defaults().rowSpacing(), index, boardScale);

            String rowColor = config.formatting().rankColor(index, page.color());
            String renderedRow = renderRow(rowColor, circledNumber, playerName, formattedValue);
            rows.add(new RowEntry(offsetY, plugin.deserializeMiniMessage(renderedRow)));
        }
        return rows;
    }

    private String nativeSource(String query, String position) {
        return config.providerName() + ":" + page.holderId() + ";" + query + ";" + position;
    }

    private String sanitize(String value, String placeholder) {
        boolean invalid = value == null || value.isBlank() || value.equalsIgnoreCase(placeholder)
                || value.contains("%");
        return invalid ? config.missingText() : value;
    }

    private void logPlaceholder(String type, int index, String placeholder, String resolved) {
        if (debugPlaceholders) {
            plugin.emitPlaceholderDebug(definition, page, index, type, placeholder, resolved);
        }
    }

    // ---------------------------------------------------------------- title

    private void spawnTitle(World world) {
        Location base = origin.clone();
        if (barMode == BarMode.NONE) {
            base.add(0.0D, config.defaults().barOffsetY() * boardScale, 0.0D);
        }
        BoardDisplay display = spawnDisplay(base, baseOptions()
                .shadowed(definition.textShadow())
                .opacity(packOpacity(OPACITY_TRANSPARENT))
                .scale(0.0F, 0.0F, 0.0F)
                .interpolationDuration(TITLE_INTERPOLATION)
                .teleportDuration(TITLE_INTERPOLATION));

        scheduleTitleTyping(display);
        scheduleTitleAnimations(display);
    }

    private void scheduleTitleTyping(BoardDisplay display) {
        String fullTitle = page.title();
        long interval = Math.max(1L, definition.settings().typingIntervalTicks());
        PluginConfig.Sounds.Typing typing = config.sounds().typing();
        Location base = origin;
        List<Component> characters = toPartialComponents(fullTitle);
        TaskHandle task = scheduler.runAtFixedRate(origin, new java.util.function.Consumer<>() {
            int index = 0;

            @Override
            public void accept(TaskHandle task) {
                if (!ensureSessionActive() || display.isRemoved()) {
                    task.cancel();
                    return;
                }
                if (index >= characters.size()) {
                    task.cancel();
                    if (!usesClientInterpolatedTitleEntrance()) {
                        display.opacity(packOpacity(OPACITY_OPAQUE));
                    }
                    return;
                }
                display.text(characters.get(index));
                if (typing.enabled()) {
                    playTypingSound(base, typing);
                }
                index++;
            }
        }, interval, interval);
        tasks.add(task);
    }

    private List<Component> toPartialComponents(String title) {
        if (title == null || title.isEmpty()) {
            return List.of(plugin.deserializeMiniMessage(renderTitle("")));
        }
        List<Component> parts = new ArrayList<>(title.length());
        for (int i = 1; i <= title.length(); i++) {
            parts.add(plugin.deserializeMiniMessage(renderTitle(title.substring(0, i))));
        }
        return parts;
    }

    private String renderTitle(String title) {
        return definition.formatting().renderTitle(
                page.color(), page.icon(), Objects.requireNonNullElse(title, ""));
    }

    private void playTypingSound(Location base, PluginConfig.Sounds.Typing typing) {
        if (world == null) {
            return;
        }
        float pitch = typing.pitchMin()
                + ThreadLocalRandom.current().nextFloat() * (typing.pitchMax() - typing.pitchMin());
        world.playSound(
                base,
                typing.key(),
                SoundCategory.AMBIENT,
                typingBroadcastVolume(typing.volume(), typing.radius()),
                pitch);
    }

    static float typingBroadcastVolume(float configuredVolume, double radius) {
        float volume = Math.max(0.0F, configuredVolume);
        double naturalRadius = 16.0D * Math.max(1.0D, volume);
        if (radius <= naturalRadius) {
            return volume;
        }
        return (float) (radius / 16.0D);
    }

    private void scheduleTitleAnimations(BoardDisplay display) {
        float[] frames = animationCache.titleInScale();
        LeaderboardAnimations.Title animation = definition.animations().title();
        if (usesClientInterpolation(animation.inEnabled(), animation.inCurve(), frames.length)) {
            float targetScale = frames[frames.length - 1];
            scheduleLinearTransition(
                    display,
                    1L,
                    frames.length,
                    () -> applyScale(display, targetScale, packOpacity(OPACITY_OPAQUE)),
                    () -> scheduleTitleFade(display)
            );
            return;
        }
        scheduleFrameAnimation(
                display,
                1L,
                frames,
                (scale, frame, totalFrames) -> {
                    display.interpolation(0, TITLE_INTERPOLATION);
                    applyScale(display, scale, opacityAtFrame(frame, totalFrames, true));
                },
                () -> scheduleTitleFade(display)
        );
    }

    private void scheduleTitleFade(BoardDisplay display) {
        long delay = Math.max(1L, definition.settings().pageDurationTicks());
        float[] frames = animationCache.titleOutScale();
        LeaderboardAnimations.Title animation = definition.animations().title();
        if (usesClientInterpolation(animation.outEnabled(), animation.outCurve(), frames.length)) {
            float targetScale = frames[frames.length - 1];
            scheduleLinearTransition(
                    display,
                    delay,
                    frames.length,
                    () -> applyScale(display, targetScale, packOpacity(OPACITY_TRANSPARENT)),
                    () -> despawnDisplay(display)
            );
            return;
        }
        scheduleFrameAnimation(
                display,
                delay,
                frames,
                (scale, frame, totalFrames) -> {
                    display.interpolation(0, TITLE_INTERPOLATION);
                    applyScale(display, scale, opacityAtFrame(frame, totalFrames, false));
                },
                () -> despawnDisplay(display)
        );
    }

    private void applyScale(BoardDisplay display, float scale, byte opacity) {
        float scaled = scale * boardScale;
        display.transformAndOpacity(0.0F, 0.0F, 0.0F, scaled, scaled, scaled, opacity);
    }

    // ------------------------------------------------------------------ bar

    private void spawnBar(World world) {
        switch (barMode) {
            case NONE -> {
            }
            case DOTS -> spawnDots(world);
            case BAR -> spawnFillBar(world);
        }
    }

    private void spawnDots(World world) {
        Location anchor = origin.clone().add(0.0D, config.defaults().barOffsetY() * boardScale, 0.0D);
        spawnDisplay(anchor, baseOptions()
                .shadowed(false)
                .opacity(packOpacity(OPACITY_OPAQUE))
                .text(buildDotsComponent())
                .interpolationDuration(TITLE_INTERPOLATION)
                .teleportDuration(TITLE_INTERPOLATION)
                .scale(boardScale, boardScale, boardScale));
    }

    private Component buildDotsComponent() {
        PluginConfig.Bar bar = config.bar();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < totalPages; index++) {
            String color;
            if (index <= pageIndex) {
                color = barUseHolderColor ? definition.pages().get(index).color() : bar.activeColor();
            } else {
                color = bar.pendingColor();
            }
            builder.append("<color:").append(color).append(">")
                    .append(bar.symbol())
                    .append("</color>");
            if (index < totalPages - 1) {
                builder.append(bar.separator());
            }
        }
        return plugin.deserializeMiniMessage(builder.toString());
    }

    private void spawnFillBar(World world) {
        Location anchor = origin.clone().add(0.0D, config.defaults().barOffsetY() * boardScale, 0.0D);
        String foregroundColor = barUseHolderColor ? page.color() : null;
        boolean clientInterpolated = usesClientInterpolatedBar();
        BarPair pair = spawnBarPair(world, anchor, foregroundColor, clientInterpolated);
        animateFillBar(pair);
    }

    private BarPair spawnBarPair(World world,
            Location containerLocation,
            String foregroundColor,
            boolean valueInitiallyOpaque) {
        Location valueLocation = computeValueLocation(containerLocation);
        String backgroundTemplate = definition.formatting().barBackground();
        String foregroundTemplate = definition.formatting().barForeground();

        BoardDisplay container = spawnDisplay(containerLocation, baseOptions()
                .shadowed(false)
                .text(deserializeBarComponent(backgroundTemplate, null))
                .opacity(packOpacity(OPACITY_TRANSPARENT))
                .interpolationDuration(BAR_INITIAL_INTERPOLATION)
                .teleportDuration(BAR_INITIAL_INTERPOLATION)
                .scale(boardScale, 2.0F * boardScale, boardScale));

        BoardDisplay value = spawnDisplay(valueLocation, baseOptions()
                .shadowed(false)
                .text(deserializeBarComponent(foregroundTemplate, foregroundColor))
                .opacity(packOpacity(valueInitiallyOpaque ? OPACITY_OPAQUE : OPACITY_TRANSPARENT))
                .interpolationDuration(BAR_INITIAL_INTERPOLATION)
                .teleportDuration(BAR_INITIAL_INTERPOLATION)
                .scale(0.0F, 2.0F * boardScale, boardScale));

        BarPair pair = new BarPair(container, value);
        applyBarFrame(pair, 0.0F, -1.0F);
        return pair;
    }

    private void animateFillBar(BarPair pair) {
        BoardDisplay container = pair.container();
        BoardDisplay value = pair.value();
        container.interpolation(0, BAR_INITIAL_INTERPOLATION);
        container.opacity(packOpacity(OPACITY_OPAQUE));

        long cycle = progressCycleTicks(
                definition.settings().pageDurationTicks(),
                animationCache.titleInScale().length);
        float[] scales = animationCache.barScales();
        float[] translations = animationCache.barTranslations();
        int frameCount = Math.max(Math.max(scales.length, translations.length), 1);
        if (usesClientInterpolatedBar()) {
            animateLinearFillBar(pair, cycle, scales, translations);
            return;
        }
        value.opacity(packOpacity(OPACITY_TRANSPARENT));

        TaskHandle fadeIn = scheduler.runDelayed(origin, task -> {
                if (!ensureSessionActive() || container.isRemoved() || value.isRemoved()) {
                    task.cancel();
                    return;
                }
                value.opacity(packOpacity(OPACITY_OPAQUE));
        }, 1L);
        tasks.add(fadeIn);

        int interpolationTicks = progressInterpolationTicks(cycle, frameCount);
        value.interpolation(0, interpolationTicks);

        TaskHandle progress = scheduler.runAtFixedRate(origin, new java.util.function.Consumer<>() {
            long elapsed = 0L;
            int lastFrame = -1;

            @Override
            public void accept(TaskHandle task) {
                if (!ensureSessionActive() || value.isRemoved()) {
                    task.cancel();
                    return;
                }
                double ratio = Math.min(1.0D, (double) (elapsed + 1L) / (double) cycle);
                int targetFrame = frameCount <= 1 ? 0
                        : Math.min(frameCount - 1, (int) Math.floor(ratio * (frameCount - 1)));
                if (targetFrame != lastFrame) {
                    float progressValue = scales.length == 0 ? (float) ratio
                            : scales[Math.min(targetFrame, scales.length - 1)];
                    float translationValue = translations.length == 0 ? (-1.0F + progressValue)
                            : translations[Math.min(targetFrame, translations.length - 1)];
                    value.interpolation(0, interpolationTicks);
                    applyBarFrame(pair, progressValue, translationValue);
                    lastFrame = targetFrame;
                }
                if (ratio >= 1.0D) {
                    task.cancel();
                    scheduleBarFade(pair, barFadeDelayTicks(
                            definition.settings().intervalTicks(), cycle, BAR_FADE_TICKS));
                    return;
                }
                elapsed++;
            }
        }, 1L, 1L);
        tasks.add(progress);
    }

    private void animateLinearFillBar(BarPair pair,
            long cycle,
            float[] scales,
            float[] translations) {
        BoardDisplay value = pair.value();
        float targetScale = scales.length == 0 ? 1.0F : scales[scales.length - 1];
        float targetTranslation = translations.length == 0 ? 0.0F : translations[translations.length - 1];

        TaskHandle target = scheduler.runDelayed(origin, task -> {
            if (!ensureSessionActive() || value.isRemoved()) {
                task.cancel();
                return;
            }
            value.interpolation(0, linearCycleInterpolationTicks(cycle));
            applyBarFrame(pair, targetScale, targetTranslation);

            TaskHandle resetInterpolation = scheduler.runDelayed(origin, resetTask -> {
                if (!ensureSessionActive() || value.isRemoved()) {
                    resetTask.cancel();
                    return;
                }
                value.interpolation(0, BAR_INITIAL_INTERPOLATION);
            }, linearCycleInterpolationTicks(cycle));
            tasks.add(resetInterpolation);
        }, 1L);
        tasks.add(target);

        long fadeDelay = barFadeDelayTicks(
                definition.settings().intervalTicks(),
                cycle,
                BAR_FADE_TICKS);
        scheduleBarFade(pair, cycle + fadeDelay);
    }

    private void scheduleBarFade(BarPair pair, long fadeDelayTicks) {
        BoardDisplay container = pair.container();
        BoardDisplay value = pair.value();
        TaskHandle fade = scheduler.runDelayed(origin, task -> {
                if (!ensureSessionActive()) {
                    task.cancel();
                    return;
                }
                container.interpolation(0, BAR_FADE_TICKS);
                container.opacity(packOpacity(OPACITY_TRANSPARENT));
                value.interpolation(0, BAR_FADE_TICKS);
                value.opacity(packOpacity(OPACITY_TRANSPARENT));
                TaskHandle cleanup = scheduler.runDelayed(origin, cleanupTask -> {
                        despawnDisplay(container);
                        despawnDisplay(value);
                }, BAR_FADE_TICKS);
                tasks.add(cleanup);
        }, fadeDelayTicks);
        tasks.add(fade);
    }

    static long progressCycleTicks(long pageDurationTicks, int entranceFrames) {
        return Math.max(1L, pageDurationTicks) + Math.max(1, entranceFrames);
    }

    static int progressInterpolationTicks(long cycleTicks, int frameCount) {
        int transitions = Math.max(1, frameCount - 1);
        return Math.max(1, (int) Math.ceil((double) Math.max(1L, cycleTicks) / transitions));
    }

    static int linearInterpolationTicks(int frameCount) {
        return Math.max(1, frameCount - 1);
    }

    static long linearCompletionDelayTicks(int frameCount) {
        return Math.max(1L, frameCount);
    }

    static int linearCycleInterpolationTicks(long cycleTicks) {
        long transitions = Math.max(1L, cycleTicks - 1L);
        return (int) Math.min(Integer.MAX_VALUE, transitions);
    }

    static boolean usesClientInterpolation(boolean enabled, EasingType easing, int frameCount) {
        return enabled && easing == EasingType.LINEAR && frameCount > 1;
    }

    static double scaledRowOffset(double rowYOffset, double rowSpacing, int index, double boardScale) {
        return (rowYOffset - (index * rowSpacing)) * boardScale;
    }

    static long barFadeDelayTicks(long intervalTicks, long cycleTicks, int fadeTicks) {
        return Math.max(1L, intervalTicks - cycleTicks - Math.max(1, fadeTicks));
    }

    private Location computeValueLocation(Location containerLocation) {
        Vector direction = origin.getDirection();
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(BAR_DEPTH_OFFSET * boardScale);
        Location valueLocation = containerLocation.clone().add(direction);
        if ((valueLocation.getBlockX() >> 4) != (containerLocation.getBlockX() >> 4)
                || (valueLocation.getBlockZ() >> 4) != (containerLocation.getBlockZ() >> 4)) {
            valueLocation = containerLocation.clone().subtract(direction);
        }
        return valueLocation;
    }

    private Component deserializeBarComponent(String template, String preferredColor) {
        String fallbackColor = extractColorFromTemplate(template);
        String colorToUse = sanitizeHexColor(preferredColor);
        if (colorToUse == null) {
            colorToUse = sanitizeHexColor(fallbackColor);
        }
        String processed = template;
        if (processed.contains("%color%")) {
            String replacement = colorToUse != null ? colorToUse : (fallbackColor != null ? fallbackColor : "#CFCFCF");
            processed = processed.replace("%color%", replacement);
        }
        if (colorToUse != null && preferredColor != null) {
            processed = overrideColorTag(processed, colorToUse);
        }
        return plugin.deserializeMiniMessage(processed);
    }

    private String sanitizeHexColor(String color) {
        if (color == null) {
            return null;
        }
        String trimmed = color.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (trimmed.length() != 4 && trimmed.length() != 7) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private String extractColorFromTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        Matcher hexMatcher = HEX_COLOR_TAG.matcher(template);
        if (hexMatcher.find()) {
            return "#" + hexMatcher.group(1);
        }
        Matcher colorMatcher = COLOR_TAG.matcher(template);
        if (colorMatcher.find()) {
            return colorMatcher.group(1);
        }
        return null;
    }

    private String overrideColorTag(String template, String color) {
        Matcher hexMatcher = HEX_COLOR_TAG.matcher(template);
        if (hexMatcher.find()) {
            return hexMatcher.replaceFirst(Matcher.quoteReplacement("<" + color + ">"));
        }
        Matcher colorMatcher = COLOR_TAG.matcher(template);
        if (colorMatcher.find()) {
            return colorMatcher.replaceFirst(Matcher.quoteReplacement("<color:" + color + ">"));
        }
        return "<" + color + ">" + template;
    }

    private void applyBarFrame(BarPair pair, float progressValue, float translationValue) {
        float clampedProgress = Math.max(0.0F, Math.min(1.0F, progressValue));
        float translationFactor = Float.isNaN(translationValue) ? (-1.0F + clampedProgress) : translationValue;
        float offset = Math.max(-1.0F, Math.min(1.0F, translationFactor));
        BoardDisplay value = pair.value();
        value.transform(offset * boardScale, 0.0F, 0.0F,
                clampedProgress * boardScale, 2.0F * boardScale, boardScale);
    }

    // ----------------------------------------------------------------- rows

    private void spawnRows(World world, List<RowEntry> entries) {
        long delayBetween = Math.max(1L, definition.settings().rowDelayTicks());
        for (int i = 0; i < entries.size(); i++) {
            RowEntry entry = entries.get(i);
            long spawnDelay = delayBetween * i;
            TaskHandle task = scheduler.runDelayed(origin, scheduledTask -> {
                    if (!leaderboard.isRenderable()) {
                        stop();
                        return;
                    }
                    if (!active) {
                        scheduledTask.cancel();
                        return;
                    }
                    spawnRow(world, entry);
            }, spawnDelay);
            tasks.add(task);
        }
    }

    private void spawnRow(World world, RowEntry entry) {
        Location base = origin.clone().add(0.0D, entry.offsetY(), 0.0D);
        float[] rowOffsets = animationCache.rowInOffsets();
        float startOffset = rowOffsets.length == 0 ? 0.0F : rowOffsets[0];
        BoardDisplay row = spawnDisplay(base, baseOptions()
                .shadowed(definition.textShadow())
                .text(entry.component())
                .opacity(packOpacity(OPACITY_TRANSPARENT))
                .interpolationDuration(ROW_INTERPOLATION)
                .teleportDuration(ROW_INTERPOLATION)
                .translation(startOffset * boardScale, 0.0F, 0.0F)
                .scale(boardScale, boardScale, boardScale));
        animateRow(row);
    }

    private String renderRow(String color, String circledNumber, String playerName, String value) {
        return definition.formatting().renderRow(color, circledNumber, playerName, value, page.icon());
    }

    private void animateRow(BoardDisplay display) {
        float[] offsets = animationCache.rowInOffsets();
        LeaderboardAnimations.Row animation = definition.animations().row();
        if (usesClientInterpolation(animation.inEnabled(), animation.inCurve(), offsets.length)) {
            float targetOffset = offsets[offsets.length - 1];
            scheduleLinearTransition(
                    display,
                    1L,
                    offsets.length,
                    () -> applyRowTransform(display, targetOffset, packOpacity(OPACITY_OPAQUE)),
                    () -> scheduleRowFade(display)
            );
            return;
        }
        scheduleFrameAnimation(
                display,
                1L,
                offsets,
                (offset, frame, totalFrames) -> {
                    display.interpolation(0, ROW_INTERPOLATION);
                    applyRowTransform(display, offset, opacityAtFrame(frame, totalFrames, true));
                },
                () -> scheduleRowFade(display)
        );
    }

    private void scheduleRowFade(BoardDisplay display) {
        long delay = Math.max(1L, definition.settings().pageDurationTicks());
        float[] offsets = animationCache.rowOutOffsets();
        LeaderboardAnimations.Row animation = definition.animations().row();
        if (usesClientInterpolation(animation.outEnabled(), animation.outCurve(), offsets.length)) {
            float targetOffset = offsets[offsets.length - 1];
            scheduleLinearTransition(
                    display,
                    delay,
                    offsets.length,
                    () -> applyRowTransform(display, targetOffset, packOpacity(OPACITY_TRANSPARENT)),
                    () -> despawnDisplay(display)
            );
            return;
        }
        scheduleFrameAnimation(
                display,
                delay,
                offsets,
                (offset, frame, totalFrames) -> {
                    display.interpolation(0, ROW_INTERPOLATION);
                    applyRowTransform(display, offset, opacityAtFrame(frame, totalFrames, false));
                },
                () -> despawnDisplay(display)
        );
    }

    private void applyRowTransform(BoardDisplay display, float offset, byte opacity) {
        display.transformAndOpacity(offset * boardScale, 0.0F, 0.0F,
                boardScale, boardScale, boardScale, opacity);
    }

    private void scheduleFrameAnimation(BoardDisplay display,
            long initialDelayTicks,
            float[] frames,
            FrameApplier applier,
            Runnable onComplete) {
        long startTick = Bukkit.getCurrentTick() + Math.max(1L, initialDelayTicks);
        frameAnimations.add(new FrameAnimation(display, startTick, frames, applier, onComplete));
        ensureFrameAnimationTask();
    }

    private void ensureFrameAnimationTask() {
        if (frameAnimationTask != null && !frameAnimationTask.isCancelled()) {
            return;
        }
        frameAnimationTask = scheduler.runAtFixedRate(origin, this::tickFrameAnimations, 1L, 1L);
        tasks.add(frameAnimationTask);
    }

    private void tickFrameAnimations(TaskHandle task) {
        if (!ensureSessionActive()) {
            frameAnimations.clear();
            task.cancel();
            frameAnimationTask = null;
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        for (int index = frameAnimations.size() - 1; index >= 0; index--) {
            FrameAnimation animation = frameAnimations.get(index);
            if (animation.display.isRemoved()) {
                frameAnimations.remove(index);
                continue;
            }
            if (currentTick < animation.startTick) {
                continue;
            }
            if (animation.frame >= animation.frames.length) {
                frameAnimations.remove(index);
                animation.onComplete.run();
                continue;
            }

            animation.applier.apply(
                    animation.frames[animation.frame],
                    animation.frame,
                    animation.frames.length);
            animation.frame++;
        }

        if (frameAnimations.isEmpty()) {
            task.cancel();
            frameAnimationTask = null;
        }
    }

    private void scheduleLinearTransition(BoardDisplay display,
            long initialDelayTicks,
            int frameCount,
            Runnable applyTarget,
            Runnable onComplete) {
        TaskHandle start = scheduler.runDelayed(origin, task -> {
            if (!ensureSessionActive() || display.isRemoved()) {
                task.cancel();
                return;
            }
            display.interpolation(0, linearInterpolationTicks(frameCount));
            applyTarget.run();

            TaskHandle completion = scheduler.runDelayed(origin, completionTask -> {
                if (!ensureSessionActive() || display.isRemoved()) {
                    completionTask.cancel();
                    return;
                }
                display.interpolation(0, 1);
                onComplete.run();
            }, linearCompletionDelayTicks(frameCount));
            tasks.add(completion);
        }, Math.max(1L, initialDelayTicks));
        tasks.add(start);
    }

    private boolean usesClientInterpolatedBar() {
        LeaderboardAnimations.Bar animation = definition.animations().bar();
        int frameCount = Math.max(
                animationCache.barScales().length,
                animationCache.barTranslations().length);
        return usesClientInterpolation(animation.enabled(), animation.curve(), frameCount);
    }

    private boolean usesClientInterpolatedTitleEntrance() {
        LeaderboardAnimations.Title animation = definition.animations().title();
        return usesClientInterpolation(
                animation.inEnabled(),
                animation.inCurve(),
                animationCache.titleInScale().length);
    }

    private boolean ensureSessionActive() {
        if (!active) {
            return false;
        }
        if (leaderboard.isRenderable()) {
            return true;
        }
        stop();
        return false;
    }

    private void despawnDisplay(BoardDisplay display) {
        if (display == null) {
            return;
        }
        boolean removed = displays.remove(display);
        if (removed) {
            liveDisplays = Math.max(0, liveDisplays - 1);
        }
        leaderboard.untrack(display);
        display.remove();
        if (removed) {
            checkCompletion();
        }
    }

    private void checkCompletion() {
        if (!completed && liveDisplays <= 0) {
            completed = true;
            active = false;
            leaderboard.onSessionFinished(this);
        }
    }

    private static byte packOpacity(int alpha) {
        int clamped = Math.max(0, Math.min(OPACITY_OPAQUE, alpha));
        return (byte) clamped;
    }

    private static byte opacityAtFrame(int frame, int totalFrames, boolean fadeIn) {
        if (totalFrames <= 1) {
            return packOpacity(fadeIn ? OPACITY_OPAQUE : OPACITY_TRANSPARENT);
        }
        double progress = (double) frame / (double) (totalFrames - 1);
        double alpha = fadeIn ? progress : (1.0D - progress);
        return packOpacity((int) Math.round(alpha * OPACITY_OPAQUE));
    }

    private record BarPair(BoardDisplay container, BoardDisplay value) {
    }

    private record RowEntry(double offsetY, Component component) {
    }

    @FunctionalInterface
    private interface FrameApplier {
        void apply(float value, int frame, int totalFrames);
    }

    private static final class FrameAnimation {
        private final BoardDisplay display;
        private final long startTick;
        private final float[] frames;
        private final FrameApplier applier;
        private final Runnable onComplete;
        private int frame;

        private FrameAnimation(BoardDisplay display,
                long startTick,
                float[] frames,
                FrameApplier applier,
                Runnable onComplete) {
            this.display = display;
            this.startTick = startTick;
            this.frames = frames;
            this.applier = applier;
            this.onComplete = onComplete;
        }
    }
}
