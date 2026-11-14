package io.github.foundry4j.quarkforge.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/// ## ResourceBundleMessageSource
/// 基于 `java.util.ResourceBundle` 的默认文案源，从 classpath 读取
/// `<baseName>[_语言[_国家]].properties`，统一按 UTF-8 解码
/// （JDK 默认的 `properties` 读取是 ISO-8859-1，中文会乱码）。
///
/// 查找顺序遵循标准 `ResourceBundle` 回退规则：
/// `zh_CN` → `zh` → 无后缀的默认文件。
public final class ResourceBundleMessageSource implements MessageSource {

    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
            if (!"java.properties".equals(format)) {
                return super.newBundle(baseName, locale, format, loader, reload);
            }
            String resource = toResourceName(toBundleName(baseName, locale), "properties");
            URL url = loader.getResource(resource);
            if (url == null) {
                return null;
            }
            URLConnection connection = url.openConnection();
            if (reload) {
                connection.setUseCaches(false);
            }
            try (InputStream in = connection.getInputStream();
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }

        /// 禁止回退到 JVM 默认语言：否则同一份配置在中文开发机与英文 CI 上
        /// 会解析出不同语言（CI 上 `zh_CN` 会拿到 `_en` 文案）。
        /// 候选语言都缺失时按标准候选链回退到无后缀的基础文案文件。
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    };

    private final String baseName;
    private final ClassLoader classLoader;
    private final Map<Locale, Optional<ResourceBundle>> cache = new ConcurrentHashMap<>();

    public ResourceBundleMessageSource(String baseName) {
        this(baseName, Thread.currentThread().getContextClassLoader());
    }

    public ResourceBundleMessageSource(String baseName, ClassLoader classLoader) {
        this.baseName = baseName;
        this.classLoader = classLoader != null ? classLoader : ResourceBundleMessageSource.class.getClassLoader();
    }

    @Override
    public String resolve(String key, Locale locale, Object... args) {
        if (key == null) {
            return null;
        }
        ResourceBundle bundle = bundle(locale).orElse(null);
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        String pattern = bundle.getString(key);
        if (pattern == null || args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    @Override
    public boolean supports(Locale locale) {
        if (locale == null) {
            return false;
        }
        ResourceBundle bundle = bundle(locale).orElse(null);
        if (bundle == null) {
            return false;
        }
        // 命中回退到 base 包时 bundle 的 Locale 为空，说明没有该语言的文件
        String wanted = locale.getLanguage().toLowerCase(Locale.ROOT);
        String actual = bundle.getLocale().getLanguage().toLowerCase(Locale.ROOT);
        return wanted.isEmpty() || wanted.equals(actual);
    }

    private Optional<ResourceBundle> bundle(Locale locale) {
        Locale target = locale != null ? locale : Locale.getDefault();
        return cache.computeIfAbsent(target, l -> {
            try {
                return Optional.of(ResourceBundle.getBundle(baseName, l, classLoader, UTF8_CONTROL));
            } catch (RuntimeException e) {
                // 文案文件缺失或损坏时降级为“无文案”，不影响主流程
                return Optional.empty();
            }
        });
    }
}
