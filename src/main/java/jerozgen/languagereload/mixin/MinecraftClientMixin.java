package jerozgen.languagereload.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jerozgen.languagereload.access.ITranslationStorage;
import jerozgen.languagereload.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class, priority = 990)
abstract class MinecraftClientMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    void onConstructed(RunArgs args, CallbackInfo ci) {
        Config.load();
    }

    @ModifyExpressionValue(method = {"method_1581" /* Creative Inventory */, "method_53866" /* Recipe Book */},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;getString()Ljava/lang/String;"))
    private static String addFallbackTranslationsToSearchTooltips(String original, Text text) {
        if (Config.getInstance() == null) return original;
        if (!Config.getInstance().multilingualItemSearch) return original;
        if (!(Language.getInstance() instanceof TranslationStorage translationStorage)) return original;

        var stringBuilder = new StringBuilder(original);
        for (String fallbackCode : Config.getInstance().fallbacks) {
            ((ITranslationStorage) translationStorage).languagereload_setTargetLanguage(fallbackCode);
            stringBuilder.append('\n').append(text.getString());
        }
        ((ITranslationStorage) translationStorage).languagereload_setTargetLanguage(Language.DEFAULT_LANGUAGE);
        stringBuilder.append('\n').append(text.getString());

        ((ITranslationStorage) translationStorage).languagereload_setTargetLanguage(null);
        return stringBuilder.toString();
    }
}
