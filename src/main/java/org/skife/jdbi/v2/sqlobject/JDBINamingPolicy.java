package org.skife.jdbi.v2.sqlobject;

import net.sf.cglib.core.DefaultNamingPolicy;
import net.sf.cglib.core.NamingPolicy;
import net.sf.cglib.core.Predicate;

/**
 * Properly namespaces generated classes to avoid collisions with other libraries which use CGLIB.
 */
public class JDBINamingPolicy implements NamingPolicy {
    public static final JDBINamingPolicy INSTANCE = new JDBINamingPolicy();

    @Override
    public String getClassName(String prefix, String source, Object key, Predicate names) {
        return DefaultNamingPolicy.INSTANCE.getClassName("JDBI:" + prefix, source, key, names);
    }
}
