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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;

public class StringTemplate3StatementLocator implements StatementLocator
{
    private final StringTemplateGroup group;
    private final StringTemplateGroup literals = new StringTemplateGroup("literals", AngleBracketTemplateLexer.class);

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

        ConcurrentMap<String, StringTemplateGroup> myState = null;
        try {
            myState = state.getState(getClass(), new Callable<ConcurrentMap<String, StringTemplateGroup>>() {
                @Override
                public ConcurrentMap<String, StringTemplateGroup> call() throws Exception {
                    return new ConcurrentHashMap<String, StringTemplateGroup>();
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("just creating a ConcurrentHashMap, so this should never happen.");
        }

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

    public String locate(String name, StatementContext ctx) throws Exception
    {
        if (group.isDefined(name)) {
            // yeah, found template for it!
            StringTemplate t = group.lookupTemplate(name).getInstanceOf();
            for (Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                t.setAttribute(entry.getKey(), entry.getValue());
            }
            return t.toString();
        }
        else if (treatLiteralsAsTemplates) {
            // no template in the template group, but we want literals to be templates
            final String key = new String(new Base64().encode(name.getBytes()));
            if (!literals.isDefined(key)) {
                literals.defineTemplate(key, name);
            }
            StringTemplate t = literals.lookupTemplate(key);
            for (Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                t.setAttribute(entry.getKey(), entry.getValue());
            }
            return t.toString();
        }
        else {
            // no template, no literals as template, just use the literal as sql
            return name;
        }
    }

    private final static String sep = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

    private static String mungify(String path)
    {
        return path.replaceAll("\\.", Matcher.quoteReplacement(sep));
    }

}
