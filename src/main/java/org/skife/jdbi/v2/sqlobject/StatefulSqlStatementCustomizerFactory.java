package org.skife.jdbi.v2.sqlobject;


/**
 Allows the creator of the factory to provide a source of state, freeing the factories from
 * the obligation to manage memory.
*/
public interface StatefulSqlStatementCustomizerFactory extends SqlStatementCustomizerFactory {
    public void setState(HandlerState state);
}
