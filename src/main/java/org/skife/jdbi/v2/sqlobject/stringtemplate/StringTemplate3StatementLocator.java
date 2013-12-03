package org.skife.jdbi.v2.sqlobject.stringtemplate;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.antlr.stringtemplate.language.AngleBracketTemplateLexer;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.HandlerState;
import org.skife.jdbi.v2.tweak.StatementLocator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;

public class StringTemplate3StatementLocator implements StatementLocator
{
    private final StringTemplateGroup group;
    private final StringTemplateGroup literals = new StringTemplateGroup("literals", AngleBracketTemplateLexer.class);
    private final HandlerState state;

    private boolean treatLiteralsAsTemplates;

    public StringTemplate3StatementLocator(Class baseClass)
    {
        this(mungify("/" + baseClass.getName()) + ".sql.stg", false, false, null);
    }

    public StringTemplate3StatementLocator(Class baseClass,
                                           boolean allowImplicitTemplateGroup,
                                           boolean treatLiteralsAsTemplates,
                                           HandlerState state)
    {
        this(mungify("/" + baseClass.getName()) + ".sql.stg", allowImplicitTemplateGroup, treatLiteralsAsTemplates, state);
    }

    public StringTemplate3StatementLocator(String templateGroupFilePathOnClasspath)
    {
        this(templateGroupFilePathOnClasspath, false, false, null);
    }

    public StringTemplate3StatementLocator(String templateGroupFilePathOnClasspath,
                                           final boolean allowImplicitTemplateGroup,
                                           boolean treatLiteralsAsTemplates,
                                           HandlerState state)
    {
        this.treatLiteralsAsTemplates = treatLiteralsAsTemplates;

        this.group = findInCacheOrCreateStringTemplateGroup(templateGroupFilePathOnClasspath,
                allowImplicitTemplateGroup, state);

        this.state = state;

    }

    /**
     * Loads a StringTemplateGroup from the classpath, first checking a lock-free cache for a pre-existing group.
     * The lock-free cache could potentially allow >1 threads to create an instance, but this implementation
     * guarantees that only one of them will win in the end.
     *
     * @param templateGroupFilePathOnClasspath
     * @param allowImplicitTemplateGroup
     * @return
     */
    private StringTemplateGroup findInCacheOrCreateStringTemplateGroup(String templateGroupFilePathOnClasspath,
                                                                       boolean allowImplicitTemplateGroup,
                                                                       HandlerState state) {
        if (state == null)
            return loadStringTemplateGroup(templateGroupFilePathOnClasspath, allowImplicitTemplateGroup);

        ConcurrentMap<String, StringTemplateGroup> myState = state.getState(getClass(), new HandlerState.StateCreator<ConcurrentMap<String,StringTemplateGroup>>() {
            @Override
            public ConcurrentMap<String, StringTemplateGroup> create()  {
                return new ConcurrentHashMap<String, StringTemplateGroup>();
            }
        });

        StringTemplateGroup result = myState.get(templateGroupFilePathOnClasspath);
        if (result == null) {
            result = loadStringTemplateGroup(templateGroupFilePathOnClasspath, allowImplicitTemplateGroup);

            StringTemplateGroup possiblyCreatedByAnotherThread = myState
                    .putIfAbsent(templateGroupFilePathOnClasspath, result);

            if (possiblyCreatedByAnotherThread != null) {
                // the other thread won the race
                result = possiblyCreatedByAnotherThread;
            } // else this thread won
        }

        return result;
    }

    private static StringTemplateGroup loadStringTemplateGroup(String templateGroupFilePathOnClasspath, boolean allowImplicitTemplateGroup) {
        InputStream ins = StringTemplate3StatementLocator.class.getResourceAsStream(templateGroupFilePathOnClasspath);
        if (allowImplicitTemplateGroup && ins == null) {
            return new StringTemplateGroup("empty template group", AngleBracketTemplateLexer.class);
        }
        else if (ins == null) {
            throw new IllegalStateException("unable to find group file "
                                            + templateGroupFilePathOnClasspath
                                            + " on classpath");
        }
        else {
            InputStreamReader reader = new InputStreamReader(ins);
            try {
                StringTemplateGroup group = new StringTemplateGroup(reader, AngleBracketTemplateLexer.class);
                reader.close();
                return group;
            }
            catch (IOException e) {
                throw new IllegalStateException("unable to load string template group " + templateGroupFilePathOnClasspath,
                                                e);
            }
        }
    }

    final static Object templateCacheLock = new Object();
    public String locate(String name, StatementContext ctx) throws Exception
    {
        Map<String, StringTemplate> templateCache;
        if (state != null) {
            templateCache = state.getState(StringTemplate.class, new HandlerState.StateCreator<Map<String, StringTemplate>>() {
                @Override
                public Map<String, StringTemplate> create() {
                    return new HashMap<String, StringTemplate>();
                }
            });
        } else {
            templateCache = new HashMap<String, StringTemplate>();
        }

        StringTemplate t;
        t = templateCache.get(name);

        if (t == null) {
            if (group.isDefined(name)) {
                t = group.lookupTemplate(name);
                if (t != null) {
                    synchronized (templateCacheLock) {
                        templateCache.put(name, t);
                    }
                }
            }
        }

        if (t != null && t != NO_TEMPLATE) {
            // yeah, found template for it!
            StringTemplate instance = t.getInstanceOf();
            for (Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                instance.setAttribute(entry.getKey(), entry.getValue());
            }
            return instance.toString();
        }
        else if (treatLiteralsAsTemplates) {
            // no template in the template group, but we want literals to be templates
            final String key = new String(new Base64().encode(name.getBytes()));
            if (!literals.isDefined(key)) {
                literals.defineTemplate(key, name);
            }
            t = literals.lookupTemplate(key);

            synchronized (templateCacheLock) {
                templateCache.put(name, t);
            }

            StringTemplate instance = t.getInstanceOf();
            for (Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                instance.setAttribute(entry.getKey(), entry.getValue());
            }
            return instance.toString();
        }
        else {
            synchronized (templateCacheLock) {
                templateCache.put(name, NO_TEMPLATE);
            }

            // no template, no literals as template, just use the literal as sql
            return name;
        }
    }

    private final StringTemplate NO_TEMPLATE = new StringTemplate();

    private final static String sep = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

    private static String mungify(String path)
    {
        return path.replaceAll("\\.", Matcher.quoteReplacement(sep));
    }

}
