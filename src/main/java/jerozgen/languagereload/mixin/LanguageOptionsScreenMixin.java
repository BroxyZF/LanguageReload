package jerozgen.languagereload.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jerozgen.languagereload.LanguageReload;
import jerozgen.languagereload.access.ILanguageOptionsScreen;
import jerozgen.languagereload.config.Config;
import jerozgen.languagereload.gui.LanguageEntry;
import jerozgen.languagereload.gui.LanguageListWidget;
import jerozgen.languagereload.gui.LockedLanguageEntry;
import jerozgen.languagereload.gui.MovableLanguageEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Stream;

@Mixin(LanguageOptionsScreen.class)
public abstract class LanguageOptionsScreenMixin extends GameOptionsScreen implements ILanguageOptionsScreen {
    @Shadow @Final private static Text LANGUAGE_WARNING_TEXT;

    @Unique private LanguageListWidget availableLanguageList;
    @Unique private LanguageListWidget selectedLanguageList;
    @Unique private TextFieldWidget searchBox;
    @Unique private final LinkedList<String> selectedLanguages = new LinkedList<>();
    @Unique private final Map<String, MovableLanguageEntry> languageEntries = new LinkedHashMap<>();
    @Unique private LockedLanguageEntry defaultLanguageEntry;

    LanguageOptionsScreenMixin(Screen parent, GameOptions options, Text title) {
        super(parent, options, title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    void onConstructed(Screen parent, GameOptions options, LanguageManager languageManager, CallbackInfo ci) {
        var currentLangCode = languageManager.getLanguage();
        if (!currentLangCode.equals(Language.DEFAULT_LANGUAGE))
            selectedLanguages.add(currentLangCode);
        selectedLanguages.addAll(Config.getInstance().fallbacks);
        languageManager.getAllLanguages().forEach((code, language) -> {
            if (!code.equals(Language.DEFAULT_LANGUAGE))
                languageEntries.put(code, new MovableLanguageEntry(this::refresh, code, language, selectedLanguages));
            else defaultLanguageEntry = new LockedLanguageEntry(this::refresh, code, language, selectedLanguages);
        });
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    void onInit(CallbackInfo ci) {
        searchBox = new TextFieldWidget(textRenderer, width / 2 - 100, 22, 200, 20, searchBox, Text.empty()) {
            @Override
            public void setFocused(boolean focused) {
                if (!isFocused() && focused) {
                    super.setFocused(true);
                    focusSearch();
                } else super.setFocused(focused);
            }
        };
        searchBox.setChangedListener(__ -> refresh());
        addSelectableChild(searchBox);
        setInitialFocus(searchBox);

        var listWidth = Math.min(width / 2 - 4, 200);
        var it = (LanguageOptionsScreen) (Object) this;
        availableLanguageList = new LanguageListWidget(client, it, listWidth, height, Text.translatable("pack.available.title"));
        selectedLanguageList = new LanguageListWidget(client, it, listWidth, height, Text.translatable("pack.selected.title"));
        availableLanguageList.setLeftPos(width / 2 - 4 - listWidth);
        selectedLanguageList.setLeftPos(width / 2 + 4);
        addSelectableChild(availableLanguageList);
        addSelectableChild(selectedLanguageList);
        refresh();

        addDrawableChild(gameOptions.getForceUnicodeFont().createWidget(gameOptions, width / 2 - 155, height - 28, 150));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, this::onDone)
                .dimensions(width / 2 - 155 + 160, height - 28, 150, 20)
                .build());

        super.init();
        ci.cancel();
    }

    @Unique
    private void onDone(ButtonWidget button) {
        if (client == null) return;
        client.setScreen(parent);

        var language = selectedLanguages.peekFirst();
        if (language == null) {
            LanguageReload.setLanguage(Language.DEFAULT_LANGUAGE, new LinkedList<>());
        } else {
            var fallbacks = new LinkedList<>(selectedLanguages);
            fallbacks.remove(0);
            LanguageReload.setLanguage(language, fallbacks);
        }
    }

    @Override
    public void languagereload_focusList(LanguageListWidget list) {
        switchFocus(GuiNavigationPath.of(list, this));
    }

    @Override
    public void languagereload_focusEntry(LanguageEntry entry) {
        switchFocus(GuiNavigationPath.of(entry, entry.getParent(), this));
    }

    @Unique
    private void focusSearch() {
        switchFocus(GuiNavigationPath.of(searchBox, this));
    }

    @Unique
    private void refresh() {
        refreshList(selectedLanguageList, Stream.concat(
                selectedLanguages.stream().map(languageEntries::get).filter(Objects::nonNull),
                Stream.of(defaultLanguageEntry)));
        refreshList(availableLanguageList, languageEntries.values().stream()
                .filter(entry -> {
                    if (selectedLanguageList.children().contains(entry)) return false;
                    var query = searchBox.getText().toLowerCase(Locale.ROOT);
                    var langCode = entry.getCode().toLowerCase(Locale.ROOT);
                    var langName = entry.getLanguage().getDisplayText().getString().toLowerCase(Locale.ROOT);
                    return langCode.contains(query) || langName.contains(query);
                }));
    }

    @Unique
    private void refreshList(LanguageListWidget list, Stream<? extends LanguageEntry> entries) {
        var selectedEntry = list.getSelectedOrNull();
        list.setSelected(null);
        list.children().clear();
        entries.forEach(entry -> {
            list.children().add(entry);
            entry.setParent(list);
            if (entry == selectedEntry) {
                list.setSelected(entry);
            }
        });
        list.setScrollAmount(list.getScrollAmount());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        super.render(context, mouseX, mouseY, delta);

        availableLanguageList.render(context, mouseX, mouseY, delta);
        selectedLanguageList.render(context, mouseX, mouseY, delta);
        searchBox.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, LANGUAGE_WARNING_TEXT, width / 2, height - 46, 0x808080);

        ci.cancel();
    }

    @ModifyExpressionValue(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/KeyCodes;isToggle(I)Z"))
    boolean disableVanillaSelectWithToggleKeys(boolean ignoredOriginal) {
        return false;
    }
}
